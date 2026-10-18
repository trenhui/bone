package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.core.tenant.context.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HITL 人工终审 REST 面：租户从上下文 + 裁决委托 + 处置链回放。 */
class HitlControllerTest {

  private final HitlApplicationService hitlService = mock(HitlApplicationService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new HitlController(hitlService, new AiTenantContext()))
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private HitlItem pendingItem() {
    return HitlItem.create("1", "claim-C1", "建议核赔", "30000.0", "MEDIUM", 0.5, "trace-1");
  }

  @Test
  void items_lists_pending_from_context_tenant() throws Exception {
    TenantContext.setTenantId("1");
    HitlItem item = pendingItem();
    item.setId("hitl-1");
    when(hitlService.findPending("1", "claim-C1", null)).thenReturn(List.of(item));

    mockMvc
        .perform(get("/api/ai/v1/hitl/items").param("scene", "claim-C1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value("hitl-1"))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"));
  }

  @Test
  void act_reject_requires_reason_and_delegates() throws Exception {
    TenantContext.setTenantId("1");
    HitlItem item = pendingItem();
    item.setId("hitl-1");
    when(hitlService.reject(eq("1"), eq("hitl-1"), eq("ops-1"), eq(ReasonType.RULE_MISS), any()))
        .thenReturn(item);

    mockMvc
        .perform(
            post("/api/ai/v1/hitl/items/hitl-1/act")
                .contentType("application/json")
                .content(
                    "{\"action\":\"reject\",\"actor\":\"ops-1\",\"reasonType\":\"RULE_MISS\","
                        + "\"comment\":\"既往症漏判\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(hitlService).reject("1", "hitl-1", "ops-1", ReasonType.RULE_MISS, "既往症漏判");
  }

  @Test
  void dispositions_replay_from_persistence() throws Exception {
    TenantContext.setTenantId("1");
    when(hitlService.dispositionHistory("1", "hitl-1"))
        .thenReturn(
            List.of(
                new Disposition(
                    "reject", "RULE_MISS", null, "既往症漏判", "ops-1", LocalDateTime.now())));

    mockMvc
        .perform(get("/api/ai/v1/hitl/items/hitl-1/dispositions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].action").value("reject"))
        .andExpect(jsonPath("$.data[0].actor").value("ops-1"));
  }

  @Test
  void item_detail_returns_terminal_item_with_tenant_check() throws Exception {
    TenantContext.setTenantId("1");
    HitlItem item = pendingItem();
    item.setId("hitl-1");
    item.confirm("ops-1", ReasonType.CONTEXT_MISS);
    when(hitlService.findById("1", "hitl-1")).thenReturn(item);

    mockMvc
        .perform(get("/api/ai/v1/hitl/items/hitl-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value(HitlStatus.CONFIRMED.name()));
  }

  @Test
  void overdue_lists_items_with_default_sla() throws Exception {
    TenantContext.setTenantId("1");
    HitlItem item = pendingItem();
    item.setId("hitl-1");
    when(hitlService.findOverdue(eq("1"), eq(HitlApplicationService.DEFAULT_SLA)))
        .thenReturn(List.of(item));

    mockMvc
        .perform(get("/api/ai/v1/hitl/items/overdue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("hitl-1"));

    verify(hitlService).findOverdue("1", HitlApplicationService.DEFAULT_SLA);
  }

  @Test
  void overdue_honours_custom_sla_hours() throws Exception {
    TenantContext.setTenantId("1");
    when(hitlService.findOverdue(eq("1"), eq(java.time.Duration.ofHours(24))))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/ai/v1/hitl/items/overdue").param("slaHours", "24"))
        .andExpect(status().isOk());

    verify(hitlService).findOverdue("1", java.time.Duration.ofHours(24));
  }

  @Test
  void escalate_overdue_triggers_batch_and_returns_escalated() throws Exception {
    TenantContext.setTenantId("1");
    HitlItem item = pendingItem();
    item.setId("hitl-1");
    item.setStatus(HitlStatus.ESCALATED);
    when(hitlService.escalateOverdue("1", HitlApplicationService.DEFAULT_SLA))
        .thenReturn(List.of(item));

    mockMvc
        .perform(post("/api/ai/v1/hitl/items/escalate-overdue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value(HitlStatus.ESCALATED.name()));

    verify(hitlService).escalateOverdue("1", HitlApplicationService.DEFAULT_SLA);
  }
}
