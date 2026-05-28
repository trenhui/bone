package com.bone.system.adapter.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.system.application.query.handler.ConsoleOverviewQueryHandler;
import com.bone.system.application.query.handler.QuickActionsQueryHandler;
import com.bone.system.domain.model.console.ConsoleOverview;
import com.bone.system.domain.model.console.KeyMetrics;
import com.bone.system.domain.model.console.QuickAction;
import com.bone.system.domain.model.console.ResourceUsage;
import com.bone.system.domain.model.console.ServiceStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web 切片测试：仅校验 Controller → QueryHandler 编排与响应包装；
 * 业务计数由 Gateway 单测覆盖，避免重复。
 */
@ExtendWith(MockitoExtension.class)
class ConsoleControllerTest {

    @Mock
    private ConsoleOverviewQueryHandler consoleOverviewQueryHandler;

    @Mock
    private QuickActionsQueryHandler quickActionsQueryHandler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsoleController(consoleOverviewQueryHandler, quickActionsQueryHandler))
                .build();
    }

    @Test
    void overview() throws Exception {
        when(consoleOverviewQueryHandler.handle()).thenReturn(ConsoleOverview.builder()
                .services(List.of(ServiceStatus.builder().name("IAM").serviceCode("bone-iam").port("8081").status("UP").latencyMs(0).build()))
                .resourceUsage(ResourceUsage.builder().memoryUsedBytes(123).memoryMaxBytes(456).updatedAt(Instant.now()).build())
                .keyMetrics(KeyMetrics.builder().userCount(1).entityCount(2).integrationFlowCount(3).extensionPluginCount(4).updatedAt(Instant.now()).build())
                .alerts(List.of())
                .updatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/console/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.services[0].serviceCode").value("bone-iam"))
                .andExpect(jsonPath("$.data.keyMetrics.userCount").value(1))
                .andExpect(jsonPath("$.data.resourceUsage.memoryUsedBytes").value(123));
    }

    @Test
    void services() throws Exception {
        when(consoleOverviewQueryHandler.handleServices()).thenReturn(List.of(
                ServiceStatus.builder().name("系统管理").serviceCode("bone-system").port("8083").status("UP").latencyMs(0).build()));

        mockMvc.perform(get("/api/v1/console/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serviceCode").value("bone-system"));
    }

    @Test
    void resources() throws Exception {
        when(consoleOverviewQueryHandler.handleResources()).thenReturn(
                ResourceUsage.builder().memoryUsedBytes(999).memoryMaxBytes(1000).updatedAt(Instant.now()).build());

        mockMvc.perform(get("/api/v1/console/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memoryUsedBytes").value(999));
    }

    @Test
    void metrics() throws Exception {
        when(consoleOverviewQueryHandler.handleMetrics()).thenReturn(
                KeyMetrics.builder().userCount(5).jvmThreadsLive(7).updatedAt(Instant.now()).build());

        mockMvc.perform(get("/api/v1/console/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCount").value(5))
                .andExpect(jsonPath("$.data.jvmThreadsLive").value(7));
    }

    @Test
    void quickActions() throws Exception {
        when(quickActionsQueryHandler.handle()).thenReturn(List.of(
                QuickAction.builder().id("iam").title("账号权限管理").path("/iam").icon("UserOutlined").build()));

        mockMvc.perform(get("/api/v1/console/quick-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value("/iam"));
    }
}
