package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface FlowConnectionRepository extends Repository<FlowConnection, Long> {
    List<FlowConnection> findByFlowId(FlowId flowId);
    void deleteByFlowId(FlowId flowId);
}