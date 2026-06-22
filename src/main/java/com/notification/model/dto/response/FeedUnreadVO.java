package com.notification.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feed 未读数 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedUnreadVO {
    private String feedType;      // Feed 类型
    private String feedName;      // Feed 名称
    private long unreadCount;     // 未读数
}
