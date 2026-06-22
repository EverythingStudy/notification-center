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
 * WeChat official account template message channel.
 * Sends template messages via WeChat API (gets access token first, then sends).
 */
@Slf4j
@Component
public class WeChatChannel implements NotificationChannel {

    private static final String GET_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appId}&secret={secret}";
    private static final String SEND_TEMPLATE_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token={token}";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${channel.wechat.app-id:}")
    private String appId;

    @Value("${channel.wechat.app-secret:}")
    private String appSecret;

    @Override
    public String channelName() {
        return "wechat";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.INSTANT;
    }

    @Override
    public void send(ChannelSendContext context) {
        List<String> openIds = context.getOriginalMessage().getRecipients() != null
                ? context.getOriginalMessage().getRecipients().getWechatOpenIds()
                : List.of();

        if (openIds.isEmpty()) {
            log.warn("No WeChat recipients, skipping");
            return;
        }

        if (appId == null || appId.isEmpty()) {
            log.warn("WeChat app-id not configured, logging instead: openIds={}", openIds);
            return;
        }

        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.error("Failed to get WeChat access token");
            return;
        }

        String templateId = context.getTemplate().getWechatTemplateId();
        for (String openId : openIds) {
            try {
                Map<String, Object> body = Map.of(
                        "touser", openId,
                        "template_id", templateId,
                        "data", buildTemplateData(context.getOriginalMessage().getParams()),
                        "url", ""
                );
                restTemplate.postForEntity(
                        SEND_TEMPLATE_URL.replace("{token}", accessToken),
                        body, String.class);
                log.debug("WeChat template message sent to: {}", openId);
            } catch (Exception e) {
                log.error("Failed to send WeChat message to {}: {}", openId, e.getMessage());
                throw new RuntimeException("WeChat send failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "wechat".equals(context.getChannel());
    }

    private String getAccessToken() {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    GET_TOKEN_URL, Map.class, appId, appSecret);
            if (response != null && response.containsKey("access_token")) {
                return (String) response.get("access_token");
            }
            log.error("WeChat token response: {}", response);
            return null;
        } catch (Exception e) {
            log.error("Failed to get WeChat access token: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildTemplateData(Map<String, String> params) {
        if (params == null) return Map.of();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            data.put(entry.getKey(), Map.of("value", entry.getValue(), "color", "#173177"));
        }
        return data;
    }
}
