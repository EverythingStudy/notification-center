package com.notification.mapper;

import com.notification.model.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户订阅关系 Mapper
 */
@Mapper
public interface SubscriptionMapper {

    int insert(Subscription subscription);

    int delete(@Param("userId") Long userId, @Param("feedType") String feedType);

    List<Subscription> findByUserId(@Param("userId") Long userId);

    List<String> findFeedTypesByUserId(@Param("userId") Long userId);
}
