package com.notification.service;

import com.notification.model.entity.NotificationTemplate;

import java.util.Optional;

public interface TemplateService {
    Optional<NotificationTemplate> findByCode(String templateCode);
    String renderTitle(NotificationTemplate template, java.util.Map<String, String> params);
    String renderContent(NotificationTemplate template, java.util.Map<String, String> params);
}