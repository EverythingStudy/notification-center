package com.notification.service.impl;

import com.notification.model.dto.response.CategoryUnreadVO;
import com.notification.model.dto.response.PageResponse;
import com.notification.model.entity.NotificationMessage;
import com.notification.repository.NotificationMessageRepository;
import com.notification.service.NotificationQueryService;
import com.notification.util.MessageIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationMessageRepository messageRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<CategoryUnreadVO> getCategoryUnread(Long userId) {
        List<CategoryUnreadVO> result = new ArrayList<>();
        for (com.notification.model.enums.NotificationCategoryEnum category
                : com.notification.model.enums.NotificationCategoryEnum.values()) {
            String cacheKey = MessageIdUtils.buildUnreadCategoryKey(userId, category.getCode());
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            long unreadCount;
            if (cached instanceof Number num) {
                unreadCount = num.longValue();
            } else {
                unreadCount = messageRepository.countByUserIdAndCategoryAndIsReadFalseAndIsDeletedFalse(
                        userId, category.getCode());
            }
            result.add(CategoryUnreadVO.builder()
                    .category(category.getCode())
                    .categoryName(category.getDisplayName())
                    .unreadCount(unreadCount)
                    .build());
        }
        return result;
    }

    @Override
    public PageResponse<NotificationMessage> pageMessages(Long userId, String category, Pageable pageable) {
        Page<NotificationMessage> page;
        if (category != null && !category.isEmpty()) {
            page = messageRepository.findByUserIdAndCategoryAndIsDeletedFalseOrderByCreatedAtDesc(
                    userId, category, pageable);
        } else {
            page = messageRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable);
        }
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    @Override
    @Transactional
    public void markAsRead(Long id, Long userId) {
        Optional<NotificationMessage> msg = messageRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId);
        if (msg.isPresent() && !msg.get().getIsRead()) {
            msg.get().setIsRead(true);
            msg.get().setReadAt(LocalDateTime.now());
            messageRepository.save(msg.get());
            String cacheKey = MessageIdUtils.buildUnreadCategoryKey(userId, msg.get().getCategory());
            redisTemplate.opsForValue().decrement(cacheKey);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId, String category) {
        int updated = messageRepository.markAllAsRead(userId, category);
        if (updated > 0) {
            String cacheKey = MessageIdUtils.buildUnreadCategoryKey(userId, category);
            redisTemplate.opsForValue().set(cacheKey, 0);
        }
    }

    @Override
    @Transactional
    public void deleteMessage(Long id, Long userId) {
        Optional<NotificationMessage> msg = messageRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId);
        msg.ifPresent(m -> {
            m.setIsDeleted(true);
            messageRepository.save(m);
        });
    }
}