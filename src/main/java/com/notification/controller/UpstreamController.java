package com.notification.controller;

import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.dto.response.ApiResponse;
import com.notification.service.UpstreamMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for upstream services to submit notification messages.
 * Supports both single-message and batch submission.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upstream")
@RequiredArgsConstructor
public class UpstreamController {

    private final UpstreamMessageService upstreamMessageService;

    @PostMapping("/message")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<String> sendMessage(@Valid @RequestBody UpstreamMessageDTO message) {
        log.info("Received upstream message via REST: messageId={}, templateCode={}",
                message.getMessageId(), message.getTemplateCode());
        upstreamMessageService.processMessage(message);
        return ApiResponse.success(message.getMessageId());
    }

    @PostMapping("/messages/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Integer> sendBatch(@Valid @RequestBody List<UpstreamMessageDTO> messages) {
        log.info("Received upstream batch via REST: count={}", messages.size());
        for (UpstreamMessageDTO message : messages) {
            upstreamMessageService.processMessage(message);
        }
        return ApiResponse.success(messages.size());
    }
}