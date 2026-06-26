-- V3__add_message_center_tables.sql
-- 消息中心核心表：message / message_feed_mapping / user_message / user_feed_cursor / push_task / subscription

CREATE TABLE IF NOT EXISTS message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    biz_type    VARCHAR(64)   NOT NULL COMMENT '业务类型',
    title       VARCHAR(256)  NOT NULL COMMENT '标题',
    content_url VARCHAR(1024) COMMENT '内容存储地址(OSS)',
    send_type   TINYINT       NOT NULL DEFAULT 0 COMMENT '发送类型: 0=BROADCAST, 1=USER',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态: 0=NORMAL, 1=RECALL, 2=EXPIRE',
    create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    expire_time DATETIME(3)   COMMENT '过期时间',
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主体表';

CREATE TABLE IF NOT EXISTS message_feed_mapping (
    message_id BIGINT      NOT NULL COMMENT '消息ID',
    feed_type  VARCHAR(32) NOT NULL COMMENT 'Feed类型: SYSTEM/VIP/MARKETING/ORDER/LOGISTICS/RISK',
    PRIMARY KEY (message_id, feed_type),
    INDEX idx_feed_type (feed_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息-Feed映射表';

CREATE TABLE IF NOT EXISTS user_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    message_id  BIGINT       NOT NULL COMMENT '消息ID',
    biz_type    VARCHAR(64)  NOT NULL COMMENT '业务类型',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '已读状态: 0=UNREAD, 1=READ',
    read_time   DATETIME(3)  COMMENT '阅读时间',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_user_message (user_id, message_id) COMMENT '消费幂等',
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_create (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息表(写扩散)';

CREATE TABLE IF NOT EXISTS user_feed_cursor (
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    feed_type   VARCHAR(32) NOT NULL COMMENT 'Feed类型',
    `cursor`    BIGINT      NOT NULL DEFAULT 0 COMMENT '已读位置(对应message.id)',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_user_feed (user_id, feed_type) COMMENT '一个用户每个Feed只有一个游标'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Feed游标表';

CREATE TABLE IF NOT EXISTS push_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id  BIGINT      NOT NULL COMMENT '消息ID',
    target_type VARCHAR(32) NOT NULL COMMENT '推送目标: in_app/sms/email/wechat/dingtalk/webhook',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '状态: 0=PENDING, 1=SENT, 2=FAILED',
    retry_count INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_message_id (message_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送任务表';

CREATE TABLE IF NOT EXISTS subscription (
    user_id   BIGINT      NOT NULL COMMENT '用户ID',
    feed_type VARCHAR(32) NOT NULL COMMENT 'Feed类型',
    PRIMARY KEY (user_id, feed_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅关系表';
