package com.notification.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.entity.NotificationTemplate;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSendContext {
    private UpstreamMessageDTO originalMessage;
    private NotificationTemplate template;
    private List<Long> userIds;
    private String channel;
    private String renderedTitle;
    private String renderedContent;
    private int retryCount;
}
