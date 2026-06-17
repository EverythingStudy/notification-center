package com.notification.channel;

import com.notification.channel.ChannelSendContext;
import com.notification.model.enums.ChannelTypeEnum;

public interface NotificationChannel {
    String channelName();
    ChannelTypeEnum channelType();
    void send(ChannelSendContext context);
    boolean supports(ChannelSendContext context);
}