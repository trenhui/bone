package com.bone.aiapp.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.core.exception.GlobalExceptionHandler;
import com.bone.core.tenant.context.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 证据链 REST 回放面：租户从上下文 + 跨租户显式拒绝 + 404 语义。
 *
 * <p>「跨租户命中返 403 而非空列表」是本控制器最要紧的行为：空列表会让「查不到」与「不许查」无法区分， 取证场景误判成本极高——测试把它钉死。
 */
class TraceControllerTest {

  private final EvidenceQueryApplicationService evidenceQuery =
      mock(EvidenceQueryApplicationService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TraceController(evidenceQuery, new AiTenantContext()))
            .setControllerAdvice(new GlobalExceptionHandler()) // 与生产装配一致：BizException → HTTP 状态码
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static EvidenceSnapshot snapshot(Long id, Long tenantId, String traceId) {
    return EvidenceSnapshot.record(
        id, tenantId, traceId, EvidenceType.LLM_CALL, "sha256:abc", "qwen-27b", "{}");
  }

  @Test
  @DisplayName("按 traceId 回放本租户证据（多条按序返回）")
  void replay_returns_snapshots_of_context_tenant() throws Exception {
    TenantContext.setTenantId("1");
    when(evidenceQuery.replayByTraceId("trace-1"))
        .thenReturn(List.of(snapshot(10L, 1L, "trace-1"), snapshot(11L, 1L, "trace-1")));

    mockMvc
        .perform(get("/api/ai/v1/trace/trace-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].traceId").value("trace-1"));
  }

  @Test
  @DisplayName("跨租户命中：403 显式拒绝（不是空列表）")
  void replay_rejects_cross_tenant_with_403() throws Exception {
    TenantContext.setTenantId("2"); // 证据属租户 1，当前上下文是租户 2
    when(evidenceQuery.replayByTraceId("trace-1"))
        .thenReturn(List.of(snapshot(10L, 1L, "trace-1")));

    mockMvc
        .perform(get("/api/ai/v1/trace/trace-1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("按 ID 取单条：本租户 200")
  void by_id_returns_own_snapshot() throws Exception {
    TenantContext.setTenantId("1");
    when(evidenceQuery.byId(10L)).thenReturn(snapshot(10L, 1L, "trace-1"));

    mockMvc
        .perform(get("/api/ai/v1/trace/item/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(10));
  }

  @Test
  @DisplayName("按 ID 取单条：不存在 404")
  void by_id_not_found_404() throws Exception {
    TenantContext.setTenantId("1");
    when(evidenceQuery.byId(99L)).thenReturn(null);

    mockMvc.perform(get("/api/ai/v1/trace/item/99")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("按 ID 取单条：跨租户 403")
  void by_id_rejects_cross_tenant_with_403() throws Exception {
    TenantContext.setTenantId("2");
    when(evidenceQuery.byId(10L)).thenReturn(snapshot(10L, 1L, "trace-1"));

    mockMvc.perform(get("/api/ai/v1/trace/item/10")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("租户上下文缺失：400 fail-closed（不接受匿名回放）")
  void missing_tenant_context_is_rejected() throws Exception {
    mockMvc.perform(get("/api/ai/v1/trace/trace-1")).andExpect(status().isBadRequest());
  }
}
