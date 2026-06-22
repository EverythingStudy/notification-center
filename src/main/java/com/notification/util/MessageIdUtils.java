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
