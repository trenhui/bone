package com.bone.aiapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PipelineControllerTest {

  private final OrchestrationApplicationService orchestration =
      mock(OrchestrationApplicationService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PipelineController(orchestration, new AiTenantContext()))
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void run_delegates_to_orchestration() throws Exception {
    TenantContext.setTenantId("1");
    when(orchestration.run(any())).thenReturn(mock(OrchestrationResult.class));
    mockMvc
        .perform(
            post("/api/ai/v1/pipeline")
                .contentType("application/json")
                .content("{\"query\":\"q\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void run_fails_closed_without_tenant_context() {
    // 未注入 TenantContext ⇒ 拒绝对外提供服务（禁入参传租户，见 AiTenantContext）
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/api/ai/v1/pipeline")
                        .contentType("application/json")
                        .content("{\"query\":\"q\"}")))
        .isInstanceOf(jakarta.servlet.ServletException.class)
        .hasRootCauseInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) ((Exception) e).getCause()).getErrorCode())
                    .isEqualTo(AiTenantContext.ERROR_TENANT_MISSING));
  }
}
