package com.notification.service.impl;

import com.notification.mapper.SubscriptionMapper;
import com.notification.model.entity.Subscription;
import com.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户订阅服务实现
 * 控制用户可访问的 Feed 范围
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    public List<String> getUserFeedTypes(Long userId) {
        return subscriptionMapper.findFeedTypesByUserId(userId);
    }

    @Override
    public void subscribe(Long userId, String feedType) {
        Subscription sub = Subscription.builder()
                .userId(userId)
                .feedType(feedType)
                .build();
        subscriptionMapper.insert(sub);
        log.info("用户订阅 Feed: userId={}, feedType={}", userId, feedType);
    }

    @Override
    public void unsubscribe(Long userId, String feedType) {
        subscriptionMapper.delete(userId, feedType);
        log.info("用户取消订阅: userId={}, feedType={}", userId, feedType);
    }
}
