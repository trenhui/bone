package com.bone.architecture.fixture.domain.repository;

import com.bone.architecture.fixture.domain.order.OrderAggregate;
import java.util.List;
import java.util.Optional;

/** 规则单测夹具：违规仓储——投影 / List / 持久化词汇 / exists 读意图，用于验证负向拦截。 */
public interface BadReportingRepository {

  /** 返回 DTO 投影 → 违规（读侧职责）。 */
  OrderSummaryDto findSummary(Long id);

  /** 返回 {@code List<聚合>} → <b>合法</b>（ADR-0030 R2：{@code List} 可承载领域层内的读模型/聚合）。 */
  List<OrderAggregate> findByStatus(String status);

  /** 返回 {@code Optional<聚合>} → 合法。 */
  Optional<OrderAggregate> findByCode(String code);

  /** 复合自然键返回聚合 → 合法。 */
  OrderAggregate findByIdInTenant(Long id, Long tenantId);

  /** 持久化词汇入方法名 → 违规。 */
  void updateStatus(OrderAggregate aggregate);

  /** {@code exists} 读侧意图入方法名 → 违规（诱导先查后判竞态；E-4.1 禁止）。 */
  boolean existsByCode(String code);
}
