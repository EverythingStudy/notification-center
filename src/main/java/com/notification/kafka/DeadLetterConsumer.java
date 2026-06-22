package com.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.mapper.NotificationDeadLetterMapper;
import com.notification.model.entity.NotificationDeadLetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumes dead-letter messages from the channel-dead-letter topic.
 * Persists the failed message details to the notification_dead_letter
 * table for manual review and potential replay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final NotificationDeadLetterMapper deadLetterMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.channel-dead-letter}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onDeadLetter(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String messageId = root.has("messageId") ? root.get("messageId").asText() : "unknown";
            String channel = root.has("channel") ? root.get("channel").asText() : "unknown";
            int retryCount = root.has("retryCount") ? root.get("retryCount").asInt() : 0;
            String reason = root.has("reason") ? root.get("reason").asText() : "";

            NotificationDeadLetter deadLetter = NotificationDeadLetter.builder()
                    .messageId(messageId)
                    .originalPayload(payload)
                    .channel(channel)
                    .errorReason(reason)
                    .retryCount(retryCount)
                    .failedAt(LocalDateTime.now())
                    .isResolved(false)
                    .build();

            deadLetterMapper.insert(deadLetter);
            log.info("Dead letter persisted: messageId={}, channel={}, reason={}",
                    messageId, channel, reason);

        } catch (Exception e) {
            log.error("Failed to persist dead letter: payload={}", payload, e);
        }
    }
}
