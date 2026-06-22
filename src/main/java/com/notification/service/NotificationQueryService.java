package com.notification.service;

import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;

import java.util.List;

/**
 * 消息查询服务接口（消息中心模式）
 * 基于 Feed + Cursor 架构
 */
public interface NotificationQueryService {

    /**
     * 获取用户各 Feed 未读数
     */
    List<FeedUnreadVO> getFeedUnread(Long userId);

    /**
     * 分页查询用户的消息列表
     * 合并广播消息（读扩散）+ 用户消息（写扩散）
     */
    PageResponse<MessageVO> pageMessages(Long userId, int page, int size);

    /**
     * 更新用户在某个 Feed 中的游标（标记为已读）
     */
    void updateCursor(Long userId, String feedType, Long cursor);
}
