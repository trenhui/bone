package com.bone.integration.application.service;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.application.port.IntegrationExecutionRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 流程执行门面：默认委托 {@link LinearSyncFlowRuntime}（INT-09）。 */
@Service
@RequiredArgsConstructor
public class FlowExecutionService {

    private final FlowRuntime flowRuntime;
    private final IntegrationExecutionRecorder executionRecorder;

    public void execute(IntegrationLog log, IntegrationFlow flow) {
        flowRuntime.execute(log, flow);
        executionRecorder.record(log);
    }
}
