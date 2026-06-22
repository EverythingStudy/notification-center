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
