package com.notification.service;

import com.notification.model.dto.request.UpstreamMessageDTO;

/**
 * 上游消息服务接口
 * 支持广播消息（读扩散）和用户消息（写扩散）两种模式
 */
public interface UpstreamMessageService {

    /**
     * 处理上游消息
     * 根据 sendType 自动选择广播或用户消息流程
     */
    void processMessage(UpstreamMessageDTO message);
}
