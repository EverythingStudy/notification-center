package com.notification.channel.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.NotificationChannel;
import com.notification.mapper.NotificationMessageMapper;
import com.notification.model.entity.NotificationMessage;
import com.notification.model.enums.ChannelTypeEnum;
import com.notification.model.enums.SendStatusEnum;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final NotificationMessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String channelName() {
        return "in_app";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.INSTANT;
    }

    @Override
    public void send(ChannelSendContext context) {
        log.info("Sending in-app notification: userIds={}, title={}",
                context.getUserIds(), context.getRenderedTitle());

        for (Long userId : context.getUserIds()) {
            NotificationMessage message = NotificationMessage.builder()
                    .messageId(context.getOriginalMessage().getMessageId())
                    .userId(userId)
                    .category(context.getOriginalMessage().getCategory() != null
                            ? context.getOriginalMessage().getCategory() : "system")
                    .channel("in_app")
                    .title(context.getRenderedTitle())
                    .content(context.getRenderedContent())
                    .isRead(false)
                    .isDeleted(false)
                    .sendStatus(SendStatusEnum.SUCCESS)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(message);

            String unreadKey = MessageIdUtils.buildUnreadCategoryKey(userId,
                    context.getOriginalMessage().getCategory() != null
                            ? context.getOriginalMessage().getCategory() : "system");
            redisTemplate.opsForValue().increment(unreadKey);

            log.debug("In-app message saved: userId={}, messageId={}",
                    userId, context.getOriginalMessage().getMessageId());
        }
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "in_app".equals(context.getChannel());
    }
}
