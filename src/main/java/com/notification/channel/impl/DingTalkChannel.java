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
 * DingTalk robot webhook channel.
 * Sends message to configured DingTalk group robot webhook URL.
 */
@Slf4j
@Component
public class DingTalkChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${channel.dingtalk.webhook-url:}")
    private String webhookUrl;

    @Override
    public String channelName() {
        return "dingtalk";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.INSTANT;
    }

    @Override
    public void send(ChannelSendContext context) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("DingTalk webhook URL not configured, skipping message: {}", context.getRenderedTitle());
            return;
        }

        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "title", context.getRenderedTitle(),
                        "text", "### " + context.getRenderedTitle() + "\n" + context.getRenderedContent()
                )
        );

        restTemplate.postForEntity(webhookUrl, body, String.class);
        log.info("DingTalk message sent: title={}", context.getRenderedTitle());
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "dingtalk".equals(context.getChannel());
    }
}
