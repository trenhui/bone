package com.bone.blueprint.domain.order;

import com.bone.metadata.sdk.Repository;

/**
 * 订单明细写侧仓储端口。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p><b>为何放在 {@code domain.order} 而非 {@code domain.repository}</b>：订单明细是 Order
 * 聚合的<strong>子实体</strong>， 并非独立聚合根。DDD 门禁 R9（一事务一聚合）按 {@code ..domain.repository..} 接口类型去重计数 ——
 * 若本仓储落在 {@code domain.repository}， 则 {@code CreateOrderCommandHandler} 同时保存 Order 与 OrderItem
 * 会被判定为「一事务写两个聚合」而违规。 将明细仓储与聚合根 {@link Order} 同包，语义上表达「明细随订单聚合一同落库」，R9 仅计 OrderRepository（1
 * 个聚合），合规。
 *
 * <p><b>读明细走查询侧</b>：下游（如库存预留）读取明细应通过 {@code OrderReadPort.findOrderWithItems}（联表投影）， 而非在本写侧仓储加返回
 * {@code List} 的查询方法（违反仓储方法白名单：写侧仓储只返 聚合根 / {@code Optional<聚合根>} / {@code boolean} / {@code
 * void}）。 故本接口保持裸接口，仅复用 SDK 的 {@code save/findById} 等基础能力。
 *
 * <p>订单明细表 {@code t_order_item} 无独立多租户列，租户隔离经 {@code t_order.tenant_id} 间接保证，按 {@code orderId}
 * 即可定位（orderId 为全局唯一雪花 ID）。
 *
 * <p><b>E-8.3 S1 信号评估（登记，非立即重构）</b>：Order 聚合持有需持久化的 {@code List<OrderItem>} 集合，且明细有独立表 {@code
 * t_order_item} 与独立写侧仓储——这正是 E-8.3 的 S1 退出信号（聚合持有需持久化集合/嵌套实体，且无 SDK 级联落库）。 当前采用「D1 充血聚合 +
 * 同包明细仓储显式逐条 save」的过渡方案（见 {@code CreateOrderCommandHandler}）， 待 SDK 支持聚合级联或团队决定 PO 分离时，再按 E-8.3
 * 迁移路径演进：建 {@code OrderItemPO} + {@code OrderItemConverter}， 领域 {@code OrderItem} 落回 D0，仓储实现改为操作
 * PO。此处为已知技术债登记点，不影响当下合规。
 */
public interface OrderItemRepository extends Repository<OrderItem, Long> {}
