package com.bone.integration.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.ExecuteFlowCommand;
import com.bone.integration.application.command.handler.ExecuteFlowApplicationService;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionDetailQueryApplicationService;
import com.bone.integration.application.query.handler.ExecutionLogListQueryApplicationService;
import com.bone.integration.application.query.handler.FlowStatisticsQueryApplicationService;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import com.bone.integration.application.service.FlowMonitorService;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorControllerTest {

  @Mock private ExecuteFlowApplicationService executeFlowHandler;

  @Mock private ExecutionLogListQueryApplicationService executionLogListQueryHandler;

  @Mock private IntegrationLogRepository logRepository;

  @Mock private IntegrationFlowRepository flowRepository;

  @Mock private FlowMonitorService flowMonitorService;
  @Mock private ExecutionDetailQueryApplicationService executionDetailQueryHandler;
  @Mock private FlowStatisticsQueryApplicationService flowStatisticsQueryHandler;

  @InjectMocks private MonitorController monitorController;

  @Test
  void execute_delegatesToHandler() {
    ExecuteFlowCommand cmd = new ExecuteFlowCommand(1L, "{\"k\":\"v\"}");
    when(executeFlowHandler.handle(cmd)).thenReturn(99L);

    var response = monitorController.execute(cmd);

    assertTrue(response.isSuccess());
    assertEquals(99L, response.getData());
  }

  @Test
  void listExecutions_returnsPage() {
    ExecutionLogListQuery qry = new ExecutionLogListQuery(1, 10, 1L, null);
    PageResult<ExecutionLogDTO> page = PageResult.of(Collections.emptyList(), 0L, 1, 10);
    when(executionLogListQueryHandler.handle(qry)).thenReturn(page);

    var response = monitorController.listExecutions(qry);

    assertTrue(response.isSuccess());
    assertEquals(page, response.getData());
  }

  @Test
  void getExecution_mapsDomainToDto() {
    ExecutionLogDTO dto =
        new ExecutionLogDTO(1L, 2L, ExecutionStatus.SUCCESS.name(), null, null, "{}", "ok", null);
    when(executionDetailQueryHandler.handle(any(ExecutionDetailQuery.class))).thenReturn(dto);

    var response = monitorController.getExecution(1L);

    assertTrue(response.isSuccess());
    assertEquals(1L, response.getData().id());
    assertEquals(2L, response.getData().flowId());
    assertEquals(ExecutionStatus.SUCCESS.name(), response.getData().status());
  }

  @Test
  void getStatistics_aggregatesByFlowId() {
    FlowStatisticsDTO stats = new FlowStatisticsDTO(10L, "demo", 5L, 4L, 1L, 80.0);
    when(flowStatisticsQueryHandler.handle(any(FlowStatisticsQuery.class)))
        .thenReturn(List.of(stats));

    var response = monitorController.getStatistics(10L);

    assertTrue(response.isSuccess());
    assertEquals(1, response.getData().size());
    FlowStatisticsDTO dto = response.getData().get(0);
    assertEquals(10L, dto.flowId());
    assertEquals(5L, dto.executionCount());
    verify(flowStatisticsQueryHandler).handle(any(FlowStatisticsQuery.class));
  }
}
