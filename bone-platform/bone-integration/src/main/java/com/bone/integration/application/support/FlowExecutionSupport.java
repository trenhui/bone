package com.bone.integration.application.support;

import com.bone.integration.application.port.out.FlowRuntimePort;
import com.bone.integration.application.port.out.IntegrationExecutionRecorder;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 流程执行门面：默认委托 {@link FlowRuntimePort} 端口（INT-09）。 */
@Service
@RequiredArgsConstructor
public class FlowExecutionSupport {

  private final FlowRuntimePort flowRuntime;
  private final IntegrationExecutionRecorder executionRecorder;

  public void execute(IntegrationLog log, IntegrationFlow flow) {
    flowRuntime.execute(log, flow);
    executionRecorder.record(log);
  }
}
