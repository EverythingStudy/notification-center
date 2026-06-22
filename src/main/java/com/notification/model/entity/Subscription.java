package com.notification.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户订阅关系实体
 * 控制用户可以访问哪些 Feed
 * 如普通用户只能访问 SYSTEM，VIP 用户可访问 SYSTEM + VIP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    private Long userId;
    private String feedType;
}
