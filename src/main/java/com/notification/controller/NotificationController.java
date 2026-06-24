package com.notification.controller;

import com.notification.model.dto.response.ApiResponse;
import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.service.NotificationQueryService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息中心 REST API
 * Feed + Cursor 架构，支持逐条已读和"全部标已读"
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
     * 查询某个 Feed 中的未读消息列表（按消息 ID 升序）
     * 服务端自动使用 user_cursor 作为水位，返回大于 cursor 且未逐条已读的消息
     * after 用于游标分页（上一页最后一条消息的 messageId），不传则从 user_cursor 开始
     */
    @GetMapping("/feed/{feedType}")
    public ApiResponse<List<MessageVO>> getFeedMessages(
            @RequestParam Long userId,
            @PathVariable String feedType,
            @RequestParam(required = false) @Min(0) Long after,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        List<MessageVO> result = notificationQueryService.getFeedMessages(userId, feedType, after, size);
        return ApiResponse.success(result);
    }

    /**
     * 逐条标记消息为已读
     * 写入 user_message（UNIQUE 保证幂等），不移动 cursor
     */
    @PostMapping("/read")
    public ApiResponse<Void> markAsRead(
            @RequestParam Long userId,
            @RequestParam Long messageId,
            @RequestParam String feedType) {
        notificationQueryService.markAsRead(userId, messageId, feedType);
        return ApiResponse.success(null);
    }

    /**
     * 将某个 Feed 全部标为已读
     * 推进 cursor 到当前 feed 最大消息 ID，同时清理低于光标已读记录
     */
    @PostMapping("/cursor")
    public ApiResponse<Void> markAllRead(
            @RequestParam Long userId,
            @RequestParam String feedType) {
        notificationQueryService.markAllRead(userId, feedType);
        return ApiResponse.success(null);
    }

    /**
     * 分页查询用户消息列表（旧接口，兼容保留）
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
}
