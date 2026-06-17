package com.notification.model.entity;

import com.notification.model.enums.SendStatusEnum;
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
@Table(name = "notification_message", indexes = {
    @Index(name = "idx_user_cat_time", columnList = "userId, category, createdAt DESC"),
    @Index(name = "idx_user_read_cat", columnList = "userId, isRead, category")
})
public class NotificationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "biz_type", length = 64)
    private String bizType;

    @Column(name = "biz_id", length = 128)
    private String bizId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "send_status", nullable = false)
    private SendStatusEnum sendStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    public void prePersist() {
        if (isRead == null) isRead = false;
        if (isDeleted == null) isDeleted = false;
        if (sendStatus == null) sendStatus = SendStatusEnum.PENDING;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}