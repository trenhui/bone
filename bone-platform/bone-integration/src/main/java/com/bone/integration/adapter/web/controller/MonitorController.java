package com.bone.integration.adapter.web.controller;

import com.bone.core.exception.DomainException;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.ExecuteFlowCommand;
import com.bone.integration.application.command.handler.ExecuteFlowHandler;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionLogListQueryHandler;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.service.FlowMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1)
@RequiredArgsConstructor
public class MonitorController {
    private final ExecuteFlowHandler executeFlowHandler;
    private final ExecutionLogListQueryHandler executionLogListQueryHandler;
    private final IntegrationLogRepository logRepository;
    private final IntegrationFlowRepository flowRepository;
    private final FlowMonitorService flowMonitorService;

    @PostMapping("/executions")
    public ApiResponse<Long> execute(@RequestBody ExecuteFlowCommand cmd) {
        Long id = executeFlowHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping("/executions")
    public ApiResponse<PageResult<ExecutionLogDTO>> listExecutions(ExecutionLogListQuery qry) {
        PageResult<ExecutionLogDTO> result = executionLogListQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/executions/{id}")
    public ApiResponse<ExecutionLogDTO> getExecution(@PathVariable Long id) {
        IntegrationLog log = logRepository.findById(id);
        if (log == null) {
            throw new DomainException("执行记录不存在");
        }
        ExecutionLogDTO dto = new ExecutionLogDTO(
                log.getId(),
                log.getFlowId(),
                log.getStatus().name(),
                log.getStartedAt(),
                log.getEndedAt(),
                log.getInputData(),
                log.getOutputData(),
                log.getErrorMessage());
        return ApiResponse.success(dto);
    }

    @PostMapping("/executions/{id}/retry")
    public ApiResponse<Long> retry(@PathVariable Long id) {
        IntegrationLog log = logRepository.findById(id);
        if (log == null) {
            throw new DomainException("执行记录不存在");
        }
        ExecuteFlowCommand cmd = new ExecuteFlowCommand(log.getFlowId(), log.getInputData());
        Long newId = executeFlowHandler.handle(cmd);
        return ApiResponse.success(newId);
    }

    @GetMapping("/statistics")
    public ApiResponse<FlowStatisticsDTO> getStatistics(@RequestParam Long flowId) {
        IntegrationFlow flow = flowRepository.findById(flowId);
        if (flow == null) {
            throw new DomainException("流程不存在");
        }
        long executionCount = flowMonitorService.getExecutionCount(flow.getId());
        long successCount = flowMonitorService.getSuccessCount(flow.getId());
        long failureCount = flowMonitorService.getFailureCount(flow.getId());
        double successRate = flowMonitorService.getSuccessRate(flow.getId());

        FlowStatisticsDTO dto = new FlowStatisticsDTO(
                flow.getId(),
                flow.getName(),
                executionCount,
                successCount,
                failureCount,
                successRate);
        return ApiResponse.success(dto);
    }
}
