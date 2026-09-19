/**
 * application 层读侧端口：CQRS 读模型的查询接口。
 *
 * <p>按 ADR-0028 Selective CQRS 策略，这些端口用于 <strong>聚合内部 JOIN</strong>（如 OrderWithItemsProjection
 * 从订单聚合查订单 + 明细）和跨聚合投影（如 OrderSummary 的用户昵称 JOIN）。 简单读（按 ID 查详情、简单分页）通常由 ApplicationService 直查写
 * Repository，不一定经过此包。
 *
 * <p><b>放什么</b>：PaymentQueryPort（两个全租户扫描）。订单读模型（findOrderWithItems 聚合 JOIN、findOrderPage 分页投影、
 * findCreatedExpiredBeforeAllTenants 全租户扫描）已随 ADR-0030 合并进 {@code
 * domain.repository.OrderRepository}，不再经此包。
 *
 * <p><b>不放什么</b>：与写聚合同形的简单读（如按 ID 查支付详情）——{@code PaymentDto} 是 {@code bp_payment} 全行的子集，与 {@code
 * Payment} 无读模型分歧，直接走写 Repository + Assembler， 不为"未来可能的 JOIN"预留空壳端口方法。
 */
package com.bone.blueprint.application.query.port;
