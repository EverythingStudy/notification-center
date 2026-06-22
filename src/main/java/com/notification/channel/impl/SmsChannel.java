package com.notification.channel.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.NotificationChannel;
import com.notification.model.enums.ChannelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * SMS channel via HTTP provider API.
 * Sends template-based SMS messages through a configurable SMS gateway.
 */
@Slf4j
@Component
public class SmsChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${channel.sms.api-url:}")
    private String apiUrl;

    @Value("${channel.sms.app-key:}")
    private String appKey;

    @Value("${channel.sms.app-secret:}")
    private String appSecret;

    @Override
    public String channelName() {
        return "sms";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.INSTANT;
    }

    @Override
    public void send(ChannelSendContext context) {
        List<String> phones = context.getOriginalMessage().getRecipients() != null
                ? context.getOriginalMessage().getRecipients().getPhones()
                : List.of();

        if (phones.isEmpty()) {
            log.warn("No SMS recipients, skipping");
            return;
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            log.warn("SMS API URL not configured, logging instead: phones={}, content={}",
                    phones, context.getRenderedContent());
            return;
        }

        String templateCode = context.getTemplate().getSmsTemplateName();
        for (String phone : phones) {
            try {
                Map<String, Object> request = Map.of(
                        "phone", phone,
                        "templateCode", templateCode != null ? templateCode : "default",
                        "templateParam", context.getOriginalMessage().getParams() != null
                                ? context.getOriginalMessage().getParams()
                                : Map.of(),
                        "appKey", appKey,
                        "signName", "Notification"
                );
                restTemplate.postForEntity(apiUrl, request, String.class);
                log.debug("SMS sent to: {}", phone);
            } catch (Exception e) {
                log.error("Failed to send SMS to {}: {}", phone, e.getMessage());
                throw new RuntimeException("SMS send failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "sms".equals(context.getChannel());
    }
}
