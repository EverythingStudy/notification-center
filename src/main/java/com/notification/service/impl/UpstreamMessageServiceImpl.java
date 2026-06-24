package com.notification.service.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.ChannelSendService;
import com.notification.exception.BusinessException;
import com.notification.exception.ResultCode;
import com.notification.mapper.MessageFeedMappingMapper;
import com.notification.mapper.NotificationMessageMapper;
import com.notification.mapper.UserMessageMapper;
import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.entity.Message;
import com.notification.model.entity.MessageFeedMapping;
import com.notification.model.entity.NotificationMessage;
import com.notification.model.entity.NotificationTemplate;
import com.notification.model.entity.UserMessage;
import com.notification.model.enums.FeedTypeEnum;
import com.notification.model.enums.ReadStatusEnum;
import com.notification.model.enums.MessageSendTypeEnum;
import com.notification.model.enums.MessageStatusEnum;
import com.notification.model.enums.SendStatusEnum;
import com.notification.service.MessageService;
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

/**
 * 上游消息服务实现
 * 支持广播消息（读扩散）和用户消息（写扩散）两种模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamMessageServiceImpl implements UpstreamMessageService {

    private final TemplateService templateService;
    private final MessageService messageService;
    private final MessageFeedMappingMapper feedMappingMapper;
    private final NotificationMessageMapper notificationMessageMapper;
    private final UserMessageMapper userMessageMapper;
    private final ChannelSendService channelSendService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void processMessage(UpstreamMessageDTO dto) {
        log.info("处理上游消息: messageId={}, sendType={}, templateCode={}",
                dto.getMessageId(), dto.getSendType(), dto.getTemplateCode());

        // 1. 校验并获取模板
        NotificationTemplate template = templateService.findByCode(dto.getTemplateCode())
                .orElseThrow(() -> new BusinessException(ResultCode.TEMPLATE_NOT_FOUND));

        // 2. 确定发送类型（默认 USER）
        boolean isBroadcast = "BROADCAST".equalsIgnoreCase(dto.getSendType());
        MessageSendTypeEnum sendType = isBroadcast ? MessageSendTypeEnum.BROADCAST : MessageSendTypeEnum.USER;

        // 3. 确定 Feed 类型列表
        List<String> rawFeedTypes = dto.getFeedTypes();
        List<String> feedTypes;
        if (rawFeedTypes == null || rawFeedTypes.isEmpty()) {
            String category = dto.getCategory() != null ? dto.getCategory() : FeedTypeEnum.SYSTEM.getCode();
            feedTypes = List.of(category);
        } else {
            feedTypes = rawFeedTypes;
        }

        // 4. 渲染标题
        String renderedTitle = templateService.renderTitle(template, dto.getParams());

        // 5. 保存消息主体
        Message message = Message.builder()
                .bizType(dto.getCategory())
                .title(renderedTitle)
                .sendType(sendType)
                .status(MessageStatusEnum.NORMAL)
                .createTime(LocalDateTime.now())
                .build();
        Message savedMessage = messageService.saveMessage(message);

        // 6. 建立消息与 Feed 的关联
        List<MessageFeedMapping> mappings = feedTypes.stream()
                .map(ft -> MessageFeedMapping.builder()
                        .messageId(savedMessage.getId())
                        .feedType(ft)
                        .build())
                .toList();
        feedMappingMapper.batchInsert(mappings);

        // 7. 根据发送类型处理
        if (isBroadcast) {
            handleBroadcastMessage(dto, message, feedTypes);
        } else {
            handleUserMessage(dto, message, template, renderedTitle, feedTypes);
        }

        log.info("上游消息处理完成: messageId={}, messageTableId={}", dto.getMessageId(), message.getId());
    }

    /**
     * 处理广播消息（读扩散）
     * 仅保存 message 和 mapping，不生成 user_message
     * 更新 Redis feed:max_cursor
     */
    private void handleBroadcastMessage(UpstreamMessageDTO dto, Message message,
                                        List<String> feedTypes) {
        for (String feedType : feedTypes) {
            String redisKey = MessageIdUtils.buildFeedMaxCursorKey(feedType);
            redisTemplate.opsForValue().set(redisKey, message.getId());
            log.debug("广播消息 Feed cursor 更新: feed={}, maxCursor={}", feedType, message.getId());
        }
        log.info("广播消息已处理（读扩散）: messageId={}, feeds={}", message.getId(), feedTypes);
    }

    /**
     * 处理用户消息（写扩散）
     * 为每个用户生成 user_message 记录
     * 同时兼容现有渠道发送流程
     */
    private void handleUserMessage(UpstreamMessageDTO dto, Message message,
                                   NotificationTemplate template, String renderedTitle,
                                   List<String> feedTypes) {
        List<Long> userIds = dto.getRecipients() != null && dto.getRecipients().getUserIds() != null
                ? dto.getRecipients().getUserIds()
                : List.of();

        if (userIds.isEmpty()) {
            log.warn("用户消息无收件人: messageId={}", dto.getMessageId());
            return;
        }

        String renderedContent = templateService.renderContent(template, dto.getParams());
        List<String> channels = dto.getChannels() != null
                ? dto.getChannels()
                : List.of("in_app");

        for (Long userId : userIds) {
            // 幂等校验（Redis 去重）
            String dedupKey = MessageIdUtils.buildDedupKey(dto.getMessageId(), userId);
            Boolean alreadySent = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1");
            if (Boolean.FALSE.equals(alreadySent)) {
                log.debug("重复消息跳过: messageId={}, userId={}", dto.getMessageId(), userId);
                continue;
            }
            redisTemplate.expire(dedupKey, java.time.Duration.ofDays(7));

            // 写入 user_message（UNREAD 状态），标记该用户有待读的 USER 消息
            String userMsgFeedType = feedTypes != null && !feedTypes.isEmpty() ? feedTypes.get(0) : FeedTypeEnum.SYSTEM.getCode();
            UserMessage userMsg = UserMessage.builder()
                    .userId(userId)
                    .messageId(message.getId())
                    .feedType(userMsgFeedType)
                    .bizType(dto.getCategory())
                    .sendType(1)  // USER
                    .status(ReadStatusEnum.UNREAD)
                    .createTime(LocalDateTime.now())
                    .build();
            try {
                userMessageMapper.insert(userMsg);
            } catch (Exception e) {
                log.warn("user_message 重复插入忽略: userId={}, messageId={}", userId, message.getId());
            }

            // 兼容现有渠道发送流程（写入 notification_message + 渠道发送）
            for (String channel : channels) {
                NotificationMessage msg = NotificationMessage.builder()
                        .messageId(dto.getMessageId())
                        .userId(userId)
                        .category(dto.getCategory() != null ? dto.getCategory() : FeedTypeEnum.SYSTEM.getCode())
                        .channel(channel)
                        .title(renderedTitle)
                        .content(renderedContent)
                        .isRead(false)
                        .isDeleted(false)
                        .sendStatus(SendStatusEnum.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationMessageMapper.insert(msg);

                ChannelSendContext context = ChannelSendContext.builder()
                        .originalMessage(dto)
                        .template(template)
                        .userIds(List.of(userId))
                        .channel(channel)
                        .renderedTitle(renderedTitle)
                        .renderedContent(renderedContent)
                        .retryCount(0)
                        .build();

                channelSendService.sendAsync(context, msg.getId());
            }
        }

        log.info("用户消息已处理（写扩散）: messageId={}, 用户数={}", message.getId(), userIds.size());
    }
}
