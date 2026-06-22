package com.notification.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息返回 VO（统一消息格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {
    private Long messageId;          // 消息 ID
    private String title;            // 标题
    private String contentUrl;       // 内容地址
    private String bizType;          // 业务类型
    private String feedType;         // 所属 Feed
    private String sendType;         // BROADCAST / USER
    private boolean isRead;          // 是否已读
    private LocalDateTime createTime;// 创建时间
}
