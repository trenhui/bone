package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface FlowNodeRepository extends Repository<FlowNode, Long> {
    List<FlowNode> findByFlowId(FlowId flowId);
    void deleteByFlowId(FlowId flowId);
}