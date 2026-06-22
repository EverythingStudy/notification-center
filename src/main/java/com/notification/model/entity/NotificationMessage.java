package com.notification.model.entity;

import com.notification.model.enums.SendStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private Long id;

    private String messageId;

    private Long userId;

    private String category;

    private String channel;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    private Boolean isRead;

    private Boolean isDeleted;

    private SendStatusEnum sendStatus;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;
}