package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.reconciliation.DiffType;
import com.bone.claim.domain.model.reconciliation.ReconciliationAction;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.repository.ReconciliationActionRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 对账差异处置：单向一次、结论受限、每次动作都落流水。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationDispositionServiceTest {

  private static final long TENANT = 9L;
  private static final long DIFF_ID = 7001L;

  @Mock private ReconciliationDiffRepository diffRepository;
  @Mock private ReconciliationActionRepository actionRepository;
  @Mock private TenantPort tenantPort;

  private ReconciliationDispositionService service;

  @BeforeEach
  void setUp() {
    service = new ReconciliationDispositionService(diffRepository, actionRepository, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
  }

  @Test
  @DisplayName("处置一条待处置差异 ⇒ 差异行落终态 + 追加一条 DIFF_RESOLVE 流水（说明进 detail）")
  void resolvesAndRecordsAction() {
    ReconciliationDiff diff = pendingDiff();
    when(diffRepository.findDiff(DIFF_ID)).thenReturn(diff);

    ReconciliationDispositionService.Disposition result =
        service.resolveDiff(DIFF_ID, ReconciliationDiff.RESOLUTION_WAIVED, "商户已确认重复单", "ops-1");

    assertThat(result.resolution()).isEqualTo(ReconciliationDiff.RESOLUTION_WAIVED);
    assertThat(result.diffId()).isEqualTo(DIFF_ID);
    assertThat(diff.isPending()).isFalse();
    assertThat(diff.getResolvedBy()).isEqualTo("ops-1");
    verify(diffRepository).save(diff);

    ArgumentCaptor<ReconciliationAction> captor =
        ArgumentCaptor.forClass(ReconciliationAction.class);
    verify(actionRepository).save(captor.capture());
    ReconciliationAction action = captor.getValue();
    assertThat(action.getTargetType()).isEqualTo(ReconciliationAction.TARGET_DIFF);
    assertThat(action.getTargetId()).isEqualTo(DIFF_ID);
    assertThat(action.getActionType()).isEqualTo(ReconciliationAction.ACTION_DIFF_RESOLVE);
    assertThat(action.getResolution()).isEqualTo(ReconciliationDiff.RESOLUTION_WAIVED);
    assertThat(action.getDetail()).isEqualTo("商户已确认重复单");
    assertThat(action.getOperatorId()).isEqualTo("ops-1");
    assertThat(action.getTenantId()).isEqualTo(TENANT);
  }

  @Test
  @DisplayName("差异不存在 ⇒ 404 语义，且不写任何流水")
  void diffNotFound() {
    when(diffRepository.findDiff(DIFF_ID)).thenReturn(null);

    assertThatThrownBy(
            () ->
                service.resolveDiff(DIFF_ID, ReconciliationDiff.RESOLUTION_RESOLVED, null, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_RECON_DIFF_NOT_FOUND");
    verify(actionRepository, never()).save(any());
  }

  @Test
  @DisplayName("已处置再处置 ⇒ 409（不静默幂等：三种结论对应三种后续动作，覆盖会误导调用方）")
  void alreadyResolvedConflicts() {
    ReconciliationDiff diff = pendingDiff();
    diff.resolve(ReconciliationDiff.RESOLUTION_RESOLVED, "ops-0");
    when(diffRepository.findDiff(DIFF_ID)).thenReturn(diff);

    assertThatThrownBy(
            () -> service.resolveDiff(DIFF_ID, ReconciliationDiff.RESOLUTION_WAIVED, null, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_RECON_DIFF_CONFLICT");
    verify(diffRepository, never()).save(any());
    verify(actionRepository, never()).save(any());
  }

  @Test
  @DisplayName("处置结论非法（含把 PENDING 当结论写回）⇒ 409")
  void illegalResolution() {
    when(diffRepository.findDiff(DIFF_ID)).thenReturn(pendingDiff());

    assertThatThrownBy(
            () ->
                service.resolveDiff(DIFF_ID, ReconciliationDiff.RESOLUTION_PENDING, null, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_RECON_DIFF_CONFLICT");
    verify(actionRepository, never()).save(any());
  }

  @Test
  @DisplayName("待处置队列：limit 越界被钳（≤0 → 1，>500 → 500），不把全表查询暴露给运营页")
  void pendingClampsLimit() {
    service.pending(0);
    verify(diffRepository).findPending(1);
    service.pending(9999);
    verify(diffRepository).findPending(500);
  }

  @Test
  @DisplayName("处置历史按流水映射（多条 = 被更正过；不读差异行）")
  void historyMapsActions() {
    Instant at = Instant.parse("2026-10-31T02:00:00Z");
    when(actionRepository.findByTarget(ReconciliationAction.TARGET_DIFF, DIFF_ID))
        .thenReturn(
            List.of(
                ReconciliationAction.diffResolved(
                    TENANT, DIFF_ID, ReconciliationDiff.RESOLUTION_WAIVED, "理由 A", "ops-1", at)));

    List<ReconciliationDispositionService.Disposition> history = service.history(DIFF_ID);

    assertThat(history).hasSize(1);
    assertThat(history.get(0).resolution()).isEqualTo(ReconciliationDiff.RESOLUTION_WAIVED);
    assertThat(history.get(0).note()).isEqualTo("理由 A");
    assertThat(history.get(0).operatorId()).isEqualTo("ops-1");
    assertThat(history.get(0).occurredAt()).isEqualTo(at);
    verify(diffRepository, never()).findDiff(anyLong());
    verify(diffRepository, never()).findPending(anyInt());
  }

  private static ReconciliationDiff pendingDiff() {
    return ReconciliationDiff.create(
        TENANT,
        88L,
        "C1",
        "SO1",
        DiffType.AMOUNT_MISMATCH,
        new BigDecimal("500.00"),
        new BigDecimal("400.00"),
        "PAID",
        "PAID");
  }
}
