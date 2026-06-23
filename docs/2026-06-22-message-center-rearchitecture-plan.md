# 消息中心系统重构 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 将项目从"通知分发系统"重构为"亿级用户消息中心"，实现 Feed + Cursor 架构

**架构策略:** 分层重构（方案A）— 保持现有渠道发送层，新建消息中心核心层。消息中心核心负责消息存储/查询/Cursor管理；渠道发送作为下游PushTask处理器

**阶段一范围:** 消息中心核心（Message、Feed、Cursor、Subscription） + 消息发送/查询/撤回

**Tech Stack:** Spring Boot 3.4.4, MyBatis, Flyway, Redis, Kafka, Java 21

**注释规范:** 所有新建/修改的代码使用中文注释

---

## 文件结构总览

### 新建文件

```
src/main/java/com/notification/
├── model/
│   ├── enums/
│   │   ├── FeedTypeEnum.java              (新建, 替换 NotificationCategoryEnum)
│   │   ├── MessageStatusEnum.java         (新建: NORMAL/RECALL/EXPIRE)
│   │   ├── MessageSendTypeEnum.java       (新建: BROADCAST/USER)
│   │   └── ReadStatusEnum.java            (新建: UNREAD/READ)
│   └── entity/
│       ├── Message.java                   (新建: 消息主体)
│       ├── MessageFeedMapping.java        (新建: 消息-Feed关联)
│       ├── UserMessage.java               (新建: 用户消息, 写扩散)
│       ├── UserFeedCursor.java            (新建: 用户Feed游标)
│       └── Subscription.java              (新建: 用户订阅关系)
├── mapper/
│   ├── MessageMapper.java                 (新建)
│   ├── MessageFeedMappingMapper.java      (新建)
│   ├── UserMessageMapper.java             (新建)
│   ├── UserFeedCursorMapper.java          (新建)
│   └── SubscriptionMapper.java            (新建)
├── service/
│   ├── MessageService.java                (新建: 消息写入/查询接口)
│   ├── CursorService.java                 (新建: Cursor读写/未读数接口)
│   ├── SubscriptionService.java           (新建: 订阅管理接口)
│   └── impl/
│       ├── MessageServiceImpl.java        (新建)
│       ├── CursorServiceImpl.java         (新建)
│       └── SubscriptionServiceImpl.java   (新建)
└── dto/
    └── response/
        ├── FeedUnreadVO.java              (新建: Feed未读数VO)
        └── MessageVO.java                 (新建: 消息返回VO)

src/main/resources/
├── db/migration/V3__add_message_center_tables.sql   (新建)
└── mybatis/mapper/
    ├── MessageMapper.xml                  (新建)
    ├── MessageFeedMappingMapper.xml       (新建)
    ├── UserMessageMapper.xml              (新建)
    ├── UserFeedCursorMapper.xml           (新建)
    └── SubscriptionMapper.xml             (新建)
```

### 修改文件

```
src/main/java/com/notification/
├── model/
│   ├── enums/NotificationCategoryEnum.java   (替换为 FeedTypeEnum, 删除此文件)
│   └── dto/request/UpstreamMessageDTO.java   (新增 sendType/feedTypes 字段)
├── controller/NotificationController.java    (重写为 Feed/Cursor API)
├── service/
│   ├── UpstreamMessageService.java           (更新接口)
│   ├── NotificationQueryService.java         (重写接口)
│   └── impl/
│       ├── UpstreamMessageServiceImpl.java   (重构: 支持广播/用户消息)
│       └── NotificationQueryServiceImpl.java (重写为基于新模型)
├── util/MessageIdUtils.java                  (更新 Redis key 模式)
├── config/KafkaConfig.java                   (新增 message-recall topic)
└── kafka/UpstreamMessageConsumer.java         (适配新模型)

src/main/resources/
└── application.yml                           (新增 kafka topic 配置)
```

---

### Task 1: Flyway V3 迁移脚本

**Files:**
- Create: `src/main/resources/db/migration/V3__add_message_center_tables.sql`

- [ ] **Step 1: 编写 V3 迁移脚本**

```sql
-- V3__add_message_center_tables.sql
-- 消息中心核心表：message / message_feed_mapping / user_message / user_feed_cursor / push_task

CREATE TABLE message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    biz_type    VARCHAR(64)  NOT NULL COMMENT '业务类型',
    title       VARCHAR(256) NOT NULL COMMENT '标题',
    content_url VARCHAR(1024) COMMENT '内容存储地址(OSS)',
    send_type   TINYINT      NOT NULL DEFAULT 0 COMMENT '发送类型: 0=BROADCAST, 1=USER',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=NORMAL, 1=RECALL, 2=EXPIRE',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expire_time DATETIME     COMMENT '过期时间',
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主体表';

CREATE TABLE message_feed_mapping (
    message_id BIGINT      NOT NULL COMMENT '消息ID',
    feed_type  VARCHAR(32) NOT NULL COMMENT 'Feed类型: SYSTEM/VIP/MARKETING/ORDER/LOGISTICS/RISK',
    PRIMARY KEY (message_id, feed_type),
    INDEX idx_feed_type (feed_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息-Feed映射表';

CREATE TABLE user_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    message_id  BIGINT      NOT NULL COMMENT '消息ID',
    biz_type    VARCHAR(64) NOT NULL COMMENT '业务类型',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '已读状态: 0=UNREAD, 1=READ',
    read_time   DATETIME    COMMENT '阅读时间',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_message (user_id, message_id) COMMENT '消费幂等',
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_create (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息表(写扩散)';

CREATE TABLE user_feed_cursor (
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    feed_type   VARCHAR(32) NOT NULL COMMENT 'Feed类型',
    cursor      BIGINT      NOT NULL DEFAULT 0 COMMENT '已读位置(对应message.id)',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_feed (user_id, feed_type) COMMENT '一个用户每个Feed只有一个游标'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Feed游标表';

CREATE TABLE push_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id  BIGINT      NOT NULL COMMENT '消息ID',
    target_type VARCHAR(32) NOT NULL COMMENT '推送目标: in_app/sms/email/wechat/dingtalk/webhook',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态: 0=PENDING, 1=SENT, 2=FAILED',
    retry_count INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_message_id (message_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送任务表';

-- 初始化默认订阅（所有用户默认可访问 SYSTEM）
-- 订阅由业务系统维护，此处仅为参考
```

- [ ] **Step 2: 验证 Flyway 配置**

确保 `application.yml` 中 Flyway 已启用:
```yaml
spring:
  flyway:
    enabled: true
```

验证 V1/V2 迁移脚本存在，V3 位置正确。

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V3__add_message_center_tables.sql
git commit -m "feat: add V3 migration for message center core tables (message/feed/cursor)"
```

---

### Task 2: 枚举和实体定义

**Files:**
- Create: `src/main/java/com/notification/model/enums/FeedTypeEnum.java`
- Create: `src/main/java/com/notification/model/enums/MessageStatusEnum.java`
- Create: `src/main/java/com/notification/model/enums/MessageSendTypeEnum.java`
- Create: `src/main/java/com/notification/model/enums/ReadStatusEnum.java`
- Create: `src/main/java/com/notification/model/entity/Message.java`
- Create: `src/main/java/com/notification/model/entity/MessageFeedMapping.java`
- Create: `src/main/java/com/notification/model/entity/UserMessage.java`
- Create: `src/main/java/com/notification/model/entity/UserFeedCursor.java`
- Create: `src/main/java/com/notification/model/entity/Subscription.java`
- Delete: `src/main/java/com/notification/model/enums/NotificationCategoryEnum.java`

- [ ] **Step 1: 创建 FeedTypeEnum（替换 NotificationCategoryEnum）**

```java
package com.notification.model.enums;

/**
 * Feed 类型枚举
 * 对应设计文档中定义的 6 种消息流
 */
public enum FeedTypeEnum {
    SYSTEM("system", "系统消息"),
    VIP("vip", "VIP消息"),
    MARKETING("marketing", "营销消息"),
    ORDER("order", "订单消息"),
    LOGISTICS("logistics", "物流消息"),
    RISK("risk", "风控消息");

    private final String code;
    private final String displayName;

    FeedTypeEnum(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static FeedTypeEnum fromCode(String code) {
        for (FeedTypeEnum f : values()) {
            if (f.code.equals(code)) return f;
        }
        return SYSTEM;
    }
}
```

- [ ] **Step 2: 创建 MessageStatusEnum**

```java
package com.notification.model.enums;

/**
 * 消息状态枚举
 */
public enum MessageStatusEnum {
    NORMAL(0, "正常"),
    RECALL(1, "已撤回"),
    EXPIRE(2, "已过期");

    private final int code;
    private final String desc;

    MessageStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
```

- [ ] **Step 3: 创建 MessageSendTypeEnum**

```java
package com.notification.model.enums;

/**
 * 消息发送类型枚举
 * BROADCAST: 广播消息，采用读扩散，不生成 user_message
 * USER: 用户消息，采用写扩散，写入 user_message
 */
public enum MessageSendTypeEnum {
    BROADCAST(0, "广播消息"),
    USER(1, "用户消息");

    private final int code;
    private final String desc;

    MessageSendTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
```

- [ ] **Step 4: 创建 ReadStatusEnum**

```java
package com.notification.model.enums;

/**
 * 用户消息已读状态枚举
 */
public enum ReadStatusEnum {
    UNREAD(0, "未读"),
    READ(1, "已读");

    private final int code;
    private final String desc;

    ReadStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
```

- [ ] **Step 5: 创建 Message 实体**

```java
package com.notification.model.entity;

import com.notification.model.enums.MessageSendTypeEnum;
import com.notification.model.enums.MessageStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息主体实体
 * 对应 message 表，广播消息和用户消息都先写入此表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private Long id;
    private String bizType;
    private String title;
    private String contentUrl;
    private MessageSendTypeEnum sendType;
    private MessageStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
}
```

- [ ] **Step 6: 创建 MessageFeedMapping 实体**

```java
package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息-Feed 映射实体
 * 一条消息可以属于多个 Feed（如 VIP 活动同时属于 VIP 和 MARKETING）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageFeedMapping {
    private Long messageId;
    private String feedType;
}
```

- [ ] **Step 7: 创建 UserMessage 实体**

```java
package com.notification.model.entity;

import com.notification.model.enums.ReadStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户消息实体（写扩散）
 * 仅用于 USER 类型消息，每用户每消息一条记录
 * UNIQUE(user_id, message_id) 保证消费幂等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessage {
    private Long id;
    private Long userId;
    private Long messageId;
    private String bizType;
    private ReadStatusEnum status;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
```

- [ ] **Step 8: 创建 UserFeedCursor 实体**

```java
package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户 Feed 游标实体
 * 记录用户在每个 Feed 中的已读位置
 * Cursor 单调递增，永不回退
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedCursor {
    private Long userId;
    private String feedType;
    private Long cursor;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 9: 创建 Subscription 实体**

```java
package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户订阅关系实体
 * 控制用户可以访问哪些 Feed
 * 如普通用户只能访问 SYSTEM，VIP 用户可访问 SYSTEM + VIP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    private Long userId;
    private String feedType;
}
```

- [ ] **Step 10: 删除 NotificationCategoryEnum.java**

删除旧枚举文件。后续在修改其他文件时，将所有 `NotificationCategoryEnum` 引用替换为 `FeedTypeEnum`。

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/notification/model/enums/ \
       src/main/java/com/notification/model/entity/
git rm src/main/java/com/notification/model/enums/NotificationCategoryEnum.java
git commit -m "feat: add feed/cursor domain models, replace NotificationCategoryEnum with FeedTypeEnum"
```

---

### Task 3: MyBatis Mapper（接口 + XML）

**Files:**
- Create: `src/main/java/com/notification/mapper/MessageMapper.java`
- Create: `src/main/java/com/notification/mapper/MessageFeedMappingMapper.java`
- Create: `src/main/java/com/notification/mapper/UserMessageMapper.java`
- Create: `src/main/java/com/notification/mapper/UserFeedCursorMapper.java`
- Create: `src/main/java/com/notification/mapper/SubscriptionMapper.java`
- Create: `src/main/resources/mybatis/mapper/MessageMapper.xml`
- Create: `src/main/resources/mybatis/mapper/MessageFeedMappingMapper.xml`
- Create: `src/main/resources/mybatis/mapper/UserMessageMapper.xml`
- Create: `src/main/resources/mybatis/mapper/UserFeedCursorMapper.xml`
- Create: `src/main/resources/mybatis/mapper/SubscriptionMapper.xml`

- [ ] **Step 1: 创建 MessageMapper 接口**

```java
package com.notification.mapper;

import com.notification.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体 Mapper
 */
@Mapper
public interface MessageMapper {

    int insert(Message message);

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    Optional<Message> findById(@Param("id") Long id);

    /**
     * 根据 Feed 类型和 Cursor 查询广播消息（读扩散）
     */
    List<Message> findBroadcastByFeedAndCursor(
            @Param("feedType") String feedType,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    /**
     * 查询多个 Feed 中 cursor 之后的消息
     */
    List<Message> findBroadcastByFeedsAndCursor(
            @Param("feedTypes") List<String> feedTypes,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    /**
     * 获取 Feed 最大消息 ID（用于 max_cursor）
     */
    Long findMaxIdByFeedType(@Param("feedType") String feedType);
}
```

- [ ] **Step 2: 创建 MessageFeedMappingMapper 接口**

```java
package com.notification.mapper;

import com.notification.model.entity.MessageFeedMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息-Feed 映射 Mapper
 */
@Mapper
public interface MessageFeedMappingMapper {

    int insert(MessageFeedMapping mapping);

    int batchInsert(@Param("list") List<MessageFeedMapping> list);

    List<MessageFeedMapping> findByMessageId(@Param("messageId") Long messageId);

    List<String> findFeedTypesByMessageId(@Param("messageId") Long messageId);
}
```

- [ ] **Step 3: 创建 UserMessageMapper 接口**

```java
package com.notification.mapper;

import com.notification.model.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户消息 Mapper（写扩散表）
 */
@Mapper
public interface UserMessageMapper {

    int insert(UserMessage userMessage);

    int updateStatus(@Param("userId") Long userId,
                     @Param("messageId") Long messageId,
                     @Param("status") int status);

    /**
     * 查询用户的未读消息列表（分页）
     */
    List<UserMessage> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") int status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询用户的消息列表（分页，不分已读未读）
     */
    List<UserMessage> findByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") int status);
}
```

- [ ] **Step 4: 创建 UserFeedCursorMapper 接口**

```java
package com.notification.mapper;

import com.notification.model.entity.UserFeedCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Feed 游标 Mapper
 */
@Mapper
public interface UserFeedCursorMapper {

    int insert(UserFeedCursor cursor);

    int updateCursor(@Param("userId") Long userId,
                     @Param("feedType") String feedType,
                     @Param("cursor") Long cursor);

    Optional<UserFeedCursor> findByUserIdAndFeedType(
            @Param("userId") Long userId,
            @Param("feedType") String feedType);

    List<UserFeedCursor> findByUserId(@Param("userId") Long userId);

    /**
     * 获取用户所有 Feed 的游标
     */
    List<UserFeedCursor> findByUserIdAndFeedTypes(
            @Param("userId") Long userId,
            @Param("feedTypes") List<String> feedTypes);
}
```

- [ ] **Step 5: 创建 SubscriptionMapper 接口**

```java
package com.notification.mapper;

import com.notification.model.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户订阅关系 Mapper
 */
@Mapper
public interface SubscriptionMapper {

    int insert(Subscription subscription);

    int delete(@Param("userId") Long userId, @Param("feedType") String feedType);

    List<Subscription> findByUserId(@Param("userId") Long userId);

    List<String> findFeedTypesByUserId(@Param("userId") Long userId);
}
```

- [ ] **Step 6: 创建 MessageMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.notification.mapper.MessageMapper">

    <resultMap id="BaseResultMap" type="com.notification.model.entity.Message">
        <id column="id" property="id" jdbcType="BIGINT"/>
        <result column="biz_type" property="bizType" jdbcType="VARCHAR"/>
        <result column="title" property="title" jdbcType="VARCHAR"/>
        <result column="content_url" property="contentUrl" jdbcType="VARCHAR"/>
        <result column="send_type" property="sendType" jdbcType="TINYINT"/>
        <result column="status" property="status" jdbcType="TINYINT"/>
        <result column="create_time" property="createTime" jdbcType="TIMESTAMP"/>
        <result column="expire_time" property="expireTime" jdbcType="TIMESTAMP"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, biz_type, title, content_url, send_type, status, create_time, expire_time
    </sql>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
        INSERT INTO message (biz_type, title, content_url, send_type, status, create_time, expire_time)
        VALUES (#{bizType}, #{title}, #{contentUrl}, #{sendType.code}, #{status.code},
                #{createTime}, #{expireTime})
    </insert>

    <update id="updateStatus">
        UPDATE message SET status = #{status} WHERE id = #{id}
    </update>

    <select id="findById" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/> FROM message WHERE id = #{id}
    </select>

    <select id="findBroadcastByFeedAndCursor" resultMap="BaseResultMap">
        SELECT m.id, m.biz_type, m.title, m.content_url, m.send_type, m.status, m.create_time, m.expire_time
        FROM message m
        JOIN message_feed_mapping mfm ON m.id = mfm.message_id
        WHERE mfm.feed_type = #{feedType}
          AND m.id > #{cursor}
          AND m.status = 0
          AND (m.expire_time IS NULL OR m.expire_time > NOW())
        ORDER BY m.id ASC
        LIMIT #{limit}
    </select>

    <select id="findBroadcastByFeedsAndCursor" resultMap="BaseResultMap">
        SELECT m.id, m.biz_type, m.title, m.content_url, m.send_type, m.status, m.create_time, m.expire_time
        FROM message m
        JOIN message_feed_mapping mfm ON m.id = mfm.message_id
        WHERE mfm.feed_type IN
        <foreach collection="feedTypes" item="ft" open="(" separator="," close=")">
            #{ft}
        </foreach>
          AND m.id > #{cursor}
          AND m.status = 0
          AND (m.expire_time IS NULL OR m.expire_time > NOW())
        ORDER BY m.id ASC
        LIMIT #{limit}
    </select>

    <select id="findMaxIdByFeedType" resultType="java.lang.Long">
        SELECT COALESCE(MAX(m.id), 0)
        FROM message m
        JOIN message_feed_mapping mfm ON m.id = mfm.message_id
        WHERE mfm.feed_type = #{feedType}
    </select>

</mapper>
```

- [ ] **Step 7: 创建 MessageFeedMappingMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.notification.mapper.MessageFeedMappingMapper">

    <insert id="insert">
        INSERT INTO message_feed_mapping (message_id, feed_type)
        VALUES (#{messageId}, #{feedType})
    </insert>

    <insert id="batchInsert">
        INSERT INTO message_feed_mapping (message_id, feed_type) VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.messageId}, #{item.feedType})
        </foreach>
    </insert>

    <select id="findByMessageId" resultType="com.notification.model.entity.MessageFeedMapping">
        SELECT message_id AS messageId, feed_type AS feedType
        FROM message_feed_mapping WHERE message_id = #{messageId}
    </select>

    <select id="findFeedTypesByMessageId" resultType="string">
        SELECT feed_type FROM message_feed_mapping WHERE message_id = #{messageId}
    </select>

</mapper>
```

- [ ] **Step 8: 创建 UserMessageMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.notification.mapper.UserMessageMapper">

    <resultMap id="BaseResultMap" type="com.notification.model.entity.UserMessage">
        <id column="id" property="id" jdbcType="BIGINT"/>
        <result column="user_id" property="userId" jdbcType="BIGINT"/>
        <result column="message_id" property="messageId" jdbcType="BIGINT"/>
        <result column="biz_type" property="bizType" jdbcType="VARCHAR"/>
        <result column="status" property="status" jdbcType="TINYINT"/>
        <result column="read_time" property="readTime" jdbcType="TIMESTAMP"/>
        <result column="create_time" property="createTime" jdbcType="TIMESTAMP"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, user_id, message_id, biz_type, status, read_time, create_time
    </sql>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
        INSERT INTO user_message (user_id, message_id, biz_type, status, read_time, create_time)
        VALUES (#{userId}, #{messageId}, #{bizType}, #{status.code}, #{readTime}, #{createTime})
    </insert>

    <update id="updateStatus">
        UPDATE user_message
        SET status = #{status}, read_time = NOW()
        WHERE user_id = #{userId} AND message_id = #{messageId}
    </update>

    <select id="findByUserIdAndStatus" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user_message
        WHERE user_id = #{userId} AND status = #{status}
        ORDER BY create_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="findByUserId" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user_message
        WHERE user_id = #{userId}
        ORDER BY create_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countByUserIdAndStatus" resultType="long">
        SELECT COUNT(1) FROM user_message
        WHERE user_id = #{userId} AND status = #{status}
    </select>

</mapper>
```

- [ ] **Step 9: 创建 UserFeedCursorMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.notification.mapper.UserFeedCursorMapper">

    <resultMap id="BaseResultMap" type="com.notification.model.entity.UserFeedCursor">
        <result column="user_id" property="userId" jdbcType="BIGINT"/>
        <result column="feed_type" property="feedType" jdbcType="VARCHAR"/>
        <result column="cursor" property="cursor" jdbcType="BIGINT"/>
        <result column="update_time" property="updateTime" jdbcType="TIMESTAMP"/>
    </resultMap>

    <sql id="Base_Column_List">
        user_id, feed_type, cursor, update_time
    </sql>

    <insert id="insert">
        INSERT INTO user_feed_cursor (user_id, feed_type, cursor, update_time)
        VALUES (#{userId}, #{feedType}, #{cursor}, NOW())
    </insert>

    <update id="updateCursor">
        UPDATE user_feed_cursor
        SET cursor = GREATEST(cursor, #{cursor}), update_time = NOW()
        WHERE user_id = #{userId} AND feed_type = #{feedType}
          AND cursor &lt; #{cursor}
    </update>

    <select id="findByUserIdAndFeedType" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user_feed_cursor
        WHERE user_id = #{userId} AND feed_type = #{feedType}
    </select>

    <select id="findByUserId" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user_feed_cursor
        WHERE user_id = #{userId}
    </select>

    <select id="findByUserIdAndFeedTypes" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user_feed_cursor
        WHERE user_id = #{userId}
          AND feed_type IN
        <foreach collection="feedTypes" item="ft" open="(" separator="," close=")">
            #{ft}
        </foreach>
    </select>

</mapper>
```

- [ ] **Step 10: 创建 SubscriptionMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.notification.mapper.SubscriptionMapper">

    <insert id="insert">
        INSERT INTO subscription (user_id, feed_type)
        VALUES (#{userId}, #{feedType})
    </insert>

    <delete id="delete">
        DELETE FROM subscription WHERE user_id = #{userId} AND feed_type = #{feedType}
    </delete>

    <select id="findByUserId" resultType="com.notification.model.entity.Subscription">
        SELECT user_id AS userId, feed_type AS feedType
        FROM subscription WHERE user_id = #{userId}
    </select>

    <select id="findFeedTypesByUserId" resultType="string">
        SELECT feed_type FROM subscription WHERE user_id = #{userId}
    </select>

</mapper>
```

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/notification/mapper/ \
       src/main/resources/mybatis/mapper/
git commit -m "feat: add MyBatis mappers and XML for message center tables"
```

---

### Task 4: Redis key 工具更新

**Files:**
- Modify: `src/main/java/com/notification/util/MessageIdUtils.java`

- [ ] **Step 1: 重写 MessageIdUtils**

```java
package com.notification.util;

/**
 * Redis Key 工具类
 * 消息中心模式的 key 定义
 */
public class MessageIdUtils {

    public static String generateMessageId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 消息去重 key（保留，渠道发送层仍使用）
     */
    public static String buildDedupKey(String messageId, Long userId) {
        return "notify:dedup:" + messageId + ":" + userId;
    }

    /**
     * 用户 Cursor Hash Key
     * 存储用户在所有 Feed 中的已读位置
     * Hash field = feedType, value = cursor
     */
    public static String buildUserCursorKey(Long userId) {
        return "user:" + userId + ":cursor";
    }

    /**
     * Feed 最大位置 Key
     * 存储某个 Feed 的最大消息 ID
     */
    public static String buildFeedMaxCursorKey(String feedType) {
        return "feed:" + feedType + ":max_cursor";
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -20
```

Expected: BUILD SUCCESS (MessageIdUtils changes are backward compatible for existing callers)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/notification/util/MessageIdUtils.java
git commit -m "refactor: update MessageIdUtils with cursor/feed Redis key patterns"
```

---

### Task 5: 核心 Service 层

**Files:**
- Create: `src/main/java/com/notification/service/MessageService.java`
- Create: `src/main/java/com/notification/service/impl/MessageServiceImpl.java`
- Create: `src/main/java/com/notification/service/CursorService.java`
- Create: `src/main/java/com/notification/service/impl/CursorServiceImpl.java`
- Create: `src/main/java/com/notification/service/SubscriptionService.java`
- Create: `src/main/java/com/notification/service/impl/SubscriptionServiceImpl.java`

- [ ] **Step 1: 创建 MessageService 接口**

```java
package com.notification.service;

import com.notification.model.entity.Message;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体服务接口
 */
public interface MessageService {

    /**
     * 保存消息并返回完整消息（含自增 ID）
     */
    Message saveMessage(Message message);

    /**
     * 撤回消息（逻辑删除）
     */
    void recallMessage(Long messageId);

    /**
     * 根据 ID 查询消息
     */
    Optional<Message> findById(Long messageId);

    /**
     * 查询用户在指定 Feed 中的未读广播消息（读扩散）
     * 根据 cursor 过滤出用户未读的消息
     */
    List<Message> findBroadcastMessages(String feedType, Long cursor, int limit);

    /**
     * 查询用户在多个 Feed 中的未读广播消息
     * 取所有 Feed 中最小 cursor 之后的消息
     */
    List<Message> findBroadcastMessagesByFeeds(List<String> feedTypes, Long cursor, int limit);

    /**
     * 获取 Feed 当前最大消息 ID
     */
    Long getFeedMaxCursor(String feedType);
}
```

- [ ] **Step 2: 创建 MessageServiceImpl**

```java
package com.notification.service.impl;

import com.notification.mapper.MessageMapper;
import com.notification.model.entity.Message;
import com.notification.model.enums.MessageStatusEnum;
import com.notification.service.MessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public Message saveMessage(Message message) {
        messageMapper.insert(message);
        log.debug("消息已保存: id={}, title={}, sendType={}", message.getId(), message.getTitle(), message.getSendType());
        return message;
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId) {
        messageMapper.updateStatus(messageId, MessageStatusEnum.RECALL.getCode());
        log.info("消息已撤回: messageId={}", messageId);
    }

    @Override
    public Optional<Message> findById(Long messageId) {
        return messageMapper.findById(messageId);
    }

    @Override
    public List<Message> findBroadcastMessages(String feedType, Long cursor, int limit) {
        return messageMapper.findBroadcastByFeedAndCursor(feedType, cursor, limit);
    }

    @Override
    public List<Message> findBroadcastMessagesByFeeds(List<String> feedTypes, Long cursor, int limit) {
        return messageMapper.findBroadcastByFeedsAndCursor(feedTypes, cursor, limit);
    }

    @Override
    public Long getFeedMaxCursor(String feedType) {
        // 优先从 Redis 获取
        String redisKey = MessageIdUtils.buildFeedMaxCursorKey(feedType);
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof Number num) {
            return num.longValue();
        }
        // Redis 没有则查 DB 并回写 Redis
        Long maxId = messageMapper.findMaxIdByFeedType(feedType);
        if (maxId != null) {
            redisTemplate.opsForValue().set(redisKey, maxId);
        }
        return maxId != null ? maxId : 0L;
    }
}
```

- [ ] **Step 3: 创建 CursorService 接口**

```java
package com.notification.service;

import com.notification.model.entity.UserFeedCursor;

import java.util.List;
import java.util.Map;

/**
 * Cursor 服务接口
 * 管理用户在 Feed 中的阅读进度
 */
public interface CursorService {

    /**
     * 获取用户在某个 Feed 中的已读位置
     * 优先读 Redis，再读 MySQL
     */
    Long getCursor(Long userId, String feedType);

    /**
     * 获取用户在多个 Feed 中的已读位置
     * 返回 feedType -> cursor 的映射
     */
    Map<String, Long> getCursors(Long userId, List<String> feedTypes);

    /**
     * 更新用户在某个 Feed 中的已读位置
     * cursor = max(oldCursor, newCursor)，永不回退
     * 同时更新 Redis 和 MySQL
     */
    void updateCursor(Long userId, String feedType, Long newCursor);

    /**
     * 计算用户在某 Feed 的未读数
     * unread = feedMaxCursor - userCursor
     */
    long getUnreadCount(Long userId, String feedType);

    /**
     * 计算用户在所有 Feed 的总未读数
     */
    long getTotalUnreadCount(Long userId, List<String> feedTypes);

    /**
     * 获取用户在各 Feed 的未读数详情
     */
    Map<String, Long> getUnreadCounts(Long userId, List<String> feedTypes);
}
```

- [ ] **Step 4: 创建 CursorServiceImpl**

```java
package com.notification.service.impl;

import com.notification.mapper.UserFeedCursorMapper;
import com.notification.model.entity.UserFeedCursor;
import com.notification.service.CursorService;
import com.notification.service.MessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cursor 服务实现
 * Redis 为主，MySQL 为备份
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CursorServiceImpl implements CursorService {

    private final UserFeedCursorMapper cursorMapper;
    private final MessageService messageService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Long getCursor(Long userId, String feedType) {
        // 1. 优先读 Redis Hash
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);
        Object cached = redisTemplate.opsForHash().get(redisKey, feedType);
        if (cached instanceof Number num) {
            return num.longValue();
        }
        // 2. Redis 没有则查 MySQL
        Optional<UserFeedCursor> cursorOpt = cursorMapper.findByUserIdAndFeedType(userId, feedType);
        Long cursor = cursorOpt.map(UserFeedCursor::getCursor).orElse(0L);
        // 3. 回写 Redis
        redisTemplate.opsForHash().put(redisKey, feedType, cursor);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        return cursor;
    }

    @Override
    public Map<String, Long> getCursors(Long userId, List<String> feedTypes) {
        Map<String, Long> result = new HashMap<>();
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);

        // 批量从 Redis Hash 获取
        List<Object> multiGet = redisTemplate.opsForHash().multiGet(redisKey, feedTypes.stream().map(ft -> (Object) ft).toList());
        for (int i = 0; i < feedTypes.size(); i++) {
            Object val = multiGet.get(i);
            if (val instanceof Number num) {
                result.put(feedTypes.get(i), num.longValue());
            }
        }

        // 缺失的从 MySQL 补
        List<String> missed = feedTypes.stream().filter(ft -> !result.containsKey(ft)).toList();
        if (!missed.isEmpty()) {
            List<UserFeedCursor> dbCursors = cursorMapper.findByUserIdAndFeedTypes(userId, missed);
            for (UserFeedCursor c : dbCursors) {
                result.put(c.getFeedType(), c.getCursor());
                redisTemplate.opsForHash().put(redisKey, c.getFeedType(), c.getCursor());
            }
        }

        // 仍未获取到的 feed 默认 cursor=0
        for (String ft : feedTypes) {
            result.putIfAbsent(ft, 0L);
        }

        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        return result;
    }

    @Override
    @Transactional
    public void updateCursor(Long userId, String feedType, Long newCursor) {
        // 1. 更新 MySQL（GREATEST 保证单调递增）
        int updated = cursorMapper.updateCursor(userId, feedType, newCursor);
        if (updated == 0) {
            // 首次插入
            Optional<UserFeedCursor> existing = cursorMapper.findByUserIdAndFeedType(userId, feedType);
            if (existing.isEmpty()) {
                UserFeedCursor cursor = UserFeedCursor.builder()
                        .userId(userId)
                        .feedType(feedType)
                        .cursor(newCursor)
                        .build();
                cursorMapper.insert(cursor);
            }
        }

        // 2. 更新 Redis（先获取旧值，取 max）
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);
        Object oldVal = redisTemplate.opsForHash().get(redisKey, feedType);
        long oldCursor = oldVal instanceof Number num ? num.longValue() : 0L;
        long finalCursor = Math.max(oldCursor, newCursor);
        redisTemplate.opsForHash().put(redisKey, feedType, finalCursor);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        log.debug("Cursor 已更新: userId={}, feed={}, cursor={}", userId, feedType, finalCursor);
    }

    @Override
    public long getUnreadCount(Long userId, String feedType) {
        long feedMax = messageService.getFeedMaxCursor(feedType);
        long userCursor = getCursor(userId, feedType);
        return Math.max(0, feedMax - userCursor);
    }

    @Override
    public long getTotalUnreadCount(Long userId, List<String> feedTypes) {
        return feedTypes.stream()
                .mapToLong(ft -> getUnreadCount(userId, ft))
                .sum();
    }

    @Override
    public Map<String, Long> getUnreadCounts(Long userId, List<String> feedTypes) {
        Map<String, Long> result = new HashMap<>();
        Map<String, Long> userCursors = getCursors(userId, feedTypes);

        for (String ft : feedTypes) {
            long feedMax = messageService.getFeedMaxCursor(ft);
            long userCursor = userCursors.getOrDefault(ft, 0L);
            result.put(ft, Math.max(0, feedMax - userCursor));
        }

        return result;
    }
}
```

- [ ] **Step 5: 创建 SubscriptionService 接口和实现**

```java
package com.notification.service;

import java.util.List;

/**
 * 用户订阅服务接口
 * 管理用户可以访问的 Feed
 */
public interface SubscriptionService {

    /**
     * 获取用户可访问的 Feed 列表
     */
    List<String> getUserFeedTypes(Long userId);

    /**
     * 为用户订阅一个 Feed
     */
    void subscribe(Long userId, String feedType);

    /**
     * 取消订阅
     */
    void unsubscribe(Long userId, String feedType);
}
```

```java
package com.notification.service.impl;

import com.notification.mapper.SubscriptionMapper;
import com.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户订阅服务实现
 * 控制用户可访问的 Feed 范围
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    public List<String> getUserFeedTypes(Long userId) {
        return subscriptionMapper.findFeedTypesByUserId(userId);
    }

    @Override
    public void subscribe(Long userId, String feedType) {
        com.notification.model.entity.Subscription sub = com.notification.model.entity.Subscription.builder()
                .userId(userId)
                .feedType(feedType)
                .build();
        subscriptionMapper.insert(sub);
        log.info("用户订阅 Feed: userId={}, feedType={}", userId, feedType);
    }

    @Override
    public void unsubscribe(Long userId, String feedType) {
        subscriptionMapper.delete(userId, feedType);
        log.info("用户取消订阅: userId={}, feedType={}", userId, feedType);
    }
}
```

- [ ] **Step 6: 编译验证**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -30
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/notification/service/
git commit -m "feat: add MessageService, CursorService, SubscriptionService"
```

---

### Task 6: REST API 重构

**Files:**
- Modify: `src/main/java/com/notification/service/NotificationQueryService.java`
- Modify: `src/main/java/com/notification/service/impl/NotificationQueryServiceImpl.java`
- Modify: `src/main/java/com/notification/controller/NotificationController.java`
- Create: `src/main/java/com/notification/model/dto/response/FeedUnreadVO.java`
- Create: `src/main/java/com/notification/model/dto/response/MessageVO.java`

- [ ] **Step 1: 创建 FeedUnreadVO 和 MessageVO**

```java
package com.notification.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feed 未读数 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedUnreadVO {
    private String feedType;      // Feed 类型
    private String feedName;      // Feed 名称
    private long unreadCount;     // 未读数
}
```

```java
package com.notification.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息返回 VO（统一消息格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {
    private Long messageId;          // 消息 ID
    private String title;            // 标题
    private String contentUrl;       // 内容地址
    private String bizType;          // 业务类型
    private String feedType;         // 所属 Feed
    private String sendType;         // BROADCAST / USER
    private boolean isRead;          // 是否已读
    private LocalDateTime createTime;// 创建时间
}
```

- [ ] **Step 2: 重写 NotificationQueryService 接口**

```java
package com.notification.service;

import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;

import java.util.List;

/**
 * 消息查询服务接口（消息中心模式）
 * 基于 Feed + Cursor 架构
 */
public interface NotificationQueryService {

    /**
     * 获取用户各 Feed 未读数
     */
    List<FeedUnreadVO> getFeedUnread(Long userId);

    /**
     * 分页查询用户的消息列表
     * 合并广播消息（读扩散）+ 用户消息（写扩散）
     */
    PageResponse<MessageVO> pageMessages(Long userId, int page, int size);

    /**
     * 更新用户在某个 Feed 中的游标（标记为已读）
     */
    void updateCursor(Long userId, String feedType, Long cursor);
}
```

- [ ] **Step 3: 重写 NotificationQueryServiceImpl**

```java
package com.notification.service.impl;

import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.model.entity.Message;
import com.notification.model.entity.UserMessage;
import com.notification.model.enums.FeedTypeEnum;
import com.notification.model.enums.MessageSendTypeEnum;
import com.notification.model.enums.ReadStatusEnum;
import com.notification.service.CursorService;
import com.notification.service.MessageService;
import com.notification.service.NotificationQueryService;
import com.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息查询服务实现（消息中心模式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final SubscriptionService subscriptionService;
    private final CursorService cursorService;
    private final MessageService messageService;
    private final com.notification.mapper.UserMessageMapper userMessageMapper;

    @Override
    public List<FeedUnreadVO> getFeedUnread(Long userId) {
        // 1. 获取用户可访问的 Feed
        List<String> feedTypes = subscriptionService.getUserFeedTypes(userId);
        if (feedTypes.isEmpty()) {
            // 默认所有用户可访问 SYSTEM
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        // 2. 计算各 Feed 未读数
        Map<String, Long> unreadCounts = cursorService.getUnreadCounts(userId, feedTypes);

        // 3. 组装 VO
        List<FeedUnreadVO> result = new ArrayList<>();
        for (String ft : feedTypes) {
            FeedTypeEnum feedEnum = FeedTypeEnum.fromCode(ft);
            result.add(FeedUnreadVO.builder()
                    .feedType(ft)
                    .feedName(feedEnum.getDisplayName())
                    .unreadCount(unreadCounts.getOrDefault(ft, 0L))
                    .build());
        }
        return result;
    }

    @Override
    public PageResponse<MessageVO> pageMessages(Long userId, int page, int size) {
        // 1. 获取用户可访问的 Feed
        List<String> feedTypes = subscriptionService.getUserFeedTypes(userId);
        if (feedTypes.isEmpty()) {
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        // 2. 获取用户当前 cursor（取所有 Feed 中最小 cursor 作为起始点）
        Map<String, Long> cursors = cursorService.getCursors(userId, feedTypes);
        long minCursor = cursors.values().stream().min(Long::compareTo).orElse(0L);

        // 3. 查询广播消息（读扩散）
        List<Message> broadcastMessages = messageService.findBroadcastMessagesByFeeds(
                feedTypes, minCursor, size);

        // 4. 查询用户消息（写扩散）
        int offset = page * size;
        List<UserMessage> userMessages = userMessageMapper.findByUserId(userId, offset, size);

        // 5. 合并结果
        List<MessageVO> items = new ArrayList<>();

        for (Message msg : broadcastMessages) {
            items.add(MessageVO.builder()
                    .messageId(msg.getId())
                    .title(msg.getTitle())
                    .contentUrl(msg.getContentUrl())
                    .bizType(msg.getBizType())
                    .feedType("") // 广播消息可能属于多个 Feed
                    .sendType("BROADCAST")
                    .isRead(true) // 广播消息不追踪已读
                    .createTime(msg.getCreateTime())
                    .build());
        }

        for (UserMessage um : userMessages) {
            // 查询关联的 Message 获取标题
            messageService.findById(um.getMessageId()).ifPresent(msg -> {
                items.add(MessageVO.builder()
                        .messageId(msg.getId())
                        .title(msg.getTitle())
                        .contentUrl(msg.getContentUrl())
                        .bizType(msg.getBizType())
                        .feedType("")
                        .sendType("USER")
                        .isRead(um.getStatus() == ReadStatusEnum.READ)
                        .createTime(um.getCreateTime())
                        .build());
            });
        }

        // 按时间降序排列
        items.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        return new PageResponse<>(items, (long) items.size(), page + 1, size);
    }

    @Override
    public void updateCursor(Long userId, String feedType, Long cursor) {
        cursorService.updateCursor(userId, feedType, cursor);
        log.info("用户更新 Cursor: userId={}, feed={}, cursor={}", userId, feedType, cursor);
    }
}
```

- [ ] **Step 4: 重写 NotificationController**

```java
package com.notification.controller;

import com.notification.model.dto.response.ApiResponse;
import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息中心 REST API
 * 基于 Feed + Cursor 架构的用户消息查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    /**
     * 获取用户各 Feed 的未读数
     */
    @GetMapping("/unread")
    public ApiResponse<List<FeedUnreadVO>> getFeedUnread(
            @RequestParam Long userId) {
        List<FeedUnreadVO> result = notificationQueryService.getFeedUnread(userId);
        return ApiResponse.success(result);
    }

    /**
     * 分页查询用户消息列表
     * 合并广播消息和用户消息，按时间降序
     */
    @GetMapping
    public ApiResponse<PageResponse<MessageVO>> pageMessages(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<MessageVO> result = notificationQueryService.pageMessages(userId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 更新用户在某个 Feed 中的已读位置
     * cursor = max(oldCursor, newCursor)，永不回退
     */
    @PostMapping("/cursor")
    public ApiResponse<Void> updateCursor(
            @RequestParam Long userId,
            @RequestParam String feedType,
            @RequestParam Long cursor) {
        notificationQueryService.updateCursor(userId, feedType, cursor);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -30
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/notification/service/NotificationQueryService.java \
       src/main/java/com/notification/service/impl/NotificationQueryServiceImpl.java \
       src/main/java/com/notification/controller/NotificationController.java \
       src/main/java/com/notification/model/dto/response/FeedUnreadVO.java \
       src/main/java/com/notification/model/dto/response/MessageVO.java
git commit -m "refactor: rewrite NotificationController with feed/cursor query API"
```

---

### Task 7: 消息发送流程重构

**Files:**
- Modify: `src/main/java/com/notification/model/dto/request/UpstreamMessageDTO.java`
- Modify: `src/main/java/com/notification/service/UpstreamMessageService.java`
- Modify: `src/main/java/com/notification/service/impl/UpstreamMessageServiceImpl.java`

- [ ] **Step 1: 更新 UpstreamMessageDTO（新增 sendType 和 feedTypes）**

```java
package com.notification.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 上游消息 DTO
 * 支持广播消息（BROADCAST）和用户消息（USER）两种发送模式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamMessageDTO {

    @NotBlank
    private String messageId;

    @NotBlank
    private String appId;

    @NotBlank
    private String templateCode;

    /**
     * 发送类型: BROADCAST / USER
     * BROADCAST: 广播消息，不生成 user_message（读扩散）
     * USER: 用户消息，写入 user_message（写扩散）
     */
    private String sendType;

    /**
     * Feed 类型列表: system, vip, marketing, order, logistics, risk
     * 消息属于哪些消息流
     */
    private List<String> feedTypes;

    private Recipients recipients;
    private List<String> channels;
    private Map<String, String> params;
    private String category;
    private String priority;
    private String expireAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recipients {
        private List<Long> userIds;
        private List<String> wechatOpenIds;
        private List<String> emails;
        private List<String> phones;
    }
}
```

- [ ] **Step 2: 更新 UpstreamMessageService 接口**

```java
package com.notification.service;

import com.notification.model.dto.request.UpstreamMessageDTO;

/**
 * 上游消息服务接口
 * 支持广播消息（读扩散）和用户消息（写扩散）两种模式
 */
public interface UpstreamMessageService {

    /**
     * 处理上游消息
     * 根据 sendType 自动选择广播或用户消息流程
     */
    void processMessage(UpstreamMessageDTO message);
}
```

- [ ] **Step 3: 重构 UpstreamMessageServiceImpl**

```java
package com.notification.service.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.ChannelSendService;
import com.notification.exception.BusinessException;
import com.notification.exception.ResultCode;
import com.notification.mapper.MessageFeedMappingMapper;
import com.notification.mapper.NotificationMessageMapper;
import com.notification.mapper.UserMessageMapper;
import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.entity.Message;
import com.notification.model.entity.MessageFeedMapping;
import com.notification.model.entity.NotificationMessage;
import com.notification.model.entity.NotificationTemplate;
import com.notification.model.entity.UserMessage;
import com.notification.model.enums.*;
import com.notification.service.MessageService;
import com.notification.service.TemplateService;
import com.notification.service.UpstreamMessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 上游消息服务实现
 * 支持广播消息（读扩散）和用户消息（写扩散）两种模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamMessageServiceImpl implements UpstreamMessageService {

    private final TemplateService templateService;
    private final MessageService messageService;
    private final MessageFeedMappingMapper feedMappingMapper;
    private final UserMessageMapper userMessageMapper;
    private final NotificationMessageMapper notificationMessageMapper;
    private final ChannelSendService channelSendService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void processMessage(UpstreamMessageDTO dto) {
        log.info("处理上游消息: messageId={}, sendType={}, templateCode={}",
                dto.getMessageId(), dto.getSendType(), dto.getTemplateCode());

        // 1. 校验并获取模板
        NotificationTemplate template = templateService.findByCode(dto.getTemplateCode())
                .orElseThrow(() -> new BusinessException(ResultCode.TEMPLATE_NOT_FOUND));

        // 2. 确定发送类型（默认 USER）
        boolean isBroadcast = "BROADCAST".equalsIgnoreCase(dto.getSendType());
        MessageSendTypeEnum sendType = isBroadcast ? MessageSendTypeEnum.BROADCAST : MessageSendTypeEnum.USER;

        // 3. 确定 Feed 类型列表
        List<String> feedTypes = dto.getFeedTypes();
        if (feedTypes == null || feedTypes.isEmpty()) {
            String category = dto.getCategory() != null ? dto.getCategory() : FeedTypeEnum.SYSTEM.getCode();
            feedTypes = List.of(category);
        }

        // 4. 渲染标题
        String renderedTitle = templateService.renderTitle(template, dto.getParams());

        // 5. 保存消息主体
        Message message = Message.builder()
                .bizType(dto.getCategory())
                .title(renderedTitle)
                .sendType(sendType)
                .status(MessageStatusEnum.NORMAL)
                .createTime(LocalDateTime.now())
                .build();
        message = messageService.saveMessage(message);

        // 6. 建立消息与 Feed 的关联
        List<MessageFeedMapping> mappings = feedTypes.stream()
                .map(ft -> MessageFeedMapping.builder()
                        .messageId(message.getId())
                        .feedType(ft)
                        .build())
                .toList();
        feedMappingMapper.batchInsert(mappings);

        // 7. 根据发送类型处理
        if (isBroadcast) {
            // 广播消息：读扩散，不生成 user_message
            handleBroadcastMessage(dto, message, feedTypes, renderedTitle);
        } else {
            // 用户消息：写扩散，生成 user_message
            handleUserMessage(dto, message, template, renderedTitle);
        }

        log.info("上游消息处理完成: messageId={}, messageTableId={}", dto.getMessageId(), message.getId());
    }

    /**
     * 处理广播消息（读扩散）
     * 仅保存 message 和 mapping，不生成 user_message
     * 更新 Redis feed:max_cursor
     */
    private void handleBroadcastMessage(UpstreamMessageDTO dto, Message message,
                                        List<String> feedTypes, String renderedTitle) {
        // 更新每个 Feed 的 max_cursor
        for (String feedType : feedTypes) {
            String redisKey = MessageIdUtils.buildFeedMaxCursorKey(feedType);
            redisTemplate.opsForValue().set(redisKey, message.getId());
            log.debug("广播消息 Feed cursor 更新: feed={}, maxCursor={}", feedType, message.getId());
        }

        log.info("广播消息已处理（读扩散）: messageId={}, feeds={}", message.getId(), feedTypes);
    }

    /**
     * 处理用户消息（写扩散）
     * 为每个用户生成 user_message 记录
     * 同时兼容现有渠道发送流程
     */
    private void handleUserMessage(UpstreamMessageDTO dto, Message message,
                                   NotificationTemplate template, String renderedTitle) {
        List<Long> userIds = dto.getRecipients() != null && dto.getRecipients().getUserIds() != null
                ? dto.getRecipients().getUserIds()
                : List.of();

        if (userIds.isEmpty()) {
            log.warn("用户消息无收件人: messageId={}", dto.getMessageId());
            return;
        }

        String renderedContent = templateService.renderContent(template, dto.getParams());
        List<String> channels = dto.getChannels() != null
                ? dto.getChannels()
                : List.of("in_app");

        for (Long userId : userIds) {
            // 幂等校验（Redis 去重）
            String dedupKey = MessageIdUtils.buildDedupKey(dto.getMessageId(), userId);
            Boolean alreadySent = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1");
            if (Boolean.FALSE.equals(alreadySent)) {
                log.debug("重复消息跳过: messageId={}, userId={}", dto.getMessageId(), userId);
                continue;
            }
            redisTemplate.expire(dedupKey, 7, TimeUnit.DAYS);

            // 写入 user_message（写扩散）
            UserMessage userMsg = UserMessage.builder()
                    .userId(userId)
                    .messageId(message.getId())
                    .bizType(dto.getCategory())
                    .status(ReadStatusEnum.UNREAD)
                    .createTime(LocalDateTime.now())
                    .build();
            try {
                userMessageMapper.insert(userMsg);
            } catch (Exception e) {
                // UNIQUE(user_id, message_id) 保证幂等，重复插入忽略
                log.warn("user_message 重复插入忽略: userId={}, messageId={}", userId, message.getId());
            }

            // 兼容现有渠道发送流程（写入 notification_message + 渠道发送）
            for (String channel : channels) {
                NotificationMessage msg = NotificationMessage.builder()
                        .messageId(dto.getMessageId())
                        .userId(userId)
                        .category(dto.getCategory() != null ? dto.getCategory() : FeedTypeEnum.SYSTEM.getCode())
                        .channel(channel)
                        .title(renderedTitle)
                        .content(renderedContent)
                        .isRead(false)
                        .isDeleted(false)
                        .sendStatus(SendStatusEnum.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationMessageMapper.insert(msg);

                ChannelSendContext context = ChannelSendContext.builder()
                        .originalMessage(dto)
                        .template(template)
                        .userIds(List.of(userId))
                        .channel(channel)
                        .renderedTitle(renderedTitle)
                        .renderedContent(renderedContent)
                        .retryCount(0)
                        .build();

                channelSendService.sendAsync(context, msg.getId());
            }
        }

        log.info("用户消息已处理（写扩散）: messageId={}, 用户数={}", message.getId(), userIds.size());
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -30
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/notification/model/dto/request/UpstreamMessageDTO.java \
       src/main/java/com/notification/service/UpstreamMessageService.java \
       src/main/java/com/notification/service/impl/UpstreamMessageServiceImpl.java
git commit -m "feat: refactor upstream message service supporting broadcast (read-diffusion) and user (write-diffusion) messages"
```

---

### Task 8: Kafka 配置更新

**Files:**
- Modify: `src/main/java/com/notification/config/KafkaConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 更新 KafkaConfig（新增 recall 和 feed 相关 topics）**

```java
package com.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${kafka.topics.upstream-raw}")
    private String upstreamRawTopic;

    @Value("${kafka.topics.channel-send}")
    private String channelSendTopic;

    @Value("${kafka.topics.channel-retry}")
    private String channelRetryTopic;

    @Value("${kafka.topics.channel-dead-letter}")
    private String channelDeadLetterTopic;

    @Value("${kafka.topics.message-recall}")
    private String messageRecallTopic;

    @Bean
    public NewTopic upstreamRawTopic() {
        return TopicBuilder.name(upstreamRawTopic)
                .partitions(8).replicas(1).build();
    }

    @Bean
    public NewTopic channelSendTopic() {
        return TopicBuilder.name(channelSendTopic)
                .partitions(8).replicas(1).build();
    }

    @Bean
    public NewTopic channelRetryTopic() {
        return TopicBuilder.name(channelRetryTopic)
                .partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic channelDeadLetterTopic() {
        return TopicBuilder.name(channelDeadLetterTopic)
                .partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic messageRecallTopic() {
        return TopicBuilder.name(messageRecallTopic)
                .partitions(4).replicas(1).build();
    }
}
```

- [ ] **Step 2: 更新 application.yml（新增 message-recall topic）**

```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  consumer:
    group-id: notification-center-group
    auto-offset-reset: earliest
  admin:
    fail-fast: false
    properties:
      "default.api.timeout.ms": 3000
  topics:
    upstream-raw: notification.upstream.raw
    channel-send: notification.channel.send
    channel-retry: notification.channel.retry
    channel-dead-letter: notification.channel.dead-letter
    message-recall: notification.message.recall
```

- [ ] **Step 3: Compile**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -20
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/notification/config/KafkaConfig.java \
       src/main/resources/application.yml
git commit -m "feat: add message-recall Kafka topic configuration"
```

---

### Task 9: 消息撤回功能

**Files:**
- Create: `src/main/java/com/notification/model/dto/request/RecallRequest.java`
- Create: `src/main/java/com/notification/controller/RecallController.java`
- Create: `src/main/java/com/notification/kafka/MessageRecallConsumer.java`

- [ ] **Step 1: 创建 RecallRequest DTO**

```java
package com.notification.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 消息撤回请求 DTO
 */
@Data
public class RecallRequest {
    @NotNull
    private Long messageId;  // 消息 ID
    private String reason;   // 撤回原因
}
```

- [ ] **Step 2: 创建 RecallController**

```java
package com.notification.controller;

import com.notification.model.dto.request.RecallRequest;
import com.notification.model.dto.response.ApiResponse;
import com.notification.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 消息撤回 API
 * 采用逻辑删除，将 message 状态置为 RECALL
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recall")
@RequiredArgsConstructor
public class RecallController {

    private final MessageService messageService;

    /**
     * 撤回消息
     * 将 message 表状态更新为 RECALL，并通过 Kafka 通知在线客户端
     */
    @PostMapping
    public ApiResponse<Void> recallMessage(@Valid @RequestBody RecallRequest request) {
        messageService.recallMessage(request.getMessageId());
        log.info("消息已撤回: messageId={}, reason={}", request.getMessageId(), request.getReason());
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 3: 创建 MessageRecallConsumer**

```java
package com.notification.kafka;

import com.notification.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 消息撤回事件消费者
 * 消费 message-recall topic，执行消息逻辑删除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRecallConsumer {

    private final MessageService messageService;

    @KafkaListener(
            topics = "${kafka.topics.message-recall}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRecall(@Payload Long messageId) {
        log.info("收到消息撤回事件: messageId={}", messageId);
        try {
            messageService.recallMessage(messageId);
        } catch (Exception e) {
            log.error("消息撤回处理失败: messageId={}", messageId, e);
        }
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd D:/github/notification-center
mvn compile -q 2>&1 | head -20
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/notification/model/dto/request/RecallRequest.java \
       src/main/java/com/notification/controller/RecallController.java \
       src/main/java/com/notification/kafka/MessageRecallConsumer.java
git commit -m "feat: add message recall API and Kafka consumer"
```

---

## 自检清单

### 1. 规范覆盖检查

| 规范需求 | 对应任务 |
|---------|---------|
| message 表 | Task 1 (V3), Task 2 (实体), Task 3 (Mapper), Task 5 (Service) |
| message_feed_mapping 表 | Task 1, Task 2, Task 3 |
| user_message 表（写扩散） | Task 1, Task 2, Task 3 |
| user_feed_cursor 表 | Task 1, Task 2, Task 3 |
| push_task 表 | Task 1 (仅建表，阶段二实现) |
| Feed 模型 | Task 2 (FeedTypeEnum), Task 6 (查询) |
| Cursor 机制（Redis + MySQL） | Task 4 (Key工具), Task 5 (CursorService) |
| 广播消息（读扩散） | Task 7 (UpstreamMessageServiceImpl.handleBroadcastMessage) |
| 用户消息（写扩散） | Task 7 (UpstreamMessageServiceImpl.handleUserMessage) |
| 未读数计算（cursor差值） | Task 5 (CursorServiceImpl.getUnreadCount) |
| 消息撤回（逻辑删除） | Task 9 |
| 多端共享 Cursor | Task 5 (用户维度 Cursor) |
| Kafka 幂等（唯一索引） | Task 1 (uk_user_message), Task 7 (try-catch 重复) |
| Subscription 订阅 | Task 2 (实体), Task 3 (Mapper), Task 5 (Service) |
| 冷热分离 | 阶段四（当前未实现） |
| WebSocket | 阶段三（当前未实现） |
| 1024分片 | 阶段二（当前使用单表） |

### 2. 类型一致性检查

- FeedTypeEnum.SYSTEM/ORDER 等 code 值: `system`/`order` — 与 V3 表定义一致
- MessageStatusEnum.code: 0/1/2 — 与 message.status TINYINT 一致
- MessageSendTypeEnum.code: 0/1 — 与 message.send_type 一致
- ReadStatusEnum.code: 0/1 — 与 user_message.status 一致
- Redis key 模式: `user:{userId}:cursor` / `feed:{feedType}:max_cursor` — 与设计文档一致

### 3. 占位符检查

无 TBD、TODO 或未完成的占位符。阶段三/四内容已明确标注为后续阶段。
