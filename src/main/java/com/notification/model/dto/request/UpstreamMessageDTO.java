package com.notification.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 上游消息 DTO
 * 支持广播消息（BROADCAST）和用户消息（USER）两种发送模式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamMessageDTO {

    @NotBlank
    private String messageId;

    @NotBlank
    private String appId;

    @NotBlank
    private String templateCode;

    /**
     * 发送类型: BROADCAST / USER
     * BROADCAST: 广播消息，不生成 user_message（读扩散）
     * USER: 用户消息，写入 user_message（写扩散）
     */
    private String sendType;

    /**
     * Feed 类型列表: system, vip, marketing, order, logistics, risk
     * 消息属于哪些消息流
     */
    private List<String> feedTypes;

    private Recipients recipients;
    private List<String> channels;
    private Map<String, String> params;
    private String category;
    private String priority;
    private String expireAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recipients {
        private List<Long> userIds;
        private List<String> wechatOpenIds;
        private List<String> emails;
        private List<String> phones;
    }
}
