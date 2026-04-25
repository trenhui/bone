package com.bone.integration.domain.repository;

import com.bone.integration.domain.flow.FlowNode;
import com.bone.metadata.sdk.Repository;

public interface FlowNodeRepository extends Repository<FlowNode, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
