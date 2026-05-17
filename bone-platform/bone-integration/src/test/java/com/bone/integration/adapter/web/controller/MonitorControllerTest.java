package com.bone.integration.adapter.web.controller;

import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.ExecuteFlowCmd;
import com.bone.integration.application.command.handler.ExecuteFlowHandler;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionLogListQueryHandler;
import com.bone.integration.application.query.qry.ExecutionLogListQry;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.service.FlowMonitorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorControllerTest {

    @Mock
    private ExecuteFlowHandler executeFlowHandler;

    @Mock
    private ExecutionLogListQueryHandler executionLogListQueryHandler;

    @Mock
    private IntegrationLogRepository logRepository;

    @Mock
    private IntegrationFlowRepository flowRepository;

    @Mock
    private FlowMonitorService flowMonitorService;

    @InjectMocks
    private MonitorController monitorController;

    @Test
    void execute_delegatesToHandler() {
        ExecuteFlowCmd cmd = new ExecuteFlowCmd(1L, "{\"k\":\"v\"}");
        when(executeFlowHandler.handle(cmd)).thenReturn(99L);

        var response = monitorController.execute(cmd);

        assertTrue(response.isSuccess());
        assertEquals(99L, response.getData());
    }

    @Test
    void listExecutions_returnsPage() {
        ExecutionLogListQry qry = new ExecutionLogListQry(1, 10, 1L, null);
        PageResult<ExecutionLogDTO> page =
                PageResult.of(Collections.emptyList(), 0L, 1, 10);
        when(executionLogListQueryHandler.handle(qry)).thenReturn(page);

        var response = monitorController.listExecutions(qry);

        assertTrue(response.isSuccess());
        assertEquals(page, response.getData());
    }

    @Test
    void getExecution_mapsDomainToDto() {
        IntegrationLog log = IntegrationLog.create(1L, 2L, "{}");
        log.start();
        log.complete("ok");
        when(logRepository.findById(1L)).thenReturn(log);

        var response = monitorController.getExecution(1L);

        assertTrue(response.isSuccess());
        assertEquals(1L, response.getData().id());
        assertEquals(2L, response.getData().flowId());
        assertEquals(ExecutionStatus.SUCCESS.name(), response.getData().status());
    }

    @Test
    void getStatistics_aggregatesByFlowId() {
        IntegrationFlow flow = IntegrationFlow.create(10L, "demo", "desc");
        flow.activate();
        when(flowRepository.findById(10L)).thenReturn(flow);
        when(flowMonitorService.getExecutionCount(10L)).thenReturn(5L);
        when(flowMonitorService.getSuccessCount(10L)).thenReturn(4L);
        when(flowMonitorService.getFailureCount(10L)).thenReturn(1L);
        when(flowMonitorService.getSuccessRate(10L)).thenReturn(80.0);

        var response = monitorController.getStatistics(10L);

        assertTrue(response.isSuccess());
        FlowStatisticsDTO dto = response.getData();
        assertEquals(10L, dto.flowId());
        assertEquals(5L, dto.executionCount());
        verify(flowMonitorService).getSuccessRate(eq(10L));
    }
}
