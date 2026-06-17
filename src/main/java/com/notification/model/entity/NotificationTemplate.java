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
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, unique = true, length = 64)
    private String templateCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "channels", nullable = false, length = 256)
    private String channels;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "title_template", length = 512)
    private String titleTemplate;

    @Column(name = "content_template", columnDefinition = "TEXT")
    private String contentTemplate;

    @Column(name = "wechat_template_id", length = 64)
    private String wechatTemplateId;

    @Column(name = "sms_template_name", length = 64)
    private String smsTemplateName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}