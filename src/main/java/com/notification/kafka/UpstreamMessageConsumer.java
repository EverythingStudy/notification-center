package com.notification.kafka;

import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.service.UpstreamMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes raw upstream messages from Kafka.
 * Deserializes JSON to UpstreamMessageDTO and delegates
 * to UpstreamMessageService for processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpstreamMessageConsumer {

    private final UpstreamMessageService upstreamMessageService;

    @KafkaListener(
            topics = "${kafka.topics.upstream-raw}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(@Payload UpstreamMessageDTO message) {
        log.info("Consumed upstream message from Kafka: messageId={}, templateCode={}",
                message.getMessageId(), message.getTemplateCode());
        try {
            upstreamMessageService.processMessage(message);
            log.debug("Upstream message processed: messageId={}", message.getMessageId());
        } catch (Exception e) {
            log.error("Failed to process upstream message: messageId={}",
                    message.getMessageId(), e);
        }
    }
}