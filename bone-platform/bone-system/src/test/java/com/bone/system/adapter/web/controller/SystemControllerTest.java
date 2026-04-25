package com.bone.system.adapter.web.controller;

import com.bone.system.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

class SystemControllerTest {

    @Mock
    private HealthEndpoint healthEndpoint;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private SystemController systemController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(systemController).build();
    }

    @Test
    void testHealth() throws Exception {
        org.springframework.boot.actuate.health.Health health = org.springframework.boot.actuate.health.Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/system/health"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
    }

    @Test
    void testInfo() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/system/info"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value("bone-system"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.description").value("Bone平台系统管理服务"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.version").value("1.0.0"));
    }

    @Test
    void testMetrics() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/system/metrics"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.jvm.memory.used").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.jvm.memory.max").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.jvm.threads.live").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.jvm.threads.daemon").exists());
    }
}
