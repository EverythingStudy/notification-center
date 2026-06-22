package com.notification.service;

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
