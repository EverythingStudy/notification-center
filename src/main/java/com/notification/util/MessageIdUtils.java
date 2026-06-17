package com.notification.util;

public class MessageIdUtils {

    public static String generateMessageId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public static String buildDedupKey(String messageId, Long userId) {
        return "notify:dedup:" + messageId + ":" + userId;
    }

    public static String buildUnreadKey(Long userId) {
        return "notify:unread:" + userId;
    }

    public static String buildUnreadCategoryKey(Long userId, String category) {
        return "notify:unread:" + userId + ":" + category;
    }
}