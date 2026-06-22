package com.notification.controller;

import com.notification.model.dto.response.ApiResponse;
import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息中心 REST API
 * 基于 Feed + Cursor 架构的用户消息查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    /**
     * 获取用户各 Feed 的未读数
     */
    @GetMapping("/unread")
    public ApiResponse<List<FeedUnreadVO>> getFeedUnread(
            @RequestParam Long userId) {
        List<FeedUnreadVO> result = notificationQueryService.getFeedUnread(userId);
        return ApiResponse.success(result);
    }

    /**
     * 分页查询用户消息列表
     * 合并广播消息和用户消息，按时间降序
     */
    @GetMapping
    public ApiResponse<PageResponse<MessageVO>> pageMessages(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<MessageVO> result = notificationQueryService.pageMessages(userId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 更新用户在某个 Feed 中的已读位置
     * cursor = max(oldCursor, newCursor)，永不回退
     */
    @PostMapping("/cursor")
    public ApiResponse<Void> updateCursor(
            @RequestParam Long userId,
            @RequestParam String feedType,
            @RequestParam Long cursor) {
        notificationQueryService.updateCursor(userId, feedType, cursor);
        return ApiResponse.success(null);
    }
}
