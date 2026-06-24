package com.notification.service;

import com.notification.model.entity.Message;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体服务接口
 */
public interface MessageService {

    /**
     * 保存消息并返回完整消息（含自增 ID）
     */
    Message saveMessage(Message message);

    /**
     * 撤回消息（逻辑删除）
     */
    void recallMessage(Long messageId);

    /**
     * 根据 ID 查询消息
     */
    Optional<Message> findById(Long messageId);

    /**
     * 查询用户在指定 Feed 中的未读广播消息（读扩散）
     * 根据 cursor 过滤出用户未读的消息
     */
    List<Message> findBroadcastMessages(String feedType, Long cursor, int limit);

    /**
     * 查询用户在多个 Feed 中的未读广播消息
     * 取所有 Feed 中最小 cursor 之后的消息
     */
    List<Message> findBroadcastMessagesByFeeds(List<String> feedTypes, Long cursor, int limit);

    /**
     * 查询用户在某个 Feed 中 cursor 之后的未读消息列表
     * 排除用户已逐条标记为已读的消息
     */
    List<Message> findUnreadMessagesByFeed(Long userId, String feedType, Long cursor, int limit);

    /**
     * 获取 Feed 当前最大消息 ID
     */
    Long getFeedMaxCursor(String feedType);

    /**
     * 统计某个 Feed 中所有正常消息数（不含已撤回/已过期）
     */
    long countByFeedType(String feedType);
}
