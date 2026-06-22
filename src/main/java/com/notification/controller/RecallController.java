package com.notification.controller;

import com.notification.model.dto.request.RecallRequest;
import com.notification.model.dto.response.ApiResponse;
import com.notification.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 消息撤回 API
 * 采用逻辑删除，将 message 状态置为 RECALL
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recall")
@RequiredArgsConstructor
public class RecallController {

    private final MessageService messageService;

    /**
     * 撤回消息
     * 将 message 表状态更新为 RECALL，并通过 Kafka 通知在线客户端
     */
    @PostMapping
    public ApiResponse<Void> recallMessage(@Valid @RequestBody RecallRequest request) {
        messageService.recallMessage(request.getMessageId());
        log.info("消息已撤回: messageId={}, reason={}", request.getMessageId(), request.getReason());
        return ApiResponse.success(null);
    }
}
