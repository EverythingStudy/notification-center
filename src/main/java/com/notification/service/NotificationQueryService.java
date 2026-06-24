package com.notification.service;

import com.notification.model.dto.response.FeedUnreadVO;
import com.notification.model.dto.response.MessageVO;
import com.notification.model.dto.response.PageResponse;

import java.util.List;

/**
 * 消息查询服务接口（消息中心模式）
 * Cursor 作为"全部已读"水位，user_message 记录逐条已读
 */
public interface NotificationQueryService {

    /**
     * 获取用户各 Feed 未读数
     * unread = feed 中 cursor 以上且未被逐条已读的消息数
     */
    List<FeedUnreadVO> getFeedUnread(Long userId);

    /**
     * 查询某个 Feed 中的未读消息列表（逐条已读的已排除）
     * after 用于游标分页（上一页最后一条的 messageId），null 则从 user_cursor 开始
     */
    List<MessageVO> getFeedMessages(Long userId, String feedType, Long after, int size);

    /**
     * 逐条标记消息为已读
     * 写入 user_message，不移动 cursor
     */
    void markAsRead(Long userId, Long messageId, String feedType);

    /**
     * 将某个 Feed 全部标为已读
     * 推进 cursor + 清理低于 cursor 的已读记录
     */
    void markAllRead(Long userId, String feedType);

    /**
     * 分页查询用户的消息列表（旧接口，兼容保留）
     * 合并广播消息 + 用户消息，按时间降序
     */
    PageResponse<MessageVO> pageMessages(Long userId, int page, int size);
}
