package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.order.OrderItem;
import com.bone.metadata.sdk.Repository;

/**
 * 订单明细写侧仓储端口（E-5.5：领域端口统一放 {@code domain/repository}）。
 *
 * <p><b>为何在 {@code domain.repository} 而非 {@code domain.order}</b>：E-5.5 规定领域端口包唯一。此前为避免 R9
 * （一事务一聚合）把本端口"藏"到聚合同包——那是<strong>用包位置绕过门禁</strong>：规则按 Repository 接口类型计数，
 * 换个包就能变绿，门禁随之失效。正确做法是修正判据：R9 现按<strong>被持久化的聚合根类型</strong>计数， {@code OrderItem} 是 {@code Order}
 * 聚合内的实体（非聚合根），与 {@code Order} 同事务落库属<strong>同一聚合</strong>， 天然合规，无需靠包位置规避。
 *
 * <p><b>读明细走查询侧</b>：下游（如库存预留）读取明细应通过 {@code OrderReadPort.findOrderWithItems}（联表投影）， 而非在本写侧仓储加返回
 * {@code List} 的查询方法（违反仓储方法白名单：写侧仓储只返 聚合根 / {@code Optional<聚合根>} / {@code boolean} / {@code
 * void}）。故本接口保持裸接口，仅复用 SDK 的 {@code save/findById} 等基础能力。
 *
 * <p>订单明细表 {@code t_order_item} 无独立多租户列，租户隔离经 {@code t_order.tenant_id} 间接保证，按 {@code orderId}
 * 即可定位（orderId 为全局唯一雪花 ID）。
 *
 * <p><b>E-8.3 S1 信号评估（登记，非立即重构）</b>：Order 聚合持有需持久化的 {@code List<OrderItem>} 集合，且明细有独立表 {@code
 * t_order_item} 与独立写侧仓储——这正是 E-8.3 的 S1 退出信号（聚合持有需持久化集合/嵌套实体， 无 SDK 级联落库）。当前采用「D1 充血聚合 + 显式逐条
 * save」的过渡方案（见 {@code CreateOrderCommandHandler}），待 SDK 支持聚合级联或团队决定 PO 分离时，再按 E-8.3 迁移路径演进。
 */
public interface OrderItemRepository extends Repository<OrderItem, Long> {}
