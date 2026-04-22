package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.ExecuteFlowCmd;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

        IntegrationLog log = IntegrationLog.create(flow.getId(), cmd.inputData());
        IntegrationLog savedLog = logRepository.save(log);

        // 异步执行流程
        // TODO: 发送消息到消息队列

        return savedLog.getId().value();
    }
}