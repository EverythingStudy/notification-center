package com.notification.service.impl;

import com.notification.model.entity.NotificationTemplate;
import com.notification.repository.NotificationTemplateRepository;
import com.notification.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public Optional<NotificationTemplate> findByCode(String templateCode) {
        return templateRepository.findByTemplateCode(templateCode);
    }

    @Override
    public String renderTitle(NotificationTemplate template, Map<String, String> params) {
        String title = template.getTitleTemplate();
        if (title == null || params == null) {
            return title;
        }
        return renderTemplate(title, params);
    }

    @Override
    public String renderContent(NotificationTemplate template, Map<String, String> params) {
        String content = template.getContentTemplate();
        if (content == null || params == null) {
            return content;
        }
        return renderTemplate(content, params);
    }

    private String renderTemplate(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}