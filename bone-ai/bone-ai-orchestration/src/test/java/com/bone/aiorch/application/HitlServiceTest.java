package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HitlService：HITL 五态流转 + 处置链回放 + 受控回流约束。 */
class HitlServiceTest {

  private HitlItemRepository repository;
  private HitlService service;

  @BeforeEach
  void setUp() {
    repository = mock(HitlItemRepository.class);
    service = new HitlService(repository);
  }

  private HitlItem pendingItem() {
    HitlItem item = HitlItem.create("t1", "procuraAI-approval", "建议通过", 15000.0, "HIGH", 0.85);
    item.setId("hitl-1");
    when(repository.findPendingById("t1", "hitl-1")).thenReturn(Optional.of(item));
    return item;
  }

  @Test
  void create_starts_pending() {
    HitlItem item = service.create("t1", "approval", "建议", 100.0, "MEDIUM", 0.9);
    assertThat(item.getStatus()).isEqualTo(HitlStatus.PENDING);
    assertThat(item.getTenantId()).isEqualTo("t1");
  }

  @Test
  void confirm_moves_to_confirmed_with_history() {
    HitlItem item = pendingItem();
    HitlItem done = service.confirm("t1", "hitl-1", "ops-1", ReasonType.CONTEXT_MISS);
    assertThat(done.getStatus()).isEqualTo(HitlStatus.CONFIRMED);
    assertThat(done.getActedBy()).isEqualTo("ops-1");
    assertThat(done.dispositionHistory()).hasSize(1);
    assertThat(done.dispositionHistory().get(0).action()).isEqualTo("confirm");
    assertThat(done.dispositionHistory().get(0).reasonType()).isEqualTo("CONTEXT_MISS");
  }

  @Test
  void reject_requires_reason_type() {
    HitlItem item = pendingItem();
    assertThatThrownBy(() -> service.reject("t1", "hitl-1", "ops-1", null, "no"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason_type");
    assertThat(item.getStatus()).isEqualTo(HitlStatus.PENDING);
  }

  @Test
  void reject_with_reason_moves_to_rejected() {
    HitlItem item = pendingItem();
    HitlItem done = service.reject("t1", "hitl-1", "ops-1", ReasonType.RULE_MISS, "规则漏判");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.REJECTED);
    assertThat(done.getReasonType()).isEqualTo("RULE_MISS");
    assertThat(done.dispositionHistory().get(0).comment()).isEqualTo("规则漏判");
  }

  @Test
  void modify_updates_value_and_requires_reason() {
    HitlItem item = pendingItem();
    assertThatThrownBy(() -> service.modify("t1", "hitl-1", "ops-1", 20000.0, null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason_type");

    HitlItem done =
        service.modify("t1", "hitl-1", "ops-1", 20000.0, ReasonType.SUGGESTION_WRONG, "金额偏小");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.MODIFIED);
    assertThat(done.getSuggestedValue()).isEqualTo(20000.0);
  }

  @Test
  void escalate_moves_to_escalated_and_rejects_further_actions() {
    HitlItem item = pendingItem();
    HitlItem done = service.escalate("t1", "hitl-1", "ops-1", "超时转人工全程");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.ESCALATED);

    // 终态不可再裁决
    assertThatThrownBy(() -> service.confirm("t1", "hitl-1", "ops-2", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resolved");
  }

  @Test
  void disposition_history_replays_chain() {
    HitlItem item = pendingItem();
    when(repository.findById("hitl-1")).thenReturn(item); // dispositionHistory 走 findById
    service.confirm("t1", "hitl-1", "ops-1", ReasonType.RULE_MISS);
    var history = service.dispositionHistory("t1", "hitl-1");
    assertThat(history).hasSize(1);
    assertThat(history.get(0).action()).isEqualTo("confirm");
    assertThat(history.get(0).actor()).isEqualTo("ops-1");
    assertThat(history.get(0).reasonType()).isEqualTo("RULE_MISS");
  }

  @Test
  void find_pending_delegates_with_filters() {
    service.findPending("t1", "procuraAI-approval", "HIGH");
    org.mockito.Mockito.verify(repository).findPending("t1", "procuraAI-approval", "HIGH");
  }
}
