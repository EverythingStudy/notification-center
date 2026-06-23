package com.notification.service.impl;

import com.notification.mapper.MessageMapper;
import com.notification.model.entity.Message;
import com.notification.model.enums.MessageStatusEnum;
import com.notification.service.MessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 消息主体服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public Message saveMessage(Message message) {
        messageMapper.insert(message);
        log.debug("消息已保存: id={}, title={}, sendType={}", message.getId(), message.getTitle(), message.getSendType());
        return message;
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId) {
        messageMapper.updateStatus(messageId, MessageStatusEnum.RECALL.getCode());
        log.info("消息已撤回: messageId={}", messageId);
    }

    @Override
    public Optional<Message> findById(Long messageId) {
        return messageMapper.findById(messageId);
    }

    @Override
    public List<Message> findBroadcastMessages(String feedType, Long cursor, int limit) {
        return messageMapper.findBroadcastByFeedAndCursor(feedType, cursor, limit);
    }

    @Override
    public List<Message> findBroadcastMessagesByFeeds(List<String> feedTypes, Long cursor, int limit) {
        return messageMapper.findBroadcastByFeedsAndCursor(feedTypes, cursor, limit);
    }

    @Override
    public Long getFeedMaxCursor(String feedType) {
        // 优先从 Redis 获取
        String redisKey = MessageIdUtils.buildFeedMaxCursorKey(feedType);
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof Number num) {
            return num.longValue();
        }
        // Redis 没有则查 DB 并回写 Redis
        Long maxId = messageMapper.findMaxIdByFeedType(feedType);
        if (maxId != null) {
            redisTemplate.opsForValue().set(redisKey, maxId);
        }
        return maxId != null ? maxId : 0L;
    }

    @Override
    public long countUnreadByFeed(String feedType, Long cursor) {
        return messageMapper.countByFeedTypeAndCursor(feedType, cursor);
    }
}
