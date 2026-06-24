-- V4__user_message_add_feed_type.sql
-- user_message 表新增 feed_type 列，用于基于 feed 的未读查询

ALTER TABLE user_message ADD COLUMN feed_type VARCHAR(32) NOT NULL COMMENT 'Feed类型' AFTER message_id;
ALTER TABLE user_message ADD INDEX idx_user_feed_status (user_id, feed_type, status);
