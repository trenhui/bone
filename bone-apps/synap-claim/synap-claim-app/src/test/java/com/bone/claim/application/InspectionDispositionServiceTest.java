package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.inspection.ClaimInspectionAction;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.claim.domain.repository.ClaimInspectionActionRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.exception.BizException;
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

/** 抽检处置判据钉子（批 4 任务 2.1 / 2.2 / 4.3）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionDispositionServiceTest {

  private static final long TENANT = 42L;
  private static final long CLAIM_ID = 1001L;
  private static final String INSPECTOR = "insp-1";

  @Mock private ClaimInspectionActionRepository actionRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private ClaimTaskApplicationService claimTaskApplicationService;
  @Mock private FeatureTenantToggleService featureTenantToggleService;
  @Mock private TenantPort tenantPort;
  @Mock private SettlementOrder paidOrder;

  private InspectionDispositionService service;

  @BeforeEach
  void setUp() {
    service =
        new InspectionDispositionService(
            actionRepository,
            settlementRepository,
            claimTaskApplicationService,
            featureTenantToggleService,
            tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
  }

  @Test
  @DisplayName("2.2 已支付未冲正打回被拒：不落留痕、不生成重办任务")
  void paidWithoutReversalRejected() {
    when(featureTenantToggleService.isInspectionActionEnabled(TENANT, true)).thenReturn(true);
    when(settlementRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(paidOrder));
    when(paidOrder.fundingStageEnum()).thenReturn(FundingStage.PAID);

    assertThatThrownBy(() -> service.dispose(CLAIM_ID, "BACK", "金额有误", INSPECTOR))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ClaimErrorCodes.SC_CLAIM_REVERSAL_REQUIRED);
    verify(actionRepository, never()).insert(any());
    verify(claimTaskApplicationService, never()).openForClaim(anyLong(), any(TaskType.class));
  }

  @Test
  @DisplayName("2.1 未支付打回+开关开：生成重办任务并回填留痕 taskId")
  void unpaidBackGeneratesReworkTask() {
    when(featureTenantToggleService.isInspectionActionEnabled(TENANT, true)).thenReturn(true);
    when(settlementRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of());
    when(claimTaskApplicationService.openForClaim(CLAIM_ID, TaskType.INSPECTION))
        .thenReturn(
            new ClaimTaskApplicationService.MyTaskRow(
                "9001",
                "T9001",
                "1001",
                "INSPECTION",
                "ASSIGNED",
                100,
                null,
                null,
                null,
                null,
                null,
                null));

    var view = service.dispose(CLAIM_ID, "BACK", "材料不齐", INSPECTOR);

    assertThat(view.taskId()).isEqualTo("9001");
    assertThat(view.decision()).isEqualTo("BACK");
    assertThat(view.featureEnabled()).isTrue();
    ArgumentCaptor<ClaimInspectionAction> cap =
        ArgumentCaptor.forClass(ClaimInspectionAction.class);
    verify(actionRepository).insert(cap.capture());
    assertThat(cap.getValue().getTaskId()).isEqualTo(9001L);
    assertThat(cap.getValue().getReversalVerified()).isEqualTo(1);
  }

  @Test
  @DisplayName("4.3 开关关闭：只登记（featureEnabled=0），不生成任务、不改案件状态")
  void toggleOffRegistersOnly() {
    when(featureTenantToggleService.isInspectionActionEnabled(TENANT, true)).thenReturn(false);

    var view = service.dispose(CLAIM_ID, "BACK", "只登记测试", INSPECTOR);

    assertThat(view.featureEnabled()).isFalse();
    assertThat(view.taskId()).isNull();
    verify(claimTaskApplicationService, never()).openForClaim(anyLong(), any(TaskType.class));
    // 开关关闭时不做资金查询（根本不流转）
    verify(settlementRepository, never()).findByClaimId(anyLong());
    ArgumentCaptor<ClaimInspectionAction> cap =
        ArgumentCaptor.forClass(ClaimInspectionAction.class);
    verify(actionRepository).insert(cap.capture());
    assertThat(cap.getValue().getFeatureEnabled()).isEqualTo(0);
  }

  @Test
  @DisplayName("BACK 未给原因 → 400 入参非法，不落库")
  void backWithoutReasonRejected() {
    assertThatThrownBy(() -> service.dispose(CLAIM_ID, "BACK", "  ", INSPECTOR))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ClaimErrorCodes.SC_CLAIM_INSPECTION_ACTION_INVALID);
    verify(actionRepository, never()).insert(any());
  }
}
