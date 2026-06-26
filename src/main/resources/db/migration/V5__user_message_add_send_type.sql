-- V5__user_message_add_send_type.sql
-- user_message 新增 send_type 列（如已存在则跳过）

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_message' AND COLUMN_NAME = 'send_type') = 0,
  'ALTER TABLE user_message ADD COLUMN send_type TINYINT NOT NULL DEFAULT 0 COMMENT \'发送类型: 0=BROADCAST, 1=USER\' AFTER biz_type',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
