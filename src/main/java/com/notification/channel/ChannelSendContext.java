package com.notification.channel;

import lombok.Builder;
import lombok.Data;

import com.notification.model.dto.request.UpstreamMessageDTO;
import com.notification.model.entity.NotificationTemplate;

import java.util.List;

@Data
@Builder
public class ChannelSendContext {
    private UpstreamMessageDTO originalMessage;
    private NotificationTemplate template;
    private List<Long> userIds;
    private String channel;
    private String renderedTitle;
    private String renderedContent;
}