package com.bone.integration.domain.repository;

import com.bone.integration.domain.flow.FlowConnection;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

public interface FlowConnectionRepository extends Repository<FlowConnection, Long> {

  default List<FlowConnection> findByFlowId(Long flowId) {
    return findByCriteria(Criteria.<FlowConnection>create().eq(FlowConnection::getFlowId, flowId));
  }
}
