package com.notification.service.impl;

import com.notification.exception.BusinessException;
import com.notification.exception.ResultCode;
import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.channel.ChannelRouter;
import com.notification.channel.ChannelSendContext;
import com.notification.model.entity.NotificationMessage;
import com.notification.model.entity.NotificationTemplate;
import com.notification.model.enums.NotificationCategoryEnum;
import com.notification.model.enums.PriorityEnum;
import com.notification.repository.NotificationMessageRepository;
import com.notification.service.TemplateService;
import com.notification.service.UpstreamMessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamMessageServiceImpl implements UpstreamMessageService {

    private final TemplateService templateService;
    private final NotificationMessageRepository messageRepository;
    private final ChannelRouter channelRouter;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void processMessage(UpstreamMessageDTO message) {
        log.info("Processing upstream message: messageId={}, appId={}, templateCode={}",
                message.getMessageId(), message.getAppId(), message.getTemplateCode());

        NotificationTemplate template = templateService.findByCode(message.getTemplateCode())
                .orElseThrow(() -> new BusinessException(ResultCode.TEMPLATE_NOT_FOUND));

        String category = message.getCategory() != null ? message.getCategory() : NotificationCategoryEnum.SYSTEM.getCode();
        PriorityEnum priority = PriorityEnum.fromValue(message.getPriority());

        List<Long> userIds = message.getRecipients() != null && message.getRecipients().getUserIds() != null
                ? message.getRecipients().getUserIds()
                : List.of();

        String renderedTitle = templateService.renderTitle(template, message.getParams());
        String renderedContent = templateService.renderContent(template, message.getParams());

        for (Long userId : userIds) {
            String dedupKey = MessageIdUtils.buildDedupKey(message.getMessageId(), userId);
            Boolean alreadySent = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1");
            if (Boolean.FALSE.equals(alreadySent)) {
                log.debug("Duplicate message skipped: messageId={}, userId={}", message.getMessageId(), userId);
                continue;
            }

            List<String> channels = message.getChannels() != null
                    ? message.getChannels()
                    : List.of("in_app");

            for (String channel : channels) {
                NotificationMessage msg = NotificationMessage.builder()
                        .messageId(message.getMessageId())
                        .userId(userId)
                        .category(category)
                        .channel(channel)
                        .title(renderedTitle)
                        .content(renderedContent)
                        .isRead(false)
                        .isDeleted(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                messageRepository.save(msg);

                ChannelSendContext context = ChannelSendContext.builder()
                        .originalMessage(message)
                        .template(template)
                        .userIds(List.of(userId))
                        .channel(channel)
                        .renderedTitle(renderedTitle)
                        .renderedContent(renderedContent)
                        .build();

                channelRouter.getChannel(channel);
            }
        }

        log.info("Upstream message processed: messageId={}", message.getMessageId());
    }
}