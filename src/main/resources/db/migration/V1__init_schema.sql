CREATE TABLE notification_message (
    id          BIGINT AUTO_INCREMENT,
    message_id  VARCHAR(64)    NOT NULL COMMENT 'Original message ID for dedup',
    user_id     BIGINT         NOT NULL COMMENT 'Recipient user ID',
    category    VARCHAR(32)    NOT NULL COMMENT 'Category: system/payment/marketing/activity',
    channel     VARCHAR(32)    NOT NULL COMMENT 'Send channel: in_app/sms/email/wechat/dingtalk/webhook',
    title       VARCHAR(256)   NOT NULL COMMENT 'Message title',
    content     TEXT           COMMENT 'Message body',
    biz_type    VARCHAR(64)    COMMENT 'Business type',
    biz_id      VARCHAR(128)   COMMENT 'Business ID',
    is_read     TINYINT(1)     NOT NULL DEFAULT 0,
    is_deleted  TINYINT(1)     NOT NULL DEFAULT 0,
    send_status TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '0-pending 1-sending 2-success 3-failed',
    created_at  DATETIME(3)    NOT NULL,
    read_at     DATETIME(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notification messages';

CREATE INDEX idx_user_cat_time ON notification_message(user_id, category, created_at DESC);
CREATE INDEX idx_user_read_cat ON notification_message(user_id, is_read, category);
CREATE UNIQUE INDEX uk_message_user ON notification_message(message_id, user_id);

CREATE TABLE notification_template (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code     VARCHAR(64)  NOT NULL UNIQUE,
    name              VARCHAR(128) NOT NULL,
    channels          VARCHAR(256) NOT NULL COMMENT 'Default channels JSON array',
    category          VARCHAR(32)  NOT NULL DEFAULT 'system',
    title_template    VARCHAR(512),
    content_template  TEXT,
    wechat_template_id VARCHAR(64),
    sms_template_name VARCHAR(64),
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notification templates';

CREATE TABLE notification_dead_letter (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id       VARCHAR(64)  NOT NULL,
    original_payload TEXT         NOT NULL,
    channel          VARCHAR(32)  NOT NULL,
    error_reason     VARCHAR(512),
    retry_count      INT          NOT NULL DEFAULT 0,
    failed_at        DATETIME(3)  NOT NULL,
    is_resolved      TINYINT(1)   NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dead letter records';