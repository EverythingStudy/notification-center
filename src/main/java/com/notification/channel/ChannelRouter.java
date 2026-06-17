package com.notification.channel;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRouter {

    private final List<NotificationChannel> channelList;
    private final Map<String, NotificationChannel> channelMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (NotificationChannel channel : channelList) {
            channelMap.put(channel.channelName(), channel);
            log.info("Registered channel: {}", channel.channelName());
        }
    }

    public List<NotificationChannel> route(ChannelSendContext context) {
        return channelList.stream()
                .filter(c -> c.supports(context))
                .toList();
    }

    public NotificationChannel getChannel(String name) {
        return channelMap.get(name);
    }
}