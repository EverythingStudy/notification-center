package com.notification.repository;

import com.notification.model.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);
    boolean existsByTemplateCode(String templateCode);
}