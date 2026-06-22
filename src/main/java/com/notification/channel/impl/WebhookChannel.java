package com.notification.channel.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.NotificationChannel;
import com.notification.model.enums.ChannelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Webhook channel for downstream service integration.
 * Sends HTTP POST with JSON payload to a configured webhook URL.
 */
@Slf4j
@Component
public class WebhookChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${channel.webhook.url:}")
    private String webhookUrl;

    @Override
    public String channelName() {
        return "webhook";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.INSTANT;
    }

    @Override
    public void send(ChannelSendContext context) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL not configured, skipping message: {}", context.getRenderedTitle());
            return;
        }

        Map<String, Object> payload = Map.of(
                "messageId", context.getOriginalMessage().getMessageId(),
                "title", context.getRenderedTitle(),
                "content", context.getRenderedContent(),
                "category", context.getOriginalMessage().getCategory(),
                "appId", context.getOriginalMessage().getAppId(),
                "params", context.getOriginalMessage().getParams() != null
                        ? context.getOriginalMessage().getParams()
                        : Map.of()
        );

        restTemplate.postForEntity(webhookUrl, payload, String.class);
        log.info("Webhook message sent: title={}", context.getRenderedTitle());
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "webhook".equals(context.getChannel());
    }
}
