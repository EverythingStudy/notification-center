package com.notification.mapper;

import com.notification.model.entity.NotificationTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface NotificationTemplateMapper {

    int insert(NotificationTemplate template);

    int updateById(NotificationTemplate template);

    Optional<NotificationTemplate> findById(@Param("id") Long id);

    Optional<NotificationTemplate> findByTemplateCode(@Param("templateCode") String templateCode);

    boolean existsByTemplateCode(@Param("templateCode") String templateCode);
}
