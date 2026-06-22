package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息-Feed 映射实体
 * 一条消息可以属于多个 Feed（如 VIP 活动同时属于 VIP 和 MARKETING）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageFeedMapping {
    private Long messageId;
    private String feedType;
}
