package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.integration.application.command.cmd.ExecuteFlowCommand;
import com.bone.integration.application.command.handler.ExecuteFlowHandler;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionDetailQueryHandler;
import com.bone.integration.application.query.handler.ExecutionLogLinesQueryHandler;
import com.bone.integration.application.query.handler.ExecutionLogListQueryHandler;
import com.bone.integration.application.query.handler.FlowStatisticsQueryHandler;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1)
@RequiredArgsConstructor
public class MonitorController {
  private final ExecuteFlowHandler executeFlowHandler;
  private final ExecutionLogListQueryHandler executionLogListQueryHandler;
  private final ExecutionLogLinesQueryHandler executionLogLinesQueryHandler;
  private final ExecutionDetailQueryHandler executionDetailQueryHandler;
  private final FlowStatisticsQueryHandler flowStatisticsQueryHandler;

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
    ExecutionLogDTO dto = executionDetailQueryHandler.handle(new ExecutionDetailQuery(id));
    return ApiResponse.success(dto);
  }

  @GetMapping("/executions/{id}/logs")
  public ApiResponse<List<Map<String, Object>>> executionLogs(@PathVariable Long id) {
    return ApiResponse.success(executionLogLinesQueryHandler.handle(id));
  }

  @PostMapping("/executions/{id}/retry")
  public ApiResponse<Long> retry(@PathVariable Long id) {
    ExecutionLogDTO log = executionDetailQueryHandler.handle(new ExecutionDetailQuery(id));
    ExecuteFlowCommand cmd = new ExecuteFlowCommand(log.flowId(), log.inputData());
    Long newId = executeFlowHandler.handle(cmd);
    return ApiResponse.success(newId);
  }

  @GetMapping("/statistics")
  public ApiResponse<List<FlowStatisticsDTO>> getStatistics(
      @RequestParam(required = false) Long flowId) {
    List<FlowStatisticsDTO> dtos =
        flowStatisticsQueryHandler.handle(new FlowStatisticsQuery(flowId));
    return ApiResponse.success(dtos);
  }
}
