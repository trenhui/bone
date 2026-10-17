package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.eval.application.EvalMetrics;
import com.bone.aiapp.eval.application.EvalRunMetricsService;
import com.bone.aiapp.eval.gateway.JudgePort;
import com.bone.aiapp.eval.gateway.JudgeVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvalControllerTest {

  private final EvalRunMetricsService runMetricsService = mock(EvalRunMetricsService.class);
  private final JudgePort judgePort = mock(JudgePort.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EvalController(runMetricsService, judgePort)).build();
  }

  @Test
  void runs_returns_computed_metrics() throws Exception {
    when(runMetricsService.metricsForGate(any()))
        .thenReturn(new EvalMetrics(10, 8, 1, 1, 0, 0.89, 1.0, 0.94, 0.0, 0.5, 0.9, 1.0));
    mockMvc
        .perform(get("/api/ai/v1/eval/runs").param("gate", "rag-quality"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(10))
        .andExpect(jsonPath("$.data.recall").value(1.0));
  }

  @Test
  void judge_delegates_to_port() throws Exception {
    when(judgePort.judge(any())).thenReturn(new JudgeVerdict(0.9, true, "ok"));
    mockMvc
        .perform(
            post("/api/ai/v1/eval/judge")
                .contentType("application/json")
                .content(
                    "{\"system\":\"rag\",\"criterion\":\"引用正确\",\"input\":\"q\",\"expected\":\"e\","
                        + "\"actual\":\"a\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.passed").value(true))
        .andExpect(jsonPath("$.data.score").value(0.9));
  }
}
