package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeadLetter {

    private Long id;

    private String messageId;

    private String originalPayload;

    private String channel;

    private String errorReason;

    private Integer retryCount;

    private LocalDateTime failedAt;

    private Boolean isResolved;
}