package com.notification.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_dead_letter")
public class NotificationDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "original_payload", nullable = false, columnDefinition = "TEXT")
    private String originalPayload;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "error_reason", length = 512)
    private String errorReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved;

    @PrePersist
    public void prePersist() {
        if (retryCount == null) retryCount = 0;
        if (isResolved == null) isResolved = false;
        if (failedAt == null) failedAt = LocalDateTime.now();
    }
}