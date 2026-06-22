package com.notification.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 消息撤回请求 DTO
 */
@Data
public class RecallRequest {
    @NotNull
    private Long messageId;  // 消息 ID
    private String reason;   // 撤回原因
}
