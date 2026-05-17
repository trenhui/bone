package com.bone.integration.domain.service;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowMonitorService {
    private final IntegrationLogRepository logRepository;

    public List<IntegrationLog> getExecutionLogs(FlowId flowId) {
        return logRepository.findByFlowId(flowId);
    }

    public long getExecutionCount(FlowId flowId) {
        return logRepository.countByFlowId(flowId);
    }

    public long getSuccessCount(FlowId flowId) {
        return logRepository.countByFlowIdAndStatus(flowId, "SUCCESS");
    }

    public long getFailureCount(FlowId flowId) {
        return logRepository.countByFlowIdAndStatus(flowId, "FAILED") +
               logRepository.countByFlowIdAndStatus(flowId, "TIMEOUT");
    }

    public double getSuccessRate(FlowId flowId) {
        long total = logRepository.countByFlowId(flowId);
        if (total == 0) {
            return 0.0;
        }
        long success = logRepository.countByFlowIdAndStatus(flowId, "SUCCESS");
        return (double) success / total * 100;
    }
}