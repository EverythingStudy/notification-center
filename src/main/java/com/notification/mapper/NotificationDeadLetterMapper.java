package com.notification.mapper;

import com.notification.model.entity.NotificationDeadLetter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface NotificationDeadLetterMapper {

    int insert(NotificationDeadLetter deadLetter);

    Optional<NotificationDeadLetter> findById(@Param("id") Long id);

    List<NotificationDeadLetter> findByIsResolvedFalseAndFailedAtBefore(@Param("before") LocalDateTime before);

    List<NotificationDeadLetter> findByMessageId(@Param("messageId") String messageId);
}
