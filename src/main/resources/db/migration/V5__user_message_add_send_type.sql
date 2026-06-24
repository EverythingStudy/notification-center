-- V5__user_message_add_send_type.sql
-- user_message 新增 send_type 区分 BROADCAST/USER 类型的已读记录

ALTER TABLE user_message ADD COLUMN send_type TINYINT NOT NULL DEFAULT 0 COMMENT '发送类型: 0=BROADCAST, 1=USER' AFTER biz_type;
