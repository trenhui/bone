package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.application.OrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PipelineControllerTest {

  private final OrchestrationService orchestration = mock(OrchestrationService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new PipelineController(orchestration)).build();
  }

  @Test
  void run_delegates_to_orchestration() throws Exception {
    when(orchestration.run(any())).thenReturn(mock(OrchestrationResult.class));
    mockMvc
        .perform(
            post("/api/ai/v1/pipeline")
                .contentType("application/json")
                .content("{\"query\":\"q\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
