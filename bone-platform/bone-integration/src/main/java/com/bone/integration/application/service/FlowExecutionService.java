package com.bone.integration.application.service;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.infrastructure.observability.IntegrationExecutionMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 流程执行门面：默认委托 {@link LinearSyncFlowRuntime}（INT-09）。 */
@Service
@RequiredArgsConstructor
public class FlowExecutionService {

    private final FlowRuntime flowRuntime;
    private final IntegrationExecutionMetrics executionMetrics;

    public void execute(IntegrationLog log, IntegrationFlow flow) {
        flowRuntime.execute(log, flow);
        executionMetrics.record(log);
    }
}
