package com.bone.integration.application.support;

import com.bone.integration.application.port.IntegrationExecutionRecorder;
import com.bone.integration.application.port.out.FlowRuntime;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.infrastructure.flow.LinearSyncFlowRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 流程执行门面：默认委托 {@link LinearSyncFlowRuntime}（INT-09）。 */
@Service
@RequiredArgsConstructor
public class FlowExecutionSupport {

  private final FlowRuntime flowRuntime;
  private final IntegrationExecutionRecorder executionRecorder;

  public void execute(IntegrationLog log, IntegrationFlow flow) {
    flowRuntime.execute(log, flow);
    executionRecorder.record(log);
  }
}
