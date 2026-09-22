package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

public interface FlowNodeRepository extends Repository<FlowNode, Long> {

  default List<FlowNode> findByFlowId(Long flowId) {
    return findByCriteria(Criteria.<FlowNode>create().eq(FlowNode::getFlowId, flowId));
  }
}
