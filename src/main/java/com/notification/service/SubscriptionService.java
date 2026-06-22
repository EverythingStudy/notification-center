package com.notification.service;

import java.util.List;

/**
 * 用户订阅服务接口
 * 管理用户可以访问的 Feed
 */
public interface SubscriptionService {

    /**
     * 获取用户可访问的 Feed 列表
     */
    List<String> getUserFeedTypes(Long userId);

    /**
     * 为用户订阅一个 Feed
     */
    void subscribe(Long userId, String feedType);

    /**
     * 取消订阅
     */
    void unsubscribe(Long userId, String feedType);
}
