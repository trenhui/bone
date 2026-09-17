/**
 * application 层读侧端口：CQRS 读模型的查询接口。
 *
 * <p>按 ADR-0028 Selective CQRS 策略，这些端口用于 <strong>聚合内部 JOIN</strong>（如 OrderWithItemsProjection
 * 从订单聚合查订单 + 明细）和跨聚合投影（如 OrderSummary 的用户昵称 JOIN）。 简单读（按 ID 查详情、简单分页）通常由 ApplicationService 直查写
 * Repository，不一定经过此包。
 *
 * <p><b>放什么</b>：OrderQueryPort（findOrderWithItems 聚合 JOIN、findOrderPage 分页投影）、
 * PaymentQueryPort（findById 单表查询，为未来 JOIN 扩展预留）。
 */
package com.bone.blueprint.application.query.port;
