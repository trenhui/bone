package com.bone.integration.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.ExecuteFlowCmd;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.connector.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
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
    timeout = 300
)
@Component
@RequiredArgsConstructor
public class ExecuteFlowHandler {
    private final IntegrationLogRepository logRepository;
    private final IntegrationFlowRepository flowRepository;
    private final FlowService flowService;

    @Transactional
    public Long handle(ExecuteFlowCmd cmd) {
        IntegrationFlow flow = flowRepository.findById(cmd.flowId())
                .orElseThrow(() -> new com.bone.core.exception.DomainException("流程不存在"));
        flowService.validateFlow(flow);

        Long logId = DistributedIdGenerator.generateLongId();
        IntegrationLog log = IntegrationLog.create(logId, flow.getId(), cmd.inputData());
        IntegrationLog savedLog = logRepository.save(log);

        return savedLog.getId();
    }
}
