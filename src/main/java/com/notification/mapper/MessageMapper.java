package com.notification.mapper;

import com.notification.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体 Mapper
 */
@Mapper
public interface MessageMapper {

    int insert(Message message);

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    Optional<Message> findById(@Param("id") Long id);

    /**
     * 根据 Feed 类型和 Cursor 查询广播消息（读扩散）
     */
    List<Message> findBroadcastByFeedAndCursor(
            @Param("feedType") String feedType,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    /**
     * 查询多个 Feed 中 cursor 之后的消息
     */
    List<Message> findBroadcastByFeedsAndCursor(
            @Param("feedTypes") List<String> feedTypes,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    /**
     * 获取 Feed 最大消息 ID（用于 max_cursor）
     */
    Long findMaxIdByFeedType(@Param("feedType") String feedType);

    /**
     * 统计某个 Feed 中 cursor 之后的未读消息数
     * 直接 COUNT 而非 max-cursor，避免全局自增 ID 带来的不准确
     */
    long countByFeedTypeAndCursor(
            @Param("feedType") String feedType,
            @Param("cursor") Long cursor);
}
