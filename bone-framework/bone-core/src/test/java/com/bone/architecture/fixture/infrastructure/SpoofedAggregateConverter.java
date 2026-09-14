package com.bone.architecture.fixture.infrastructure;

import com.bone.architecture.fixture.domain.order.SpoofedAggregate;

/**
 * 规则单测夹具：持久化转换器（*Converter 角色），设置聚合身份属法定豁免—— outerLayersMustNotMutateAggregateIdentity 对
 * Repository/Converter/Mapper 等角色不报违例。
 */
public class SpoofedAggregateConverter {

  public SpoofedAggregate toDomain(long id, String name) {
    SpoofedAggregate aggregate = new SpoofedAggregate();
    aggregate.setId(id);
    return aggregate;
  }
}
