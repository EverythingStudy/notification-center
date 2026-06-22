package com.notification.model.entity;

import com.notification.model.enums.ReadStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户消息实体（写扩散）
 * 仅用于 USER 类型消息，每用户每消息一条记录
 * UNIQUE(user_id, message_id) 保证消费幂等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessage {
    private Long id;
    private Long userId;
    private Long messageId;
    private String bizType;
    private ReadStatusEnum status;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
