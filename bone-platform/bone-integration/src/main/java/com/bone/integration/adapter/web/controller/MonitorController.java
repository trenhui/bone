package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.integration.application.ExecuteFlowApplicationService;
import com.bone.integration.application.ExecutionDetailQueryApplicationService;
import com.bone.integration.application.ExecutionLogLinesQueryApplicationService;
import com.bone.integration.application.ExecutionLogListQueryApplicationService;
import com.bone.integration.application.FlowStatisticsQueryApplicationService;
import com.bone.integration.application.command.ExecuteFlowCommand;
import com.bone.integration.application.query.dto.ExecutionLogDto;
import com.bone.integration.application.query.dto.FlowStatisticsDto;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 集成执行监控控制器。
 *
 * <p><b>授权模型</b>：两个写端点（触发执行 / 重试）统一挂 {@code integration:executions:write}。
 *
 * <p><b>为何不把重试拆成独立码</b>：业界SoD 拆的是「决策权vs 执行权」（如审批 vs 提交）， 而触发与重试是<b>同一动作的两个入口</b>——都调用 {@code
 * ExecuteFlowApplicationService.handle}， 输入同为 {@code (flowId, inputData)}，能触发的人必然也能重试（重试只是把历史
 * inputData 再喂一次）。 拆码只会让权限目录与种子多维护一条码，却不产生任何隔离收益。
 */
@RestController
@RequestMapping(PlatformApiPaths.INTEGRATION_V1)
@RequiredArgsConstructor
public class MonitorController {
  private final ExecuteFlowApplicationService executeFlowHandler;
  private final ExecutionLogListQueryApplicationService executionLogListQueryHandler;
  private final ExecutionLogLinesQueryApplicationService executionLogLinesQueryHandler;
  private final ExecutionDetailQueryApplicationService executionDetailQueryHandler;
  private final FlowStatisticsQueryApplicationService flowStatisticsQueryHandler;

  @PreAuthorize("hasAuthority('integration:executions:write')")
  @PostMapping("/executions")
  public ApiResponse<Long> execute(@RequestBody ExecuteFlowCommand cmd) {
    Long id = executeFlowHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping("/executions")
  public ApiResponse<PageResult<ExecutionLogDto>> listExecutions(ExecutionLogListQuery qry) {
    PageResult<ExecutionLogDto> result = executionLogListQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/executions/{id}")
  public ApiResponse<ExecutionLogDto> getExecution(@PathVariable Long id) {
    ExecutionLogDto dto = executionDetailQueryHandler.handle(new ExecutionDetailQuery(id));
    return ApiResponse.success(dto);
  }

  @GetMapping("/executions/{id}/logs")
  public ApiResponse<List<Map<String, Object>>> executionLogs(@PathVariable Long id) {
    return ApiResponse.success(executionLogLinesQueryHandler.handle(id));
  }

  @PreAuthorize("hasAuthority('integration:executions:write')")
  @PostMapping("/executions/{id}/retry")
  public ApiResponse<Long> retry(@PathVariable Long id) {
    ExecutionLogDto log = executionDetailQueryHandler.handle(new ExecutionDetailQuery(id));
    ExecuteFlowCommand cmd = new ExecuteFlowCommand(log.flowId(), log.inputData());
    Long newId = executeFlowHandler.handle(cmd);
    return ApiResponse.success(newId);
  }

  @GetMapping("/statistics")
  public ApiResponse<List<FlowStatisticsDto>> getStatistics(
      @RequestParam(required = false) Long flowId) {
    List<FlowStatisticsDto> dtos =
        flowStatisticsQueryHandler.handle(new FlowStatisticsQuery(flowId));
    return ApiResponse.success(dtos);
  }
}
