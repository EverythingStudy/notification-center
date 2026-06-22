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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息查询服务实现（消息中心模式）
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
            // 默认所有用户可访问 SYSTEM
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        // 2. 计算各 Feed 未读数
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
    public PageResponse<MessageVO> pageMessages(Long userId, int page, int size) {
        // 1. 获取用户可访问的 Feed
        List<String> feedTypes = subscriptionService.getUserFeedTypes(userId);
        if (feedTypes.isEmpty()) {
            feedTypes = List.of(FeedTypeEnum.SYSTEM.getCode());
        }

        // 2. 获取用户当前 cursor（取所有 Feed 中最小 cursor 作为起始点）
        Map<String, Long> cursors = cursorService.getCursors(userId, feedTypes);
        long minCursor = cursors.values().stream().min(Long::compareTo).orElse(0L);

        // 3. 查询广播消息（读扩散）
        List<Message> broadcastMessages = messageService.findBroadcastMessagesByFeeds(
                feedTypes, minCursor, size);

        // 4. 查询用户消息（写扩散）
        int offset = page * size;
        List<UserMessage> userMessages = userMessageMapper.findByUserId(userId, offset, size);

        // 5. 合并结果
        List<MessageVO> items = new ArrayList<>();

        for (Message msg : broadcastMessages) {
            items.add(MessageVO.builder()
                    .messageId(msg.getId())
                    .title(msg.getTitle())
                    .contentUrl(msg.getContentUrl())
                    .bizType(msg.getBizType())
                    .feedType("")
                    .sendType("BROADCAST")
                    .isRead(true)
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

        // 按时间降序排列
        items.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        return new PageResponse<>(items, (long) items.size(), page + 1, size);
    }

    @Override
    public void updateCursor(Long userId, String feedType, Long cursor) {
        cursorService.updateCursor(userId, feedType, cursor);
        log.info("用户更新 Cursor: userId={}, feed={}, cursor={}", userId, feedType, cursor);
    }
}
