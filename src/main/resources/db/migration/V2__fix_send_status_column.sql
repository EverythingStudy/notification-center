-- V2: Fix send_status column type
-- MySQL TINYINT(1) is treated as BIT by JDBC driver, causing Hibernate
-- schema validation error for ENUM ORDINAL columns which expect TINYINT.
-- Using TINYINT(4) avoids this mapping issue.
ALTER TABLE notification_message MODIFY COLUMN send_status TINYINT(4) NOT NULL DEFAULT 0 COMMENT '0-pending 1-sending 2-success 3-failed';