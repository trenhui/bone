package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.ExecuteFlowCmd;
import com.bone.integration.application.command.handler.ExecuteFlowHandler;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.handler.ExecutionLogListQueryHandler;
import com.bone.integration.application.query.qry.ExecutionLogListQry;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(monitorController).build();
    }

    @Test
    void testExecute() throws Exception {
        ExecuteFlowCmd cmd = new ExecuteFlowCmd();
        cmd.setFlowId(1L);
        cmd.setInputData("{\"test\":\"data\"}");

        when(executeFlowHandler.handle(cmd)).thenReturn(1L);

        mockMvc.perform(MockMvcRequestBuilders.post("/integration/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flowId\":1,\"inputData\":\"{\\\"test\\\":\\\"data\\\"}\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(1));
    }

    @Test
    void testListExecutions() throws Exception {
        ExecutionLogListQry qry = new ExecutionLogListQry();
        PageResult<ExecutionLogDTO> result = PageResult.of(0L, 1, 10, java.util.Collections.emptyList());
        when(executionLogListQueryHandler.handle(qry)).thenReturn(result);

        mockMvc.perform(MockMvcRequestBuilders.get("/integration/executions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.items").isArray());
    }

    @Test
    void testGetExecution() throws Exception {
        Long id = 1L;
        IntegrationLog log = new IntegrationLog();
        log.setId(new com.bone.core.model.Identity(id));
        log.setFlowId(new com.bone.core.model.Identity(1L));
        log.setStatus(IntegrationLog.Status.SUCCESS);
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());
        log.setInputData("{\"test\":\"data\"}");
        log.setOutputData("{\"result\":\"success\"}");

        when(logRepository.findById(id)).thenReturn(Optional.of(log));

        mockMvc.perform(MockMvcRequestBuilders.get("/integration/executions/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.id").value(id));
    }

    @Test
    void testRetry() throws Exception {
        Long id = 1L;
        IntegrationLog log = new IntegrationLog();
        log.setId(new com.bone.core.model.Identity(id));
        log.setFlowId(new com.bone.core.model.Identity(1L));
        log.setInputData("{\"test\":\"data\"}");

        when(logRepository.findById(id)).thenReturn(Optional.of(log));
        when(executeFlowHandler.handle(any(ExecuteFlowCmd.class))).thenReturn(2L);

        mockMvc.perform(MockMvcRequestBuilders.post("/integration/executions/{id}/retry", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(2));
    }

    @Test
    void testGetStatistics() throws Exception {
        Long flowId = 1L;
        IntegrationFlow flow = new IntegrationFlow();
        flow.setId(new com.bone.core.model.Identity(flowId));
        flow.setName("测试流程");

        when(flowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(flowMonitorService.getExecutionCount(any())).thenReturn(100L);
        when(flowMonitorService.getSuccessCount(any())).thenReturn(90L);
        when(flowMonitorService.getFailureCount(any())).thenReturn(10L);
        when(flowMonitorService.getSuccessRate(any())).thenReturn(90.0);

        mockMvc.perform(MockMvcRequestBuilders.get("/integration/statistics")
                .param("flowId", flowId.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.flowId").value(flowId));
    }
}
