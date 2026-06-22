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
public class NotificationTemplate {

    private Long id;

    private String templateCode;

    private String name;

    private String channels;

    private String category;

    private String titleTemplate;

    private String contentTemplate;

    private String wechatTemplateId;

    private String smsTemplateName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}