package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aiorch.common.AiOrchErrorCodes;
import com.bone.aiorch.domain.hitl.HitlDisposition;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.core.exception.BizException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HitlApplicationService：HITL 五态流转 + 处置链落库回放 + 受控回流约束 + 稳定错误码。 */
class HitlServiceTest {

  private HitlItemRepository repository;
  private HitlDispositionRepository dispositionRepository;
  private HitlApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(HitlItemRepository.class);
    dispositionRepository = mock(HitlDispositionRepository.class);
    service = new HitlApplicationService(repository, dispositionRepository);
  }

  private HitlItem pendingItem() {
    HitlItem item =
        HitlItem.create("1", "procuraAI-approval", "建议通过", "15000.0", "HIGH", 0.85, "trace-1");
    item.setId("hitl-1");
    when(repository.findPendingById("1", "hitl-1")).thenReturn(Optional.of(item));
    return item;
  }

  @Test
  void create_starts_pending_and_keeps_trace_link() {
    HitlItem item = service.create("1", "approval", "建议", "100.0", "MEDIUM", 0.9, "trace-9");
    assertThat(item.getStatus()).isEqualTo(HitlStatus.PENDING);
    assertThat(item.getTenantId()).isEqualTo("1");
    assertThat(item.getTraceId()).isEqualTo("trace-9");
    verify(repository).save(item);
  }

  @Test
  void confirm_moves_to_confirmed_and_persists_disposition() {
    HitlItem item = pendingItem();
    HitlItem done = service.confirm("1", "hitl-1", "ops-1", ReasonType.CONTEXT_MISS);
    assertThat(done.getStatus()).isEqualTo(HitlStatus.CONFIRMED);
    assertThat(done.getActedBy()).isEqualTo("ops-1");
    assertThat(done.dispositionHistory()).hasSize(1);
    assertThat(done.dispositionHistory().get(0).action()).isEqualTo("confirm");
    assertThat(done.dispositionHistory().get(0).reasonType()).isEqualTo("CONTEXT_MISS");
    // 合规留痕：处置链必须落库（不能只存进程内）
    verify(dispositionRepository).insert(any(HitlDisposition.class));
  }

  @Test
  void reject_requires_reason_type_with_stable_error_code() {
    HitlItem item = pendingItem();
    assertThatThrownBy(() -> service.reject("1", "hitl-1", "ops-1", null, "no"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("reason_type")
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiOrchErrorCodes.REASON_REQUIRED));
    assertThat(item.getStatus()).isEqualTo(HitlStatus.PENDING);
    verify(dispositionRepository, never()).insert(any(HitlDisposition.class));
  }

  @Test
  void reject_with_reason_moves_to_rejected() {
    HitlItem item = pendingItem();
    HitlItem done = service.reject("1", "hitl-1", "ops-1", ReasonType.RULE_MISS, "规则漏判");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.REJECTED);
    assertThat(done.getReasonType()).isEqualTo("RULE_MISS");
    assertThat(done.dispositionHistory().get(0).comment()).isEqualTo("规则漏判");
  }

  @Test
  void modify_updates_value_and_requires_reason() {
    HitlItem item = pendingItem();
    assertThatThrownBy(() -> service.modify("1", "hitl-1", "ops-1", "20000.0", null, ""))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiOrchErrorCodes.REASON_REQUIRED));

    HitlItem done =
        service.modify("1", "hitl-1", "ops-1", "20000.0", ReasonType.SUGGESTION_WRONG, "金额偏小");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.MODIFIED);
    assertThat(done.getSuggestedValue()).isEqualTo("20000.0");
  }

  @Test
  void escalate_moves_to_escalated_and_rejects_further_actions() {
    HitlItem item = pendingItem();
    HitlItem done = service.escalate("1", "hitl-1", "ops-1", "超时转人工全程");
    assertThat(done.getStatus()).isEqualTo(HitlStatus.ESCALATED);

    // 终态不可再裁决（稳定码 AI_HITL_ALREADY_DECIDED）
    assertThatThrownBy(() -> service.confirm("1", "hitl-1", "ops-2", null))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiOrchErrorCodes.ALREADY_DECIDED));
  }

  @Test
  void disposition_history_replays_from_persistence_not_memory() {
    HitlItem item = pendingItem();
    when(repository.findById("hitl-1")).thenReturn(item);
    when(dispositionRepository.findByItemId("hitl-1"))
        .thenReturn(
            List.of(
                HitlDisposition.of(
                    1L,
                    "hitl-1",
                    "confirm",
                    "RULE_MISS",
                    null,
                    "备注",
                    "ops-1",
                    java.time.LocalDateTime.now())));
    service.confirm("1", "hitl-1", "ops-1", ReasonType.RULE_MISS);
    List<Disposition> history = service.dispositionHistory("1", "hitl-1");
    assertThat(history).hasSize(1);
    assertThat(history.get(0).action()).isEqualTo("confirm");
    assertThat(history.get(0).actor()).isEqualTo("ops-1");
    assertThat(history.get(0).reasonType()).isEqualTo("RULE_MISS");
  }

  @Test
  void disposition_history_unknown_item_fails_with_stable_code() {
    assertThatThrownBy(() -> service.dispositionHistory("1", "missing"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiOrchErrorCodes.NOT_FOUND));
  }

  @Test
  void find_pending_delegates_with_filters() {
    service.findPending("1", "procuraAI-approval", "HIGH");
    verify(repository).findPending("1", "procuraAI-approval", "HIGH");
  }
}
