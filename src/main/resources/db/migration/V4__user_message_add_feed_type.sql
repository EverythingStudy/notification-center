-- V4__user_message_add_feed_type.sql
-- user_message 表新增 feed_type 列（如已存在则跳过）

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_message' AND COLUMN_NAME = 'feed_type') = 0,
  'ALTER TABLE user_message ADD COLUMN feed_type VARCHAR(32) NOT NULL COMMENT \'Feed类型\' AFTER message_id',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_message' AND COLUMN_NAME = 'feed_type') = 1
   AND (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_message' AND INDEX_NAME = 'idx_user_feed_status') = 0,
  'CREATE INDEX idx_user_feed_status ON user_message(user_id, feed_type, status)',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
