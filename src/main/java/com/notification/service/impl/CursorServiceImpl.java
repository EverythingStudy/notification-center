package com.notification.service.impl;

import com.notification.mapper.UserFeedCursorMapper;
import com.notification.model.entity.UserFeedCursor;
import com.notification.service.CursorService;
import com.notification.service.MessageService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cursor 服务实现
 * Redis 为主，MySQL 为备份
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CursorServiceImpl implements CursorService {

    private final UserFeedCursorMapper cursorMapper;
    private final MessageService messageService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Long getCursor(Long userId, String feedType) {
        // 1. 优先读 Redis Hash
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);
        Object cached = redisTemplate.opsForHash().get(redisKey, feedType);
        if (cached instanceof Number num) {
            return num.longValue();
        }
        // 2. Redis 没有则查 MySQL
        Optional<UserFeedCursor> cursorOpt = cursorMapper.findByUserIdAndFeedType(userId, feedType);
        Long cursor = cursorOpt.map(UserFeedCursor::getCursor).orElse(0L);
        // 3. 回写 Redis
        redisTemplate.opsForHash().put(redisKey, feedType, cursor);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        return cursor;
    }

    @Override
    public Map<String, Long> getCursors(Long userId, List<String> feedTypes) {
        Map<String, Long> result = new HashMap<>();
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);

        // 批量从 Redis Hash 获取
        List<Object> multiGet = redisTemplate.opsForHash().multiGet(redisKey,
                feedTypes.stream().map(ft -> (Object) ft).toList());
        for (int i = 0; i < feedTypes.size(); i++) {
            Object val = multiGet.get(i);
            if (val instanceof Number num) {
                result.put(feedTypes.get(i), num.longValue());
            }
        }

        // 缺失的从 MySQL 补
        List<String> missed = feedTypes.stream().filter(ft -> !result.containsKey(ft)).toList();
        if (!missed.isEmpty()) {
            List<UserFeedCursor> dbCursors = cursorMapper.findByUserIdAndFeedTypes(userId, missed);
            for (UserFeedCursor c : dbCursors) {
                result.put(c.getFeedType(), c.getCursor());
                redisTemplate.opsForHash().put(redisKey, c.getFeedType(), c.getCursor());
            }
        }

        // 仍未获取到的 feed 默认 cursor=0
        for (String ft : feedTypes) {
            result.putIfAbsent(ft, 0L);
        }

        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
        return result;
    }

    @Override
    @Transactional
    public void updateCursor(Long userId, String feedType, Long newCursor) {
        // 1. 更新 MySQL（GREATEST 保证单调递增）
        int updated = cursorMapper.updateCursor(userId, feedType, newCursor);
        if (updated == 0) {
            // 首次插入
            Optional<UserFeedCursor> existing = cursorMapper.findByUserIdAndFeedType(userId, feedType);
            if (existing.isEmpty()) {
                UserFeedCursor cursor = UserFeedCursor.builder()
                        .userId(userId)
                        .feedType(feedType)
                        .cursor(newCursor)
                        .build();
                cursorMapper.insert(cursor);
            }
        }

        // 2. 更新 Redis（先获取旧值，取 max）
        String redisKey = MessageIdUtils.buildUserCursorKey(userId);
        Object oldVal = redisTemplate.opsForHash().get(redisKey, feedType);
        long oldCursor = oldVal instanceof Number num ? num.longValue() : 0L;
        long finalCursor = Math.max(oldCursor, newCursor);
        redisTemplate.opsForHash().put(redisKey, feedType, finalCursor);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        log.debug("Cursor 已更新: userId={}, feed={}, cursor={}", userId, feedType, finalCursor);
    }

    @Override
    public long getUnreadCount(Long userId, String feedType) {
        long feedMax = messageService.getFeedMaxCursor(feedType);
        long userCursor = getCursor(userId, feedType);
        return Math.max(0, feedMax - userCursor);
    }

    @Override
    public long getTotalUnreadCount(Long userId, List<String> feedTypes) {
        return feedTypes.stream()
                .mapToLong(ft -> getUnreadCount(userId, ft))
                .sum();
    }

    @Override
    public Map<String, Long> getUnreadCounts(Long userId, List<String> feedTypes) {
        Map<String, Long> result = new HashMap<>();
        Map<String, Long> userCursors = getCursors(userId, feedTypes);

        for (String ft : feedTypes) {
            long feedMax = messageService.getFeedMaxCursor(ft);
            long userCursor = userCursors.getOrDefault(ft, 0L);
            result.put(ft, Math.max(0, feedMax - userCursor));
        }

        return result;
    }
}
