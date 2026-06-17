package com.notification.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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