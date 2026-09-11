package com.bone.architecture.fixture.domain.repository;

import com.bone.architecture.fixture.domain.order.OrderAggregate;

/** 规则单测夹具：合法的写侧仓储——返回聚合 / boolean / void，允许复合自然键。 */
public interface OrderAggregateRepository {

  /** 复合自然键（id + tenantId），返回聚合 → 合法。 */
  OrderAggregate findByIdInTenant(Long id, Long tenantId);

  /** 单键 exists 返回 boolean → 合法。 */
  boolean existsByCode(String code);

  /** 聚合级动词 remove 返回 void → 合法。 */
  void remove(OrderAggregate aggregate);
}
