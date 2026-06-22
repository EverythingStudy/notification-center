package com.notification.mapper;

import com.notification.model.entity.NotificationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface NotificationMessageMapper {

    int insert(NotificationMessage message);

    int updateById(NotificationMessage message);

    Optional<NotificationMessage> findById(@Param("id") Long id);

    List<NotificationMessage> findByUserIdAndCategoryAndIsDeletedFalseOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("offset") int offset,
            @Param("size") int size);

    List<NotificationMessage> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("size") int size);

    Optional<NotificationMessage> findByIdAndUserIdAndIsDeletedFalse(
            @Param("id") Long id,
            @Param("userId") Long userId);

    int markAllAsRead(@Param("userId") Long userId, @Param("category") String category);

    long countByUserIdAndCategoryAndIsReadFalseAndIsDeletedFalse(
            @Param("userId") Long userId,
            @Param("category") String category);

    long countByUserIdAndIsReadFalseAndIsDeletedFalse(@Param("userId") Long userId);
}
