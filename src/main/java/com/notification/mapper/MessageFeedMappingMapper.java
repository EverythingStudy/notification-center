package com.notification.mapper;

import com.notification.model.entity.MessageFeedMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息-Feed 映射 Mapper
 */
@Mapper
public interface MessageFeedMappingMapper {

    int insert(MessageFeedMapping mapping);

    int batchInsert(@Param("list") List<MessageFeedMapping> list);

    List<MessageFeedMapping> findByMessageId(@Param("messageId") Long messageId);

    List<String> findFeedTypesByMessageId(@Param("messageId") Long messageId);
}
