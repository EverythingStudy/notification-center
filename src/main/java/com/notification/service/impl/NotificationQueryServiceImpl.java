package com.notification.service.impl;

import com.notification.mapper.UserMessageMapper;
import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.model.entity.Message;
import com.notification.model.entity.UserMessage;
import com.notification.model.enums.FeedTypeEnum;
import com.notification.model.enums.ReadStatusEnum;
import com.notification.service.CursorService;
import com.notification.service.MessageService;
import com.notification.service.NotificationQueryService;
import com.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息查询服务实现（消息中心模式）
 * Cursor = "全部已读"水位（仅全部标已读时推进）
 * user_message = 逐条消息的已读状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final SubscriptionService subscriptionService;
    private final CursorService cursorService;
    private final MessageService messageService;
    private final UserMessageMapper userMessageMapper;

    @Override
    public List<FeedUnreadVO> getFeedUnread(Long userId) {
        // 1. 获取用户可访问的 Feed
        List<String> feedTypes = subscriptionService.getUserFeedTypes(userId);
        if (feedTypes.isEmpty()) {
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        // 2. 计算各 Feed 未读数（已排除逐条已读的消息）
        Map<String, Long> unreadCounts = cursorService.getUnreadCounts(userId, feedTypes);

        // 3. 组装 VO
        List<FeedUnreadVO> result = new ArrayList<>();
        for (String ft : feedTypes) {
            FeedTypeEnum feedEnum = FeedTypeEnum.fromCode(ft);
            result.add(FeedUnreadVO.builder()
                    .feedType(ft)
                    .feedName(feedEnum.getDisplayName())
                    .unreadCount(unreadCounts.getOrDefault(ft, 0L))
                    .build());
        }
        return result;
    }

    @Override
    public List<MessageVO> getFeedMessages(Long userId, String feedType, Long after, int size) {
        // 未传 after 时，使用 user_cursor 作为水位
        long cursor = after != null ? after : cursorService.getCursor(userId, feedType);
        List<Message> messages = messageService.findUnreadMessagesByFeed(userId, feedType, cursor, size);

        return messages.stream()
                .map(msg -> MessageVO.builder()
                        .messageId(msg.getId())
                        .title(msg.getTitle())
                        .contentUrl(msg.getContentUrl())
                        .bizType(msg.getBizType())
                        .feedType(feedType)
                        .sendType(msg.getSendType() != null ? msg.getSendType().name() : "BROADCAST")
                        .isRead(false)
                        .createTime(msg.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long messageId, String feedType) {
        // INSERT IGNORE + UNIQUE(user_id, message_id) 保证幂等
        userMessageMapper.insertRead(userId, messageId, feedType, LocalDateTime.now());
        log.debug("逐条标为已读: userId={}, messageId={}, feedType={}", userId, messageId, feedType);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId, String feedType) {
        // 1. 获取 feed 当前最大消息 ID 作为新 cursor
        Long maxCursor = messageService.getFeedMaxCursor(feedType);
        if (maxCursor == null || maxCursor == 0L) {
            log.debug("Feed 无消息，跳过: feedType={}", feedType);
            return;
        }

        // 2. 推进 cursor（GREATEST 保证单调递增）
        cursorService.updateCursor(userId, feedType, maxCursor);
        log.info("全部标为已读: userId={}, feedType={}, cursor={}", userId, feedType, maxCursor);
    }

    @Override
    public PageResponse<MessageVO> pageMessages(Long userId, int page, int size) {
        List<String> feedTypes = subscriptionService.getUserFeedTypes(userId);
        if (feedTypes.isEmpty()) {
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        Map<String, Long> cursors = cursorService.getCursors(userId, feedTypes);
        long minCursor = cursors.values().stream().min(Long::compareTo).orElse(0L);

        // 查广播消息（使用新查询，排除已逐条已读的）
        List<Message> broadcastMessages = new ArrayList<>();
        for (String ft : feedTypes) {
            broadcastMessages.addAll(
                    messageService.findUnreadMessagesByFeed(userId, ft, minCursor, size));
        }

        // 查用户消息（旧兼容）
        int offset = page * size;
        List<UserMessage> userMessages = userMessageMapper.findByUserId(userId, offset, size);

        List<MessageVO> items = new ArrayList<>();

        for (Message msg : broadcastMessages) {
            items.add(MessageVO.builder()
                    .messageId(msg.getId())
                    .title(msg.getTitle())
                    .contentUrl(msg.getContentUrl())
                    .bizType(msg.getBizType())
                    .feedType("")
                    .sendType("BROADCAST")
                    .isRead(false)
                    .createTime(msg.getCreateTime())
                    .build());
        }

        for (UserMessage um : userMessages) {
            messageService.findById(um.getMessageId()).ifPresent(msg -> {
                items.add(MessageVO.builder()
                        .messageId(msg.getId())
                        .title(msg.getTitle())
                        .contentUrl(msg.getContentUrl())
                        .bizType(msg.getBizType())
                        .feedType("")
                        .sendType("USER")
                        .isRead(um.getStatus() == ReadStatusEnum.READ)
                        .createTime(um.getCreateTime())
                        .build());
            });
        }

        items.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
        return new PageResponse<>(items, (long) items.size(), page + 1, size);
    }
}
