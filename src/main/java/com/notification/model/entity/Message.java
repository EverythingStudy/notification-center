package com.notification.model.entity;

import com.notification.model.enums.MessageSendTypeEnum;
import com.notification.model.enums.MessageStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息主体实体
 * 对应 message 表，广播消息和用户消息都先写入此表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private Long id;
    private String bizType;
    private String title;
    private String contentUrl;
    private MessageSendTypeEnum sendType;
    private MessageStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
}
