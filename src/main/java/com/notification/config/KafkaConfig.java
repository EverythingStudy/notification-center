package com.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.upstream-raw}")
    private String upstreamRawTopic;

    @Value("${kafka.topics.channel-send}")
    private String channelSendTopic;

    @Value("${kafka.topics.channel-retry}")
    private String channelRetryTopic;

    @Value("${kafka.topics.channel-dead-letter}")
    private String channelDeadLetterTopic;

    @Bean
    public NewTopic upstreamRawTopic() {
        return TopicBuilder.name(upstreamRawTopic)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic channelSendTopic() {
        return TopicBuilder.name(channelSendTopic)
                .partitions(8)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic channelRetryTopic() {
        return TopicBuilder.name(channelRetryTopic)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic channelDeadLetterTopic() {
        return TopicBuilder.name(channelDeadLetterTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }
}