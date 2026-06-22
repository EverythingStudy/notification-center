package com.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.channel.ChannelSendContext;
import com.notification.channel.ChannelSendService;
import com.notification.mapper.NotificationTemplateMapper;
import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.entity.NotificationTemplate;
import com.notification.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consumes retry messages from the channel-retry topic.
 * Reconstructs the send context from the serialized payload and
 * invokes ChannelSendService.sendSync() for at-least-once retry.
 * <p>
 * Exponential backoff is handled at the Kafka consumer level
 * (delivery pauses between retries via the retry topic partition).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryConsumer {

    private final ChannelSendService channelSendService;
    private final TemplateService templateService;
    private final NotificationTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.channel-retry}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRetry(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            Long messageRecordId = root.has("messageRecordId") && !root.get("messageRecordId").isNull()
                    ? root.get("messageRecordId").asLong()
                    : null;
            String channel = root.get("channel").asText();
            int retryCount = root.get("retryCount").asInt();
            String templateCode = root.get("templateCode").asText();

            UpstreamMessageDTO originalMessage = objectMapper.treeToValue(
                    root.get("originalMessage"), UpstreamMessageDTO.class);

            Optional<NotificationTemplate> templateOpt = templateMapper.findByTemplateCode(templateCode);
            if (templateOpt.isEmpty()) {
                log.error("Template not found in retry: templateCode={}, messageId={}",
                        templateCode, originalMessage.getMessageId());
                return;
            }
            NotificationTemplate template = templateOpt.get();

            String renderedTitle = templateService.renderTitle(template, originalMessage.getParams());
            String renderedContent = templateService.renderContent(template, originalMessage.getParams());

            ChannelSendContext context = ChannelSendContext.builder()
                    .originalMessage(originalMessage)
                    .template(template)
                    .userIds(originalMessage.getRecipients() != null
                            ? originalMessage.getRecipients().getUserIds()
                            : java.util.List.of())
                    .channel(channel)
                    .renderedTitle(renderedTitle)
                    .renderedContent(renderedContent)
                    .retryCount(retryCount)
                    .build();

            channelSendService.sendSync(context, messageRecordId);

        } catch (Exception e) {
            log.error("Failed to process retry message: payload={}", payload, e);
        }
    }
}
