package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.airules.domain.model.RuleEvalResult;
import com.bone.core.exception.GlobalExceptionHandler;
import com.bone.core.tenant.context.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 规则资产 REST 管理面：租户从上下文 + 跨租户 403 + 发布分流（无历史⇒首版 / 有历史⇒新版）。
 *
 * <p>「发布分流」是运营心智与实现细节的边界：控制器按版本链自动选 publishFirst / publishNext，测试钉死这个分流，
 * 防止回归成「运营必须知道发过没有」。
 */
class RuleControllerTest {

  private final RuleApplicationService ruleService = mock(RuleApplicationService.class);
  private final RuleQueryApplicationService ruleQuery = mock(RuleQueryApplicationService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RuleController(ruleService, ruleQuery, new AiTenantContext()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static RuleAsset rule(String id, String tenantId) {
    RuleAsset rule = RuleAsset.create(tenantId, "R01", "免责条款", "既往症");
    rule.setId(id);
    return rule;
  }

  @Test
  @DisplayName("生效列表：租户从上下文取")
  void active_rules_from_context_tenant() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findActive("1")).thenReturn(List.of(rule("r1", "1")));

    mockMvc
        .perform(get("/api/ai/v1/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].code").value("R01"));

    // 租户隔离断言走服务交互而非响应体：TenantAggregateRoot.getTenantId() 带 @JsonIgnore
    // （框架级决定，响应不暴露 tenantId），故 JSON 路径 $.data[0].tenantId 恒不存在——
    // 「租户从上下文取」的正确钉法是验证查询用了上下文租户（2026-10-23 修正）
    verify(ruleQuery).findActive("1");
  }

  @Test
  @DisplayName("详情：不存在 404")
  void by_id_not_found_404() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findById("r-x")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/ai/v1/rules/r-x")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("详情：跨租户 403")
  void by_id_rejects_cross_tenant_with_403() throws Exception {
    TenantContext.setTenantId("2");
    when(ruleQuery.findById("r1")).thenReturn(Optional.of(rule("r1", "1")));

    mockMvc.perform(get("/api/ai/v1/rules/r1")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("发布分流：无版本历史 ⇒ publishFirst")
  void publish_first_when_no_history() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findById("r1")).thenReturn(Optional.of(rule("r1", "1")));
    when(ruleService.history("r1")).thenReturn(List.of());
    when(ruleService.publishFirst("r1", "expr", true, 0, "ops-1"))
        .thenReturn(mock(RuleAssetVersion.class));

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/publish")
                .contentType("application/json")
                .content(
                    "{\"expression\":\"expr\",\"hitBlock\":true,\"grayPercent\":0,\"operator\":\"ops-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(ruleService).publishFirst("r1", "expr", true, 0, "ops-1");
    verify(ruleService, never())
        .publishNext(any(), any(), anyBoolean(), anyInt(), any());
  }

  @Test
  @DisplayName("发布分流：已有版本历史 ⇒ publishNext")
  void publish_next_when_history_exists() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findById("r1")).thenReturn(Optional.of(rule("r1", "1")));
    when(ruleService.history("r1")).thenReturn(List.of(mock(RuleAssetVersion.class)));
    when(ruleService.publishNext("r1", "expr2", false, 20, "ops-1"))
        .thenReturn(mock(RuleAssetVersion.class));

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/publish")
                .contentType("application/json")
                .content(
                    "{\"expression\":\"expr2\",\"hitBlock\":false,\"grayPercent\":20,\"operator\":\"ops-1\"}"))
        .andExpect(status().isOk());

    verify(ruleService).publishNext("r1", "expr2", false, 20, "ops-1");
  }

  @Test
  @DisplayName("灰度 / 命中标记 / 废弃 / 回滚：委托服务层并做租户校验")
  void governance_actions_delegate_with_tenant_check() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findById("r1")).thenReturn(Optional.of(rule("r1", "1")));
    when(ruleService.rollback(eq("r1"), eq(2), eq("ops-1")))
        .thenReturn(mock(RuleAssetVersion.class));

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/rollback")
                .contentType("application/json")
                .content("{\"targetVersionNo\":2,\"operator\":\"ops-1\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/gray")
                .contentType("application/json")
                .content("{\"grayPercent\":50}"))
        .andExpect(status().isOk());
    verify(ruleService).setGrayPercent("r1", 50);

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/hit-block")
                .contentType("application/json")
                .content("{\"hitBlock\":true}"))
        .andExpect(status().isOk());
    verify(ruleService).setHitBlock("r1", true);

    mockMvc.perform(post("/api/ai/v1/rules/r1/deprecate")).andExpect(status().isOk());
    verify(ruleService).deprecate("r1");
  }

  @Test
  @DisplayName("试算求值：上下文透传服务层（不落状态）")
  void evaluate_passes_context_through() throws Exception {
    TenantContext.setTenantId("1");
    when(ruleQuery.findById("r1")).thenReturn(Optional.of(rule("r1", "1")));
    when(ruleService.evaluate(eq("r1"), any(), eq(true)))
        .thenReturn(mock(RuleEvalResult.class));

    mockMvc
        .perform(
            post("/api/ai/v1/rules/r1/evaluate")
                .contentType("application/json")
                .content("{\"context\":{\"amount\":30000},\"trace\":true}"))
        .andExpect(status().isOk());

    verify(ruleService).evaluate(eq("r1"), any(), eq(true));
  }
}
