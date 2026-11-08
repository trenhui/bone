package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.InspectionApplicationService.SampleOutcome;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionDimension;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.claim.domain.model.inspection.InspectionType;
import com.bone.claim.domain.repository.InspectionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 后督抽检应用服务测试：前置校验、幂等、四类抽样结果可区分、结论冲突语义。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionApplicationServiceTest {

  private static final long TENANT = 7L;
  private static final long CLAIM = 11L;

  @Mock private InspectionRepository inspectionRepository;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;

  private InspectionApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new InspectionApplicationService(
            inspectionRepository, claimCaseApplicationService, tenantPort, domainEventPublisher);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);
  }

  @Test
  @DisplayName("人工发起抽检：已结案 → 建单，单号确定性生成，分层层从案件金额/渠道派生")
  void openManuallyBuildsRecord() {
    ClaimCase settled = claim(TENANT, true);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(settled);

    InspectionApplicationService.InspectionView view =
        service.openManually(CLAIM, InspectionDimension.DOCUMENT, "qc-1");

    ArgumentCaptor<InspectionRecord> captor = ArgumentCaptor.forClass(InspectionRecord.class);
    verify(inspectionRepository).insert(captor.capture());
    InspectionRecord inserted = captor.getValue();
    assertThat(inserted.getInspectionNo()).isEqualTo("QI" + TENANT + CLAIM);
    assertThat(inserted.getInspectType()).isEqualTo(InspectionType.MANUAL.name());
    assertThat(inserted.getDimension()).isEqualTo(InspectionDimension.DOCUMENT.name());
    assertThat(inserted.getSampleStratum()).isEqualTo("500_1000#WECHAT");
    assertThat(view.status()).isEqualTo("PENDING");
    verify(domainEventPublisher).publishFrom(inserted);
  }

  @Test
  @DisplayName("人工发起抽检：案件未终态 → 409（后督只针对已结案/已拒赔案件）")
  void openManuallyRejectsUnsettledClaim() {
    ClaimCase unsettled = claim(TENANT, false);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(unsettled);

    BizException ex =
        catchThrowableOfType(() -> service.openManually(CLAIM, null, "qc-1"), BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.CLAIM_STATUS_CONFLICT);
    verify(inspectionRepository, never()).insert(any());
  }

  @Test
  @DisplayName("人工发起抽检：同案已有抽检单 → 幂等返回原单，不新建")
  void openManuallyIsIdempotentPerClaim() {
    ClaimCase settled = claim(TENANT, true);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(settled);
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(existing());

    InspectionApplicationService.InspectionView view =
        service.openManually(CLAIM, InspectionDimension.AMOUNT, "qc-1");

    assertThat(view.inspectionNo()).isEqualTo("QI" + TENANT + CLAIM);
    verify(inspectionRepository, never()).insert(any());
  }

  @Test
  @DisplayName("自动抽样：终态 + 命中比例 + 未抽检 → SAMPLED；四类结果互斥可区分")
  void sampleIfEligibleOutcomes() {
    ClaimCase settled = claim(TENANT, true);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(settled);

    assertThat(service.sampleIfEligible(CLAIM, 100)).isEqualTo(SampleOutcome.SAMPLED);

    // 未命中比例
    assertThat(service.sampleIfEligible(CLAIM, 0)).isEqualTo(SampleOutcome.NOT_SAMPLED);

    // 已抽检
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(existing());
    assertThat(service.sampleIfEligible(CLAIM, 100)).isEqualTo(SampleOutcome.ALREADY_INSPECTED);
  }

  @Test
  @DisplayName("自动抽样：非终态 / 租户不一致 → NOT_ELIGIBLE（不跨租户建单）")
  void sampleIfEligibleGuards() {
    ClaimCase unsettled = claim(TENANT, false);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(unsettled);
    assertThat(service.sampleIfEligible(CLAIM, 100)).isEqualTo(SampleOutcome.NOT_ELIGIBLE);

    ClaimCase otherTenant = claim(8L, true);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(otherTenant);
    assertThat(service.sampleIfEligible(CLAIM, 100)).isEqualTo(SampleOutcome.NOT_ELIGIBLE);
    verify(inspectionRepository, never()).insert(any());
  }

  @Test
  @DisplayName("结论：已结论再提交 → 409（状态冲突语义，不是 400）")
  void concludeTwiceRejectedAsConflict() {
    InspectionRecord concluded = existing();
    concluded.conclude(InspectionResult.PASS, null, null, null, "", "qc-1");
    when(inspectionRepository.findByInspectionNo("QI" + TENANT + CLAIM)).thenReturn(concluded);

    BizException ex =
        catchThrowableOfType(
            () ->
                service.conclude(
                    "QI" + TENANT + CLAIM, InspectionResult.PASS, null, null, null, "", "qc-2"),
            BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.CLAIM_INSPECTION_CONFLICT);
  }

  @Test
  @DisplayName("结论：单号不存在 → 404")
  void concludeUnknownInspection() {
    when(inspectionRepository.findByInspectionNo("QI-x")).thenReturn(null);
    BizException ex =
        catchThrowableOfType(
            () -> service.conclude("QI-x", InspectionResult.PASS, null, null, null, "", "qc-1"),
            BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.CLAIM_INSPECTION_NOT_FOUND);
  }

  @Test
  @DisplayName("结论：DIFF 落库并发布事件（差异可被下游订阅）")
  void concludeDiffPublishesEvent() {
    InspectionRecord pending = existing();
    when(inspectionRepository.findByInspectionNo("QI" + TENANT + CLAIM)).thenReturn(pending);

    InspectionApplicationService.InspectionView view =
        service.conclude(
            "QI" + TENANT + CLAIM,
            InspectionResult.DIFF,
            InspectionDiffType.ADJUST_MISMATCH,
            "{\"approvedAmount\":\"120.00->98.00\"}",
            InspectionAction.RECALCULATE,
            "ADJUSTING",
            "qc-1");

    assertThat(view.status()).isEqualTo("CONCLUDED");
    assertThat(view.result()).isEqualTo("DIFF");
    assertThat(view.actionTaken()).isEqualTo("RECALCULATE");
    verify(inspectionRepository).save(pending);
    verify(domainEventPublisher).publishFrom(pending);
  }

  private static InspectionRecord existing() {
    return InspectionRecord.open(
        99L,
        TENANT,
        "QI" + TENANT + CLAIM,
        CLAIM,
        "CL20261028001",
        InspectionDimension.AMOUNT,
        "500_1000#WECHAT",
        InspectionType.AUTO);
  }

  private static ClaimCase claim(long tenantId, boolean finished) {
    ClaimCase claim = org.mockito.Mockito.mock(ClaimCase.class);
    when(claim.getId()).thenReturn(CLAIM);
    when(claim.getTenantId()).thenReturn(tenantId);
    when(claim.getClaimNo()).thenReturn("CL20261028001");
    when(claim.getClaimAmount()).thenReturn(new BigDecimal("800.00"));
    when(claim.getSourceChannel()).thenReturn("wechat");
    when(claim.finished()).thenReturn(finished);
    return claim;
  }
}
