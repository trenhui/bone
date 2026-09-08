package com.bone.blueprint.application.command.cmd;

/**
 * 订单取消命令（不可变 record，样板统一命令/查询用 record 的写法）。
 *
 * @param orderId 订单 ID
 * @param tenantId 租户 ID：<b>异步入口（定时任务）必须显式携带</b>（E-4.4 跨边界显式携带）。定时线程没有请求上下文， 若让 Handler 回落 {@code
 *     TenantProvider}，只会取到降级后的平台租户而查不到目标订单；HTTP 入口传 {@code null} 即可。
 */
public record CancelOrderCommand(Long orderId, Long tenantId) {}
