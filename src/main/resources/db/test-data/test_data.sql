-- =====================================================
-- 消息中心测试数据
-- 覆盖所有 6 种 Feed 类型，含已读/未读/已撤回/已过期场景
-- =====================================================

-- 清理旧数据（按顺序避免外键冲突）
DELETE FROM user_message;
DELETE FROM user_feed_cursor;
DELETE FROM subscription;
DELETE FROM push_task;
DELETE FROM message_feed_mapping;
DELETE FROM message;
DELETE FROM notification_template;

-- ==================== 通知模板 ====================
INSERT INTO notification_template (template_code, name, channels, category, title_template, content_template, wechat_template_id, sms_template_name, created_at, updated_at) VALUES
-- SYSTEM
('system_maintenance', '系统维护通知',   '["in_app","sms"]',    'system',    '系统维护通知',                              '亲爱的用户，系统将于{time}进行维护，预计持续{duration}。',           NULL, 'SMS_MAINTENANCE',   NOW(), NOW()),
('system_security',    '账号安全提醒',   '["in_app","sms"]',    'system',    '账号安全提醒',                              '您的账号于{time}在{device}上登录，如非本人操作请及时修改密码。',    NULL, 'SMS_SECURITY',      NOW(), NOW()),
('system_update',      '功能更新公告',   '["in_app"]',          'system',    '功能更新：{feature}',                        '我们上线了{feature}，{description}，快来体验吧！',                NULL, NULL,                NOW(), NOW()),

-- ORDER
('order_paid',         '订单支付成功',   '["in_app","sms"]',    'order',     '订单已支付',                                '您的订单{orderId}已支付成功，金额{amount}元，预计{date}送达。',   NULL, 'SMS_ORDER_PAID',    NOW(), NOW()),
('order_shipped',      '订单已发货',     '["in_app","sms"]',    'order',     '订单已发货',                                '您的订单{orderId}已发出，快递单号{trackingNo}，请留意查收。',     NULL, 'SMS_ORDER_SHIPPED', NOW(), NOW()),
('order_refund',       '退款通知',       '["in_app"]',          'order',     '退款已处理',                                '订单{orderId}的退款申请已处理，金额{amount}元将退回至您的账户。', NULL, NULL,                NOW(), NOW()),

-- VIP
('vip_upgrade',        'VIP 升级通知',  '["in_app","sms"]',    'vip',       'VIP 会员升级',                              '恭喜您已升级为{level}会员，享受{benefits}等专属权益！',            NULL, 'SMS_VIP_UPGRADE',   NOW(), NOW()),
('vip_expiry',         'VIP 过期提醒',  '["in_app","sms"]',    'vip',       'VIP 即将过期',                              '您的 VIP 会员将于{date}到期，到期后将失去{benefits}等权益。',     NULL, 'SMS_VIP_EXPIRY',    NOW(), NOW()),

-- MARKETING
('marketing_coupon',   '优惠券发放',     '["in_app"]',          'marketing', '优惠券到账',                                '您已获得{value}元优惠券，有效期至{expireDate}，{condition}。',   NULL, NULL,                NOW(), NOW()),
('marketing_promotion','营销活动通知',   '["in_app","sms"]',    'marketing', '{name}活动开始',                            '{name}已开启，{description}，点击查看详情。',                     NULL, 'SMS_PROMOTION',     NOW(), NOW()),

-- LOGISTICS
('logistics_shipped',  '物流状态通知',  '["in_app","sms"]',    'logistics', '包裹{status}',                              '您的包裹{trackingNo}已{status}，{description}。',                 NULL, 'SMS_LOGISTICS',     NOW(), NOW()),

-- RISK
('risk_login',         '异地登录提醒',  '["in_app","sms"]',    'risk',      '异地登录提醒',                              '您的账号于{time}在{location}使用{device}登录，如非本人请立即冻结。', NULL, 'SMS_RISK_LOGIN',    NOW(), NOW()),
('risk_freeze',        '账号冻结通知',  '["in_app","sms"]',    'risk',      '账号已冻结',                                '您的账号因{reason}已被冻结，如有疑问请联系客服。',               NULL, 'SMS_RISK_FREEZE',   NOW(), NOW());

-- ==================== 消息主体 ====================
INSERT INTO message (id, biz_type, title, content_url, send_type, status, create_time, expire_time) VALUES
-- SYSTEM feed（消息 ID 1-5）
(1,  'system',    '系统升级通知',           '/content/1',   0, 0, '2026-06-20 08:00:00.000', NULL),
(2,  'system',    '账号安全提醒',           '/content/2',   0, 0, '2026-06-20 10:00:00.000', NULL),
(3,  'system',    '隐私政策更新',           '/content/3',   0, 0, '2026-06-21 09:00:00.000', NULL),
(4,  'system',    '服务器维护通知',         '/content/4',   0, 0, '2026-06-22 02:00:00.000', NULL),
(5,  'system',    '功能更新公告',           '/content/5',   0, 0, '2026-06-22 14:00:00.000', NULL),

-- ORDER feed（消息 ID 6-10）
(6,  'order',     '订单已支付',             '/content/6',   0, 0, '2026-06-20 09:00:00.000', '2026-07-20 09:00:00.000'),
(7,  'order',     '订单已发货',             '/content/7',   0, 0, '2026-06-21 10:00:00.000', '2026-07-21 10:00:00.000'),
(8,  'order',     '订单已完成',             '/content/8',   0, 0, '2026-06-22 11:00:00.000', '2026-07-22 11:00:00.000'),
(9,  'order',     '订单退款成功',           '/content/9',   0, 0, '2026-06-22 15:00:00.000', '2026-07-22 15:00:00.000'),
(10, 'order',     '订单取消通知',           '/content/10',  0, 0, '2026-06-23 08:00:00.000', '2026-07-23 08:00:00.000'),

-- VIP feed（消息 ID 11-15）
(11, 'vip',       'VIP 会员权益更新',       '/content/11',  0, 0, '2026-06-19 08:00:00.000', NULL),
(12, 'vip',       'VIP 专属活动上线',       '/content/12',  0, 0, '2026-06-20 08:00:00.000', NULL),
(13, 'vip',       'VIP 积分翻倍通知',       '/content/13',  0, 0, '2026-06-21 08:00:00.000', NULL),
(14, 'vip',       'VIP 生日礼包发放',       '/content/14',  0, 0, '2026-06-22 08:00:00.000', NULL),
(15, 'vip',       'VIP 等级即将过期',       '/content/15',  0, 0, '2026-06-23 08:00:00.000', NULL),

-- MARKETING feed（消息 ID 16-19）
(16, 'marketing', '618 大促活动',           '/content/16',  0, 0, '2026-06-18 08:00:00.000', '2026-06-25 08:00:00.000'),
(17, 'marketing', '新人专享优惠券',         '/content/17',  0, 0, '2026-06-19 10:00:00.000', '2026-06-30 10:00:00.000'),
(18, 'marketing', '限时秒杀预告',           '/content/18',  0, 0, '2026-06-22 12:00:00.000', '2026-06-28 12:00:00.000'),
(19, 'marketing', '品牌会员日',             '/content/19',  0, 0, '2026-06-23 06:00:00.000', '2026-07-01 06:00:00.000'),

-- LOGISTICS feed（消息 ID 20-22）
(20, 'logistics', '包裹已揽收',             '/content/20',  0, 0, '2026-06-21 08:00:00.000', '2026-07-21 08:00:00.000'),
(21, 'logistics', '包裹运输中',             '/content/21',  0, 0, '2026-06-22 08:00:00.000', '2026-07-22 08:00:00.000'),
(22, 'logistics', '包裹已签收',             '/content/22',  0, 0, '2026-06-23 10:00:00.000', '2026-07-23 10:00:00.000'),

-- RISK feed（消息 ID 23-25）
(23, 'risk',      '异地登录提醒',           '/content/23',  0, 0, '2026-06-22 03:00:00.000', NULL),
(24, 'risk',      '密码修改确认',           '/content/24',  0, 0, '2026-06-22 16:00:00.000', NULL),
(25, 'risk',      '可疑设备检测',           '/content/25',  0, 0, '2026-06-23 07:00:00.000', NULL),

-- 已撤回的消息（消息 ID 26）
(26, 'system',    '已撤回的测试消息',       '/content/26',  0, 1, '2026-06-20 12:00:00.000', NULL),

-- 已过期的消息（消息 ID 27）
(27, 'marketing', '已过期的促销',           '/content/27',  0, 0, '2026-06-01 08:00:00.000', '2026-06-10 08:00:00.000'),

-- USER 类型消息（消息 ID 28-30，非广播）
(28, 'order',     '您的专属优惠',           '/content/28',  1, 0, '2026-06-22 09:00:00.000', '2026-07-22 09:00:00.000'),
(29, 'order',     '补差价通知',             '/content/29',  1, 0, '2026-06-23 09:00:00.000', '2026-07-23 09:00:00.000'),
(30, 'risk',      '账号冻结警告',           '/content/30',  1, 0, '2026-06-23 11:00:00.000', NULL);

-- ==================== 消息-Feed 关联 ====================
INSERT INTO message_feed_mapping (message_id, feed_type) VALUES
-- SYSTEM
(1, 'system'), (2, 'system'), (3, 'system'), (4, 'system'), (5, 'system'),
-- ORDER
(6, 'order'), (7, 'order'), (8, 'order'), (9, 'order'), (10, 'order'),
-- VIP
(11, 'vip'), (12, 'vip'), (13, 'vip'), (14, 'vip'), (15, 'vip'),
-- MARKETING
(16, 'marketing'), (17, 'marketing'), (18, 'marketing'), (19, 'marketing'),
-- LOGISTICS
(20, 'logistics'), (21, 'logistics'), (22, 'logistics'),
-- RISK
(23, 'risk'), (24, 'risk'), (25, 'risk'),
-- 已撤回
(26, 'system'),
-- 已过期
(27, 'marketing'),
-- USER 类型消息也关联到对应 feed
(28, 'order'), (29, 'order'), (30, 'risk');

-- ==================== 用户订阅 ====================
-- 用户 1001：订阅了 SYSTEM、ORDER、VIP
INSERT INTO subscription (user_id, feed_type) VALUES
(1001, 'system'), (1001, 'order'), (1001, 'vip');

-- 用户 1002：订阅了所有 6 个 Feed
INSERT INTO subscription (user_id, feed_type) VALUES
(1002, 'system'), (1002, 'order'), (1002, 'vip'),
(1002, 'marketing'), (1002, 'logistics'), (1002, 'risk');

-- ==================== 用户已读进度（Cursor）====================
-- 用户 1001：SYSTEM 已读到消息 3，ORDER 和 VIP 从未全部标已读（cursor=0）
INSERT INTO user_feed_cursor (user_id, feed_type, cursor, update_time) VALUES
(1001, 'system', 3, '2026-06-23 10:00:00.000');

-- 用户 1002：从未标过全部已读（无记录，默认 cursor=0）

-- ==================== 逐条已读记录 ====================
-- 用户 1001 在 SYSTEM feed 中逐条读了消息 4
INSERT INTO user_message (user_id, message_id, feed_type, biz_type, send_type, status, read_time, create_time) VALUES
(1001, 4, 'system', 'system', 0, 1, '2026-06-23 10:30:00.000', '2026-06-23 10:30:00.000');

-- ==================== USER 类型消息已读记录 ====================
-- 用户 1002 收到了 USER 类型消息（发送时写入 UNREAD）
INSERT INTO user_message (user_id, message_id, feed_type, biz_type, send_type, status, read_time, create_time) VALUES
(1002, 28, 'order', 'order', 1, 0, NULL, '2026-06-23 10:00:00.000'),
(1002, 29, 'order', 'order', 1, 0, NULL, '2026-06-23 11:00:00.000'),
(1002, 30, 'risk',  'risk',  1, 0, NULL, '2026-06-23 12:00:00.000');

-- ==================== 验证查询 ====================
-- 用户 1001(订阅 system/order/vip):
--   system  未读 = 广播消息(5) - 逐条已读(1) + USER未读(0) = 4
--   order   未读 = 广播消息(5) - 0 + 0 = 5
--   vip     未读 = 广播消息(5) - 0 + 0 = 5
--
-- 用户 1002(全订阅):
--   system    未读 = 5-0+0 = 5
--   order     未读 = 5-0+2(USER) = 7
--   vip       未读 = 5-0+0 = 5
--   marketing 未读 = 2-0+0 = 2（不含过期那个）
--   logistics 未读 = 3-0+0 = 3
--   risk      未读 = 3-0+1(USER) = 4
--
-- 注意：26(已撤回)和27(已过期)因 status/expire_time 过滤不计入总数
