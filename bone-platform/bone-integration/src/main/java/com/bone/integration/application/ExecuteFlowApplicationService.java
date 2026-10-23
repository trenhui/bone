package com.bone.integration.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.ExecuteFlowCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.support.FlowExecutionSupport;
import com.bone.integration.application.support.FlowSupport;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
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
    // flowId 缺失必须在这里挡掉，不能交给 flowRepository.findById(null)：
    // SDK 的 BaseRepository.findById 对 null 抛 IllegalArgumentException("ID must not be null")，
    // 而 Spring 与两个 advice 都没有该异常的映射 ⇒ 冒泡到 catch-all 兜底成 **500 COMMON_INTERNAL_ERROR**
    // （2026-10-23 全模块联调实测：POST /api/v1/integration/executions 传空体即 500）。
    // 这是 100% 由请求体缺失字段触发的客户端错误，语义必须是 400。
    if (cmd == null || cmd.flowId() == null) {
      throw new DomainException("flowId 不能为空");
    }
    IntegrationFlow flow = flowRepository.findById(cmd.flowId());
    if (flow == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, cmd.flowId());
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
