package com.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.component.RateLimiter;
import com.notification.mapper.NotificationMessageMapper;
import com.notification.model.entity.NotificationMessage;
import com.notification.model.enums.SendStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates asynchronous channel sending with retry and dead-letter fallback.
 * <p>
 * Failure strategy (at-least-once delivery):
 * <ul>
 *   <li>On channel send failure → publish to channel-retry Kafka topic</li>
 *   <li>Retry consumer re-processes with exponential backoff (max 5 attempts)</li>
 *   <li>After exhausting retries → publish to channel-dead-letter topic → persisted to DB</li>
 *   <li>Operator dashboard or admin script can replay dead-letter records</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSendService {

    private final ChannelRouter channelRouter;
    private final RateLimiter rateLimiter;
    private final NotificationMessageMapper messageMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.channel-retry}")
    private String retryTopic;

    @Value("${kafka.topics.channel-dead-letter}")
    private String deadLetterTopic;

    @Value("${app.retry.max-attempts:5}")
    private int maxRetryAttempts;

    private static final Map<String, Long> CHANNEL_RATE_LIMITS = new HashMap<>();

    static {
        CHANNEL_RATE_LIMITS.put("sms", 20L);
        CHANNEL_RATE_LIMITS.put("email", 100L);
        CHANNEL_RATE_LIMITS.put("wechat", 50L);
        CHANNEL_RATE_LIMITS.put("dingtalk", 200L);
        CHANNEL_RATE_LIMITS.put("webhook", 500L);
        CHANNEL_RATE_LIMITS.put("in_app", Long.MAX_VALUE);
    }

    /**
     * Send asynchronously through the configured channel.
     * In-app channel is sent directly (local DB write, no retry needed);
     * external channels go through rate-limiting and retry logic.
     */
    @Async("channelSendExecutor")
    public void sendAsync(ChannelSendContext context, Long messageRecordId) {
        NotificationChannel channel = channelRouter.getChannel(context.getChannel());
        if (channel == null) {
            log.error("Channel not found: {}", context.getChannel());
            return;
        }

        if ("in_app".equals(context.getChannel())) {
            try {
                channel.send(context);
                updateMessageStatus(messageRecordId, SendStatusEnum.SUCCESS);
                return;
            } catch (Exception e) {
                log.error("In-app send failed: messageId={}", context.getOriginalMessage().getMessageId(), e);
                updateMessageStatus(messageRecordId, SendStatusEnum.FAILED);
                return;
            }
        }

        long rateLimit = CHANNEL_RATE_LIMITS.getOrDefault(context.getChannel(), 50L);
        if (!rateLimiter.tryAcquire(context.getChannel(), rateLimit)) {
            log.warn("Rate limited, routing to retry: channel={}, messageId={}",
                    context.getChannel(), context.getOriginalMessage().getMessageId());
            publishToRetry(context, messageRecordId, "rate_limited");
            return;
        }

        try {
            channel.send(context);
            //更新消息状态为成功
            updateMessageStatus(messageRecordId, SendStatusEnum.SUCCESS);
            log.debug("Channel send success: channel={}, messageId={}",
                    context.getChannel(), context.getOriginalMessage().getMessageId());
        } catch (Exception e) {
            log.error("Channel send failed: channel={}, messageId={}",
                    context.getChannel(), context.getOriginalMessage().getMessageId(), e);

            int retryCount = context.getRetryCount() + 1;
            if (retryCount <= maxRetryAttempts) {
                context.setRetryCount(retryCount);
                //放入kafka重试队列
                publishToRetry(context, messageRecordId, e.getMessage());
            } else {
                //放入kafka死信队列
                publishToDeadLetter(context, messageRecordId, e.getMessage());
                //更新消息状态为失败
                updateMessageStatus(messageRecordId, SendStatusEnum.FAILED);
            }
        }
    }

    /**
     * Synchronous send used by the retry consumer.
     */
    public void sendSync(ChannelSendContext context, Long messageRecordId) {
        NotificationChannel channel = channelRouter.getChannel(context.getChannel());
        if (channel == null) {
            log.error("Channel not found in retry: {}", context.getChannel());
            return;
        }

        long rateLimit = CHANNEL_RATE_LIMITS.getOrDefault(context.getChannel(), 50L);
        if (!rateLimiter.tryAcquire(context.getChannel(), rateLimit)) {
            log.warn("Retry rate limited, re-queuing: channel={}, messageId={}",
                    context.getChannel(), context.getOriginalMessage().getMessageId());
            publishToRetry(context, messageRecordId, "rate_limited");
            return;
        }

        try {
            channel.send(context);
            updateMessageStatus(messageRecordId, SendStatusEnum.SUCCESS);
        } catch (Exception e) {
            log.error("Retry send failed: channel={}, messageId={}, attempt={}",
                    context.getChannel(), context.getOriginalMessage().getMessageId(),
                    context.getRetryCount());
            int retryCount = context.getRetryCount() + 1;
            if (retryCount <= maxRetryAttempts) {
                context.setRetryCount(retryCount);
                publishToRetry(context, messageRecordId, e.getMessage());
            } else {
                publishToDeadLetter(context, messageRecordId, e.getMessage());
                updateMessageStatus(messageRecordId, SendStatusEnum.FAILED);
            }
        }
    }

    /**
     * kafka重试队列
     * Publishes retry message to Kafka channel-retry topic.
     * The payload includes the serialized UpstreamMessageDTO and templateCode,
     * so the retry consumer can reconstruct the send context.
     */
    private void publishToRetry(ChannelSendContext context, Long messageRecordId, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageRecordId", messageRecordId);
            payload.put("messageId", context.getOriginalMessage().getMessageId());
            payload.put("channel", context.getChannel());
            payload.put("retryCount", context.getRetryCount());
            payload.put("reason", reason);
            payload.put("templateCode", context.getTemplate().getTemplateCode());
            payload.put("originalMessage", context.getOriginalMessage());
            kafkaTemplate.send(retryTopic, context.getOriginalMessage().getMessageId(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to publish retry message", e);
        }
    }

    /**
     * kafka死信队列
     * Publishes dead-letter message to Kafka channel-dead-letter topic.
     */
    private void publishToDeadLetter(ChannelSendContext context, Long messageRecordId, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageRecordId", messageRecordId);
            payload.put("messageId", context.getOriginalMessage().getMessageId());
            payload.put("channel", context.getChannel());
            payload.put("retryCount", context.getRetryCount());
            payload.put("reason", reason);
            payload.put("templateCode", context.getTemplate().getTemplateCode());
            payload.put("originalMessage", context.getOriginalMessage());
            kafkaTemplate.send(deadLetterTopic, context.getOriginalMessage().getMessageId(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to publish dead letter", e);
        }
    }

    private void updateMessageStatus(Long messageRecordId, SendStatusEnum status) {
        if (messageRecordId == null) return;
        Optional<NotificationMessage> opt = messageMapper.findById(messageRecordId);
        opt.ifPresent(msg -> {
            msg.setSendStatus(status);
            messageMapper.updateById(msg);
        });
    }
}
