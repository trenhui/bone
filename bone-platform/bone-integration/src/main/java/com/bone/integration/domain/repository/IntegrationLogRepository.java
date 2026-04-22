package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface IntegrationLogRepository extends Repository<IntegrationLog, Long> {
    List<IntegrationLog> findByFlowId(FlowId flowId);
    List<IntegrationLog> findByFlowIdAndStatus(FlowId flowId, String status);
    long countByFlowId(FlowId flowId);
    long countByFlowIdAndStatus(FlowId flowId, String status);
}