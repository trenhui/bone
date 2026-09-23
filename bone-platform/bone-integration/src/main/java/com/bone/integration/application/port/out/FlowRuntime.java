package com.bone.integration.application.port.out;

import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;

/** 流程执行运行时（INT-09 同步实现；INT-11 Camel 编译执行）。 */
public interface FlowRuntime {

  void execute(IntegrationLog log, IntegrationFlow flow);
}
