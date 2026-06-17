package com.notification.service;

import com.notification.model.dto.request.UpstreamMessageDTO;

public interface UpstreamMessageService {
    void processMessage(UpstreamMessageDTO message);
}