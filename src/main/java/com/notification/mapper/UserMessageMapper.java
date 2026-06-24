package com.notification.mapper;

import com.notification.model.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户消息 Mapper — 仅记录已读状态
 */
@Mapper
public interface UserMessageMapper {

    int insert(UserMessage userMessage);

    int updateStatus(@Param("userId") Long userId,
                     @Param("messageId") Long messageId,
                     @Param("status") int status);

    /**
     * 查询用户的未读消息列表（分页）
     */
    List<UserMessage> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") int status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询用户的消息列表（分页，不分已读未读）
     */
    List<UserMessage> findByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") int status);

    /**
     * 插入已读记录（逐条已读时调用）
     * UNIQUE(user_id, message_id) 保证幂等
     */
    int insertRead(@Param("userId") Long userId,
                   @Param("messageId") Long messageId,
                   @Param("feedType") String feedType,
                   @Param("readTime") LocalDateTime readTime);

    /**
     * 查询用户在某个 feed 中已逐条标记为已读的 messageId 列表
     */
    List<Long> findReadMessageIdsByFeed(@Param("userId") Long userId,
                                        @Param("feedType") String feedType);

    /**
     * 统计用户在某个 feed 中已逐条标记为已读的广播消息数
     */
    long countReadByUserAndFeed(@Param("userId") Long userId,
                                @Param("feedType") String feedType);

    /**
     * 统计用户在某个 feed 中未读的 USER 类型消息数
     */
    long countUnreadUserByFeed(@Param("userId") Long userId,
                                @Param("feedType") String feedType);
}
