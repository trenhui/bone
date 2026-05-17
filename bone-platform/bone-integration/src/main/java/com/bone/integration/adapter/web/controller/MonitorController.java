package com.bone.integration.adapter.web.controller;

import com.bone.integration.application.command.cmd.ExecuteFlowCmd;
import com.bone.integration.application.command.handler.ExecuteFlowHandler;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionLogListQueryHandler;
import com.bone.integration.application.query.qry.ExecutionLogListQry;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowMonitorService;
import com.bone.core.model.PageResult;
import com.bone.core.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class MonitorController {
    private final ExecuteFlowHandler executeFlowHandler;
    private final ExecutionLogListQueryHandler executionLogListQueryHandler;
    private final IntegrationLogRepository logRepository;
    private final IntegrationFlowRepository flowRepository;
    private final FlowMonitorService flowMonitorService;

    @PostMapping("/executions")
    public ApiResponse<Long> execute(@RequestBody ExecuteFlowCmd cmd) {
        Long id = executeFlowHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping("/executions")
    public ApiResponse<PageResult<ExecutionLogDTO>> listExecutions(ExecutionLogListQry qry) {
        PageResult<ExecutionLogDTO> result = executionLogListQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/executions/{id}")
    public ApiResponse<ExecutionLogDTO> getExecution(@PathVariable Long id) {
        IntegrationLog log = logRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("执行记录不存在"));
        ExecutionLogDTO dto = new ExecutionLogDTO(
                log.getId().value(),
                log.getFlowId().value(),
                log.getStatus().name(),
                log.getStartTime(),
                log.getEndTime(),
                log.getInputData(),
                log.getOutputData(),
                log.getErrorMessage()
        );
        return ApiResponse.success(dto);
    }

    @PostMapping("/executions/{id}/retry")
    public ApiResponse<Long> retry(@PathVariable Long id) {
        IntegrationLog log = logRepository.findById(id)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("执行记录不存在"));
        ExecuteFlowCmd cmd = new ExecuteFlowCmd(log.getFlowId().value(), log.getInputData());
        Long newId = executeFlowHandler.handle(cmd);
        return ApiResponse.success(newId);
    }

    @GetMapping("/statistics")
    public ApiResponse<FlowStatisticsDTO> getStatistics(@RequestParam Long flowId) {
        IntegrationFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new com.bone.core.exception.DomainException("流程不存在"));
        long executionCount = flowMonitorService.getExecutionCount(flow.getId());
        long successCount = flowMonitorService.getSuccessCount(flow.getId());
        long failureCount = flowMonitorService.getFailureCount(flow.getId());
        double successRate = flowMonitorService.getSuccessRate(flow.getId());

        FlowStatisticsDTO dto = new FlowStatisticsDTO(
                flow.getId().value(),
                flow.getName(),
                executionCount,
                successCount,
                failureCount,
                successRate
        );
        return ApiResponse.success(dto);
    }
}