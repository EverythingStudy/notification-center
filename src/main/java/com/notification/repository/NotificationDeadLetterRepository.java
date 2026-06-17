package com.notification.repository;

import com.notification.model.entity.NotificationDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationDeadLetterRepository extends JpaRepository<NotificationDeadLetter, Long> {
    List<NotificationDeadLetter> findByIsResolvedFalseAndFailedAtBefore(LocalDateTime before);
    List<NotificationDeadLetter> findByMessageId(String messageId);
}