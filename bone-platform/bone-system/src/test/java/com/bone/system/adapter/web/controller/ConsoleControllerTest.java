package com.bone.system.adapter.web.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsoleControllerTest {

    @Mock
    private HealthEndpoint healthEndpoint;

    private MeterRegistry meterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ConsoleController(healthEndpoint, meterRegistry))
                        .build();
        when(healthEndpoint.health()).thenReturn(Health.up().build());
    }

    @Test
    void overview() throws Exception {
        mockMvc.perform(get("/api/v1/console/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.services").isArray())
                .andExpect(jsonPath("$.data.resourceUsage").exists())
                .andExpect(jsonPath("$.data.keyMetrics").exists());
    }

    @Test
    void services() throws Exception {
        mockMvc.perform(get("/api/v1/console/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    @Test
    void resources() throws Exception {
        mockMvc.perform(get("/api/v1/console/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.memoryUsedBytes").exists());
    }

    @Test
    void metrics() throws Exception {
        mockMvc.perform(get("/api/v1/console/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jvmThreadsLive").exists());
    }

    @Test
    void quickActions() throws Exception {
        mockMvc.perform(get("/api/v1/console/quick-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].path").value("/iam"));
    }
}
