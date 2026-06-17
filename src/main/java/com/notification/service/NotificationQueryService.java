package com.notification.service;

import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.dto.response.CategoryUnreadVO;
import com.notification.model.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationQueryService {
    List<CategoryUnreadVO> getCategoryUnread(Long userId);
    PageResponse<?> pageMessages(Long userId, String category, Pageable pageable);
    void markAsRead(Long id, Long userId);
    void markAllAsRead(Long userId, String category);
    void deleteMessage(Long id, Long userId);
}