package com.bone.integration.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.ExecuteFlowCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.support.FlowExecutionSupport;
import com.bone.integration.application.support.FlowSupport;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "ExecuteFlow",
    description = "执行集成流程",
    inputSchema = "{\"flowId\": \"long\", \"inputData\": \"object\"}",
    outputSchema = "{\"executionId\": \"long\"}",
    idempotent = false,
    cost = 5,
    retryable = true,
    timeout = 300)
@Component
@RequiredArgsConstructor
public class ExecuteFlowApplicationService {
  private final IntegrationLogRepository logRepository;
  private final IntegrationFlowRepository flowRepository;
  private final FlowSupport flowSupport;
  private final FlowExecutionSupport flowExecutionSupport;
  private final IntegrationDomainEventPublisher domainEventPublisher;

  @Transactional
  public Long handle(ExecuteFlowCommand cmd) {
    IntegrationFlow flow = flowRepository.findById(cmd.flowId());
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    flowSupport.validateFlow(flow);

    Long logId = DistributedIdGenerator.generateLongId();
    IntegrationLog log = IntegrationLog.create(logId, flow.getId(), cmd.inputData());
    logRepository.save(log);
    flowExecutionSupport.execute(log, flow);
    logRepository.save(log);
    domainEventPublisher.publishFrom(log);
    return log.getId();
  }
}
