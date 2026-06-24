package com.notification.model.entity;

import com.notification.model.enums.ReadStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户消息实体
 * status=UNREAD: USER 类型消息发送时写入（待读）
 * status=READ:   逐条已读时写入/转换（含 BROADCAST 和 USER）
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
    private String feedType;
    private String bizType;
    private Integer sendType;       // 0=BROADCAST, 1=USER
    private ReadStatusEnum status;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
