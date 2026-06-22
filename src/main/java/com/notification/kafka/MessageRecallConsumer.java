package com.notification.kafka;

import com.notification.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 消息撤回事件消费者
 * 消费 message-recall topic，执行消息逻辑删除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRecallConsumer {

    private final MessageService messageService;

    @KafkaListener(
            topics = "${kafka.topics.message-recall}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRecall(@Payload Long messageId) {
        log.info("收到消息撤回事件: messageId={}", messageId);
        try {
            messageService.recallMessage(messageId);
        } catch (Exception e) {
            log.error("消息撤回处理失败: messageId={}", messageId, e);
        }
    }
}
