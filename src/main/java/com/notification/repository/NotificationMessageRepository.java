package com.notification.repository;

import com.notification.model.entity.NotificationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {

    Page<NotificationMessage> findByUserIdAndCategoryAndIsDeletedFalseOrderByCreatedAtDesc(
            Long userId, String category, Pageable pageable);

    Page<NotificationMessage> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long userId, Pageable pageable);

    Optional<NotificationMessage> findByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);

    @Modifying
    @Query("UPDATE NotificationMessage n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.userId = :userId AND n.category = :category AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("category") String category);

    long countByUserIdAndCategoryAndIsReadFalseAndIsDeletedFalse(Long userId, String category);

    long countByUserIdAndIsReadFalseAndIsDeletedFalse(Long userId);
}