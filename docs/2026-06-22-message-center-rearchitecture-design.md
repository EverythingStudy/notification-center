# 消息中心系统重构设计

> 基于《亿级用户消息中心系统设计文档》的落地实现方案
> 日期: 2026-06-22

## 一、架构策略

### 分层架构

采用**分层重构**策略，保持现有渠道发送层基本不变，在其上新建消息中心核心层。

```
┌─────────────────────────────────┐
│      Message Center Core        │  ← 新建
│  Message / Feed / Cursor        │
│  Subscription / Recall / WS     │
├─────────────────────────────────┤
│     PushTask Dispatcher         │  ← 新增适配层
├─────────────────────────────────┤
│   Channel Send (现有代码)        │  ← 保留
│   SMS/Email/WeChat/DingTalk     │
│   限流/重试/死信                │
└─────────────────────────────────┘
```

### 实施阶段

- **阶段一**：消息中心核心（Message、Feed、Cursor、Subscription）
- **阶段二**：推送任务集成（PushTask → 渠道发送适配）
- **阶段三**：WebSocket 实时同步
- **阶段四**：冷热数据分离

---

## 二、领域模型

### 核心实体

| 实体 | 说明 |
|------|------|
| Message | 消息主体，包含标题、内容地址、发送类型、状态 |
| Feed | 消息流，定义消息可见范围 |
| MessageFeedMapping | 消息与 Feed 的多对多关联 |
| UserMessage | 用户级消息记录（写扩散），含已读状态 |
| UserFeedCursor | 用户在各 Feed 的阅读进度 |
| Subscription | 用户订阅关系，控制 Feed 访问权限 |
| PushTask | 推送任务，用于渠道推送 |

### Feed 类型

```
SYSTEM     - 系统公告
VIP        - VIP 公告
MARKETING  - 营销活动
ORDER      - 订单通知
LOGISTICS  - 物流通知
RISK       - 风控通知
```

### 消息发送类型

```
BROADCAST - 广播消息（读扩散，不生成 user_message）
USER      - 用户消息（写扩散，写入 user_message）
```

### 消息状态

```
NORMAL - 正常
RECALL - 已撤回
EXPIRE - 已过期
```

---

## 三、数据库设计

### message（消息主体表）

无需分片，按时间归档。

```sql
CREATE TABLE message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type    VARCHAR(64)  NOT NULL COMMENT '业务类型',
    title       VARCHAR(256) NOT NULL COMMENT '标题',
    content_url VARCHAR(1024) COMMENT '内容存储地址(OSS)',
    send_type   TINYINT      NOT NULL DEFAULT 0 COMMENT '发送类型: 0=BROADCAST, 1=USER',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=NORMAL, 1=RECALL, 2=EXPIRE',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME     COMMENT '过期时间',
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主体表';
```

### message_feed_mapping（消息-Feed 映射表）

```sql
CREATE TABLE message_feed_mapping (
    message_id BIGINT      NOT NULL COMMENT '消息ID',
    feed_type  VARCHAR(32) NOT NULL COMMENT 'Feed类型',
    PRIMARY KEY (message_id, feed_type),
    INDEX idx_feed_type (feed_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息Feed映射表';
```

### user_message（用户消息表）

按 user_id 分片（1024 片）。仅用于 USER 类型的消息。

```sql
CREATE TABLE user_message_XXXX (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    message_id  BIGINT      NOT NULL COMMENT '消息ID',
    biz_type    VARCHAR(64) NOT NULL COMMENT '业务类型',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '已读状态: 0=UNREAD, 1=READ',
    read_time   DATETIME    COMMENT '阅读时间',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_message (user_id, message_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_create (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息表';
```

### user_feed_cursor（用户 Feed 游标表）

按 user_id 分片（1024 片）。

```sql
CREATE TABLE user_feed_cursor_XXXX (
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    feed_type   VARCHAR(32) NOT NULL COMMENT 'Feed类型',
    cursor      BIGINT      NOT NULL DEFAULT 0 COMMENT '已读位置',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_feed (user_id, feed_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Feed游标表';
```

### push_task（推送任务表）

```sql
CREATE TABLE push_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT      NOT NULL COMMENT '消息ID',
    target_type VARCHAR(32) NOT NULL COMMENT '推送目标',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态: 0=PENDING, 1=SENT, 2=FAILED',
    retry_count INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message_id (message_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送任务表';
```

---

## 四、Redis 设计

### 用户 Cursor（Hash）

```
Key: user:{userId}:cursor
Field: system → 1200
Field: vip → 300
Field: marketing → 88
```

### Feed 最大位置（String）

```
Key: feed:{feedType}:max_cursor
Value: 1250
```

### 未读数计算

```
feedUnread = feedMaxCursor - userFeedCursor
totalUnread = Σ(feedUnread)
```

每次写入新消息时递增 `feed:{feedType}:max_cursor`；用户阅读时更新 `user:{userId}:cursor`。

---

## 五、消息流程

### 广播消息发送

```
上游系统 → Kafka (upstream-raw)
  → MessageService
    → 写入 message 表
    → 写入 message_feed_mapping
    → Redis INCR feed:{feedType}:max_cursor
    → （可选）创建 PushTask → 渠道发送
```

不生成 user_message 记录。用户查询时通过 cursor 过滤出可见消息。

### 用户消息发送

```
上游系统 → Kafka (upstream-raw)
  → MessageService
    → 写入 message 表
    → 写入 user_message 表（唯一索引保证幂等）
    → 创建 PushTask → 渠道发送
```

### 消息查询

```
用户请求消息列表
  → SubscriptionService 获取用户可访问的 Feed
  → CursorService 获取用户在各 Feed 的 cursor
  → 广播消息: SELECT m.* FROM message m
      JOIN message_feed_mapping mfm ON m.id = mfm.message_id
      WHERE mfm.feed_type IN (:feeds) AND m.id > :cursor
      AND m.status = 'NORMAL' AND (m.expire_time IS NULL OR m.expire_time > NOW())
  → 用户消息: SELECT * FROM user_message
      WHERE user_id = :userId AND status = 'UNREAD'
  → 合并、按时间排序返回
```

### Cursor 更新

```
客户端上报: {feedType, cursor}
服务端: cursor = max(oldCursor, newCursor)
同步 Redis + MySQL
```

---

## 六、消息撤回

```
管理端 → 更新 message SET status = 'RECALL' WHERE id = ?
  → Redis 删除 feed:{feedType}:max_cursor（可选）
  → Kafka 发送 message_recall 事件
    → WebSocket 通知在线客户端移除消息
```

---

## 七、现有代码变更清单

| 文件 | 变更 |
|------|------|
| NotificationCategoryEnum | 替换为 FeedTypeEnum（SYSTEM/VIP/MARKETING/ORDER/LOGISTICS/RISK） |
| NotificationController | 重写为 Feed/Cursor 查询接口 |
| NotificationQueryService | 重写为基于新模型 |
| UpstreamMessageService | 重构：区分广播/用户消息，写入新表 |
| MessageIdUtils | 更新 Redis key 模式 |
| 新增: Message / Feed / Cursor / Subscription / PushTask 实体 | 对应新表 |
| 新增: MessageService / FeedService / CursorService | 核心业务逻辑 |
| 新增: v3 Flyway migration | 建新表 |
| 新增: WebSocket 支持 | 实时同步（阶段三） |

---

## 八、分片设计

- `user_message`: 按 `user_id % 1024` 分片，建 1024 张表
- `user_feed_cursor`: 按 `user_id % 1024` 分片，建 1024 张表
- `message`: 无需分片，按时间归档
- 分片路由通过 ShardingSphere 或应用层路由实现

> 初始阶段可先不启用分片，使用单表，后续通过 Flyway 迁移到分片。
