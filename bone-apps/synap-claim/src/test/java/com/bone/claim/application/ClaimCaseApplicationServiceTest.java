package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimItemRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ClaimCaseApplicationService 应用层单测（编排 + 幂等 + 乐观锁重试 + 保存必配发布）。 */
@ExtendWith(MockitoExtension.class)
class ClaimCaseApplicationServiceTest {

  private static final long TENANT_ID = 42L;
  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimItemRepository claimItemRepository;
  @Mock private PolicyQueryPort policyQueryPort;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @InjectMocks private ClaimCaseApplicationService service;

  private PolicyQueryPort.PolicySnapshot snapshot;

  @BeforeEach
  void setUp() {
    snapshot =
        new PolicyQueryPort.PolicySnapshot(
            "EG20260001",
            7L,
            "v3",
            java.util.List.of("hash-abc"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T23:59:59Z"));
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
  }

  private SubmitClaim command(String idempotencyKey) {
    return new SubmitClaim(
        idempotencyKey,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        null,
        null,
        null,
        OUT_INSURE,
        "B1",
        null,
        List.of(
            new SubmitClaim.ClaimItemDto(
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"),
                null,
                "")));
  }

  @Test
  @DisplayName("报案：保单生效 + 被保人匹配 → 受理签收 → insert + publishFrom")
  void submitSuccess() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-1")).thenReturn(null);

    SubmittedClaim claimId = service.submit(command("idem-1"));

    assertThat(claimId.claimId()).isNotNull();
    assertThat(claimId.claimNo()).startsWith("CL");
    verify(claimCaseRepository).insert(any(ClaimCase.class));
    verify(domainEventPublisher).publishFrom(any(ClaimCase.class));
  }

  @Test
  @DisplayName("报案落库：发票维度与明细科室必须落进聚合（只加列不写入 = 配了不生效）")
  void submitPersistsInvoiceContext() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-ctx")).thenReturn(null);

    SubmitClaim command =
        new SubmitClaim(
            "idem-ctx",
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "04400001",
            "12345678",
            java.time.LocalDate.of(2026, 3, 10),
            "H-001",
            "DX-01",
            OUT_INSURE,
            "B1",
            null,
            List.of(
                new SubmitClaim.ClaimItemDto(
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    new BigDecimal("10.00"),
                    "内科",
                    "")));

    service.submit(command);

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    ClaimCase saved = captor.getValue();
    assertThat(saved.getInvoiceDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 10));
    assertThat(saved.getHospitalCode()).isEqualTo("H-001");
    assertThat(saved.getDiagnosisCode()).isEqualTo("DX-01");
    assertThat(saved.singleInvoiceProvable()).as("有发票号才能证明『案内只有一张票』，这是发票级维度缺失时允许降级的唯一依据").isTrue();
    assertThat(saved.getItems())
        .as("科室是行级分组维度，必须随明细落库")
        .extracting(ClaimItem::getDepartment)
        .containsExactly("内科");
  }

  @Test
  @DisplayName("报案落库：同意记录号随案写入（只加列不写入 = 配了不生效）")
  void submitPersistsConsentRecordId() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-consent")).thenReturn(null);

    SubmitClaim command =
        new SubmitClaim(
            "idem-consent",
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "04400001",
            "12345678",
            null,
            null,
            null,
            OUT_INSURE,
            "B1",
            "CONSENT-2026-0001",
            List.of(
                new SubmitClaim.ClaimItemDto(
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    new BigDecimal("10.00"),
                    null,
                    "")));

    service.submit(command);

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    // 只加列 + 只加字段而不在 submit 里写入，是"PIPL 留痕"最容易出现的假完成：
    // 接口 200、库里永远 NULL、合规抽查时才发现一条证据都没有。
    assertThat(captor.getValue().getConsentRecordId()).isEqualTo("CONSENT-2026-0001");
    assertThat(captor.getValue().consentRecorded()).isTrue();
    assertThat(captor.getValue().consentWithdrawn()).isFalse();
  }

  @Test
  @DisplayName("报案幂等：同幂等键已存在 → 返回首次案件，不重复 insert")
  void submitIdempotent() {
    ClaimCase existing =
        ClaimCase.draft(
            999L,
            TENANT_ID,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "",
            "",
            OUT_INSURE,
            "B1",
            "v3",
            List.of(
                ClaimItem.of(
                    1L,
                    1L,
                    999L,
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-1")).thenReturn(existing);

    SubmittedClaim claimId = service.submit(command("idem-1"));

    assertThat(claimId.claimId()).isEqualTo(999L);
    verify(claimCaseRepository, never()).insert(any());
    verify(domainEventPublisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("保单未生效 → SC_CLAIM_POLICY_NOT_ACTIVE（422），不建案")
  void submitRejectsInactivePolicy() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(command("idem-1")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE));
    verify(claimCaseRepository, never()).insert(any());
  }

  @Test
  @DisplayName("出险时间不在保单有效期 → SC_CLAIM_OUT_OF_POLICY_PERIOD")
  void submitRejectsOutOfPeriod() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));

    assertThatThrownBy(
            () ->
                service.submit(
                    command("idem-1").withOutInsureTime(Instant.parse("2027-01-01T00:00:00Z"))))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD));
  }

  @Test
  @DisplayName("被保人不匹配 → SC_CLAIM_INSURED_MISMATCH（403）")
  void submitRejectsInsuredMismatch() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));

    assertThatThrownBy(() -> service.submit(command("idem-1").withInsuredIdHash("other-hash")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_INSURED_MISMATCH));
  }

  @Test
  @DisplayName("决策：加载聚合 → decide → save + publishFrom")
  void decideSuccess() {
    ClaimCase claim = adjustingClaim();
    when(claimCaseRepository.findById(1001L)).thenReturn(claim);

    service.decide(1001L, Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("10.00"));

    verify(claimCaseRepository).save(any(ClaimCase.class));
    verify(domainEventPublisher).publishFrom(any(ClaimCase.class));
  }

  /**
   * 造一个停在「核赔中（41）」的案件——这是"处理类用例"的原型状态。
   *
   * <p>刻意走真实领域方法逐级推进（而不是反射改字段）：状态机的守卫本身就是被测对象的一部分，绕过它造的夹具 会让"撤回拦截"用例在一个现实中不存在的状态上通过。
   */
  private static ClaimCase adjustingClaim() {
    ClaimCase claim =
        ClaimCase.draft(
            1001L,
            TENANT_ID,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "",
            "",
            OUT_INSURE,
            "B1",
            "v3",
            List.of(
                ClaimItem.of(
                    2001L,
                    1L,
                    1001L,
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    claim.accept();
    claim.allocateToPreExam("op-1");
    claim.startPreExam(false, "op-1");
    claim.completePreExam("op-1");
    claim.escalateToAdjust("op-1");
    claim.claimAdjustTask("reviewer-1");
    return claim;
  }

  @Test
  @DisplayName("撤回同意后核赔决策 → SC_CLAIM_CONSENT_WITHDRAWN(422)，且不落库、不发事件")
  void decideBlockedAfterConsentWithdrawn() {
    ClaimCase claim = adjustingClaim();
    claim.withdrawConsent();
    when(claimCaseRepository.findById(1001L)).thenReturn(claim);

    assertThatThrownBy(
            () ->
                service.decide(
                    1001L, Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("10.00")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .as("必须是独立的合规码，不能混进 SC_CLAIM_STATUS_CONFLICT——否则合规月报数不出来")
                    .isEqualTo(ClaimErrorCodes.CLAIM_CONSENT_WITHDRAWN));
    verify(claimCaseRepository, never()).save(any(ClaimCase.class));
    verify(domainEventPublisher, never()).publishFrom(any(ClaimCase.class));
  }

  @Test
  @DisplayName("撤回是「停止新处理」而非「冻结案件」：撤回后仍可超期关单（否则案件永久悬空）")
  void closeForStaleAllowedAfterConsentWithdrawn() {
    ClaimCase claim =
        ClaimCase.draft(
            1001L,
            TENANT_ID,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "",
            "",
            OUT_INSURE,
            "B1",
            "v3",
            List.of(
                ClaimItem.of(
                    2001L,
                    1L,
                    1001L,
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    claim.accept();
    claim.requestEvidence("op-1");
    claim.withdrawConsent();
    when(claimCaseRepository.findById(1001L)).thenReturn(claim);

    service.closeForStale(1001L, "SYSTEM");

    verify(claimCaseRepository).save(any(ClaimCase.class));
  }

  @Test
  @DisplayName("撤回同意：首次落库，重复调用不再 save（不空转 bump version）")
  void revokeConsentIsIdempotent() {
    ClaimCase claim = adjustingClaim();
    when(claimCaseRepository.findById(1001L)).thenReturn(claim);

    assertThat(service.revokeConsent(1001L)).isTrue();
    assertThat(service.revokeConsent(1001L)).as("重复撤回应返回 false，让调用方知道「本来就已撤回」而不是「刚撤的」").isFalse();

    verify(claimCaseRepository, org.mockito.Mockito.times(1)).save(any(ClaimCase.class));
    assertThat(claim.consentWithdrawn()).isTrue();
  }

  @Test
  @DisplayName("跨租户访问 → SC_CLAIM_NOT_FOUND（404，防枚举）")
  void crossTenantNotFound() {
    ClaimCase otherTenantClaim =
        ClaimCase.draft(
            1001L,
            999L,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "",
            "",
            OUT_INSURE,
            "B1",
            "v3",
            List.of(
                ClaimItem.of(
                    2001L,
                    1L,
                    1001L,
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    when(claimCaseRepository.findById(1001L)).thenReturn(otherTenantClaim);

    assertThatThrownBy(() -> service.allocate(1001L, "op-1"))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_NOT_FOUND));
  }
}
