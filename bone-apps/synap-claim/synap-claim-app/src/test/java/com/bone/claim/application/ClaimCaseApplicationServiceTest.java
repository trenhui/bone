package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.CodeMappingApplicationService.ResolveView;
import com.bone.claim.application.CodeMappingApplicationService.StpGateView;
import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.mapping.ResolutionStatus;
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
  @Mock private CodeMappingApplicationService codeMappingApplicationService;
  @Mock private com.bone.claim.domain.repository.ClaimInsurerRepository claimInsurerRepository;
  @InjectMocks private ClaimCaseApplicationService service;

  private PolicyQueryPort.PolicySnapshot snapshot;

  @BeforeEach
  void setUp() {
    snapshot =
        new PolicyQueryPort.PolicySnapshot(
            "EG20260001",
            "INS-EG20260001",
            7L,
            "v3",
            java.util.List.of("hash-abc"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T23:59:59Z"));
    org.mockito.Mockito.lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    // STP 闸门默认健康（allowAuto=true），保证既有收单用例不因新增的 stpGate 调用而 NPE/置标；
    // 验证「闸门阻断」的用例自行覆盖此桩。
    org.mockito.Mockito.lenient()
        .when(codeMappingApplicationService.stpGate(any(), any()))
        .thenReturn(StpGateView.allow());
  }

  private SubmitClaim command(String idempotencyKey) {
    return new SubmitClaim(
        idempotencyKey,
        "EG20260001",
        null,
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
            null,
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
            null,
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
            "idem-key-test-0",
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

  // ===================== §16-2 双保单号：按保司号收单（解析/消歧/fail-closed） =====================
  //
  // 这一组用例守的是"保司号能收单"这条链路的**判定**，而不是"代码里有个 if"：
  // ① 平台号与保司号都可为空（保司来单只带自己的号），但两个都空必须 400；
  // ② 保司号命中多张时不替调用方猜——按被保人收敛，收敛不了就拒收，绝不按生效期/ID 取第一条；
  // ③ 有平台号时只按平台号查，不回退保司号（否则"打错一个字"会静默命中另一张保单）。

  @Test
  @DisplayName("只传保司号且唯一命中 → 建案，落库的是**平台号**（不是入参的保司号）")
  void submitByInsurerNoResolvesPlatformPolicyNo() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS-001", 7L))
        .thenReturn(List.of(snapshotOf("EG20260001", "INS-001", "hash-abc")));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-ins")).thenReturn(null);

    service.submit(insurerKeyedCommand("idem-ins", "INS-001"));

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    // 入参是保司号，但赔案表 policy_no 的语义永远是平台号：写成保司号会让额度/对账两侧都指不到保单
    assertThat(captor.getValue().getPolicyNo()).isEqualTo("EG20260001");
  }

  @Test
  @DisplayName("只传保司号且命中多张 → 按被保人承保名单收敛到唯一一张")
  void submitByInsurerNoDisambiguatesByInsured() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS-001", 7L))
        .thenReturn(
            List.of(
                snapshotOf("EG-GROUP-A", "INS-001", "hash-other"),
                snapshotOf("EG-GROUP-B", "INS-001", "hash-abc")));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-ins2")).thenReturn(null);

    service.submit(insurerKeyedCommand("idem-ins2", "INS-001"));

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    assertThat(captor.getValue().getPolicyNo()).isEqualTo("EG-GROUP-B");
  }

  @Test
  @DisplayName("只传保司号且被保人同时在多张保单内 → SC_CLAIM_POLICY_AMBIGUOUS（422），不建案")
  void submitByInsurerNoAmbiguousFailsClosed() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS-001", 7L))
        .thenReturn(
            List.of(
                snapshotOf("EG-GROUP-A", "INS-001", "hash-abc"),
                snapshotOf("EG-GROUP-B", "INS-001", "hash-abc")));

    assertThatThrownBy(() -> service.submit(insurerKeyedCommand("idem-ins3", "INS-001")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .as("多命中必须显性拒收：取第一条 = 把金额错记到另一张保单，且无人能发现")
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_AMBIGUOUS));
    verify(claimCaseRepository, never()).insert(any());
  }

  @Test
  @DisplayName("只传保司号命中多张但被保人不在任何一张 → SC_CLAIM_INSURED_MISMATCH（403）")
  void submitByInsurerNoNotCoveringInsured() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS-001", 7L))
        .thenReturn(
            List.of(
                snapshotOf("EG-GROUP-A", "INS-001", "hash-other"),
                snapshotOf("EG-GROUP-B", "INS-001", "hash-other2")));

    assertThatThrownBy(() -> service.submit(insurerKeyedCommand("idem-ins4", "INS-001")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .as("保单是存在的，只是这个被保人不在名单里——报成『保单不存在』会让运营去查一个没问题的号")
                    .isEqualTo(ClaimErrorCodes.CLAIM_INSURED_MISMATCH));
  }

  @Test
  @DisplayName("只传保司号且无任何可用保单 → SC_CLAIM_POLICY_NOT_ACTIVE（422）")
  void submitByInsurerNoNotFound() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS-404", 7L)).thenReturn(List.of());

    assertThatThrownBy(() -> service.submit(insurerKeyedCommand("idem-ins5", "INS-404")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE));
  }

  @Test
  @DisplayName("平台号查不到时不回退保司号 → 仍按 NOT_ACTIVE 拒收（防『打错一个字』静默命中另一张保单）")
  void submitPlatformNoMissDoesNotFallBackToInsurerNo() {
    when(policyQueryPort.findActivePolicy("EG-TYPO", 7L)).thenReturn(Optional.empty());
    SubmitClaim command = bothKeysCommand("idem-fb", "EG-TYPO", "INS-001");

    assertThatThrownBy(() -> service.submit(command))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE));
    verify(policyQueryPort, never()).findActivePoliciesByInsurerNo(any(), any());
  }

  @Test
  @DisplayName("平台号与保司号自洽 → 正常建案")
  void submitWithConsistentNumberPair() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-pair")).thenReturn(null);

    service.submit(bothKeysCommand("idem-pair", "EG20260001", "INS-EG20260001"));

    verify(claimCaseRepository).insert(any(ClaimCase.class));
  }

  @Test
  @DisplayName("平台号与保司号指向不同保单 → SC_CLAIM_POLICY_NUMBER_CONFLICT（422），不建案")
  void submitWithConflictingNumberPair() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));

    assertThatThrownBy(
            () -> service.submit(bothKeysCommand("idem-pair2", "EG20260001", "INS-WRONG")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .as("两个号必须自洽：否则赔案记在平台号那张、渠道按保司号那张对账，月底才发现且无法自动纠正")
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_NUMBER_CONFLICT));
    verify(claimCaseRepository, never()).insert(any());
  }

  @Test
  @DisplayName("两个保单标识都为空 → SC_CLAIM_POLICY_KEY_REQUIRED（400），且不触发任何保单查询")
  void submitWithoutAnyPolicyKey() {
    assertThatThrownBy(() -> service.submit(bothKeysCommand("idem-none", null, "  ")))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_POLICY_KEY_REQUIRED));
    verify(policyQueryPort, never()).findActivePolicy(any(), any());
    verify(policyQueryPort, never()).findActivePoliciesByInsurerNo(any(), any());
  }

  /** 报案命令的"只带保司号"变体（平台号置空）。 */
  private SubmitClaim insurerKeyedCommand(String idempotencyKey, String insurerPolicyNo) {
    return bothKeysCommand(idempotencyKey, null, insurerPolicyNo);
  }

  /** 报案命令的"双号"变体：只覆盖两个保单标识，其余沿用 {@link #command(String)} 的默认值。 */
  private SubmitClaim bothKeysCommand(
      String idempotencyKey, String policyNo, String insurerPolicyNo) {
    SubmitClaim base = command(idempotencyKey);
    return new SubmitClaim(
        base.idempotencyKey(),
        policyNo,
        insurerPolicyNo,
        base.planId(),
        base.insuredIdHash(),
        base.claimType(),
        base.invoiceCode(),
        base.invoiceNo(),
        base.invoiceDate(),
        base.hospitalCode(),
        base.diagnosisCode(),
        base.outInsureTime(),
        base.batchNo(),
        base.consentRecordId(),
        base.insurerCode(),
        base.items());
  }

  /** 指定保单号与承保名单的快照（承保名单决定多命中时能否唯一收敛）。 */
  private static PolicyQueryPort.PolicySnapshot snapshotOf(
      String policyNo, String insurerPolicyNo, String insuredIdHash) {
    return new PolicyQueryPort.PolicySnapshot(
        policyNo,
        insurerPolicyNo,
        7L,
        "v3",
        List.of(insuredIdHash),
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-12-31T23:59:59Z"));
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
            "idem-key-test-1",
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
            "idem-key-test-2",
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
            "idem-key-test-3",
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

  // ===================== 多保司收单落双码（主设计 §14 判据①②④⑨） =====================

  private SubmitClaim commandWithInsurer(
      String idempotencyKey, String insurerCode, String hospital, String feeCode) {
    return new SubmitClaim(
        idempotencyKey,
        "EG20260001",
        null,
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        null,
        hospital,
        null,
        OUT_INSURE,
        "B1",
        null,
        insurerCode,
        List.of(
            new SubmitClaim.ClaimItemDto(
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"),
                null,
                feeCode)));
  }

  private void mockInsurerRegistered() {
    when(claimInsurerRepository.findByInsurerCode(TENANT_ID, "YCBX"))
        .thenReturn(
            org.mockito.Mockito.mock(com.bone.claim.domain.model.mapping.ClaimInsurer.class));
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(null);
  }

  @Test
  @DisplayName("判据①③：已登记保司 + 映射命中 → 明细/案件落标准码与版本，不挂起")
  void submitResolvesCodesAndBackfills() {
    mockInsurerRegistered();
    when(codeMappingApplicationService.resolve(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new CodeMappingApplicationService.ResolveView(
                "RESOLVED", "STD-FEE-001", 9L, "", "V3", false));

    service.submit(commandWithInsurer("idem-map-1", "YCBX", "H-9901", "FEE-01"));

    org.mockito.ArgumentCaptor<ClaimCase> caseCaptor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(caseCaptor.capture());
    ClaimCase saved = caseCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(saved.getStdHospitalCode())
        .as("医院域命中必须落标准码（判据①）")
        .isEqualTo("STD-FEE-001");
    org.assertj.core.api.Assertions.assertThat(saved.getMappingVersion()).isEqualTo("V3");
    org.assertj.core.api.Assertions.assertThat(saved.getHangUpStatus()).as("全部命中不得挂起").isZero();
    org.assertj.core.api.Assertions.assertThat(saved.getItems())
        .as("明细标准码随聚合级联落库")
        .anyMatch(i -> "STD-FEE-001".equals(i.getStdFeeCode()));
  }

  @Test
  @DisplayName("判据②④：映射缺失 → 案件挂起转人工 + std 不回落 raw（空串=未解析）")
  void submitWithMissingMappingHangsUpWithoutFallback() {
    mockInsurerRegistered();
    when(codeMappingApplicationService.resolve(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new CodeMappingApplicationService.ResolveView(
                "MISSING", null, null, "NO_ACTIVE_MAPPING", "", true));

    service.submit(commandWithInsurer("idem-map-2", "YCBX", "H-9901", "FEE-01"));

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    ClaimCase saved = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(saved.getHangUpStatus())
        .as("存在未命中必须挂起转人工（判据②）")
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(saved.getHangUpType()).isEqualTo("EVIDENCE");
    org.assertj.core.api.Assertions.assertThat(saved.getStdHospitalCode())
        .as("未命中不得把 raw 码冒充标准码（判据④：空串=未解析）")
        .isEmpty();
    org.assertj.core.api.Assertions.assertThat(saved.getHospitalCode())
        .as("raw 侧必须保留原值（双码的可复现基础）")
        .isEqualTo("H-9901");
  }

  @Test
  @DisplayName("保司未登记 → 跳过解析走存量行为（映射面灰度推进，不拦存量报案）")
  void submitSkipsResolutionForUnregisteredInsurer() {
    when(policyQueryPort.findActivePolicy("EG20260001", 7L)).thenReturn(Optional.of(snapshot));
    when(claimCaseRepository.findByIdempotencyKey(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(null);
    when(claimInsurerRepository.findByInsurerCode(TENANT_ID, "YCBX")).thenReturn(null);

    service.submit(commandWithInsurer("idem-map-3", "YCBX", "H-9901", "FEE-01"));

    verify(codeMappingApplicationService, org.mockito.Mockito.never())
        .resolve(org.mockito.ArgumentMatchers.any());
    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getHangUpStatus()).isZero();
  }

  @Test
  @DisplayName("STP 闸门：码已解析但映射质量不达标 → 标记转人工（不挂起、不拦受理，与挂起正交）")
  void submitWithStpGateBlockedMarksManualReview() {
    mockInsurerRegistered();
    when(codeMappingApplicationService.resolve(any()))
        .thenReturn(
            new CodeMappingApplicationService.ResolveView(
                "RESOLVED", "STD-FEE-001", 9L, "", "V3", false));
    // 映射质量不达标：STP 闸门对该 (保司×域) 返回阻断（allowAuto=false）
    org.mockito.Mockito.lenient()
        .when(codeMappingApplicationService.stpGate(any(), any()))
        .thenReturn(StpGateView.blocked());

    service.submit(commandWithInsurer("idem-stp-1", "YCBX", "H-9901", "FEE-01"));

    org.mockito.ArgumentCaptor<ClaimCase> captor =
        org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    ClaimCase saved = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(saved.isManualReviewRequired())
        .as("STP 闸门不达标必须标记转人工（§6.9）")
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(saved.getHangUpStatus())
        .as("码已解析（无未命中）不得挂起——闸门与挂起正交")
        .isZero();
    org.assertj.core.api.Assertions.assertThat(saved.getStdHospitalCode())
        .as("码已解析仍须落标准码")
        .isEqualTo("STD-FEE-001");
  }

  @Test
  @DisplayName("§16-1 P0 域白名单：禁用 DISEASE 域时该域不参与解析与 STP 评估，启用域照常")
  void submitSkipsDisabledDomain() {
    mockInsurerRegistered();
    when(codeMappingApplicationService.resolve(any()))
        .thenReturn(
            new ResolveView(ResolutionStatus.RESOLVED.name(), "STD-RESOLVED", 1L, "", "v1", false));
    // 仅启用医院域：P0 先只做医院+药品时，疾病域须整体退出解析/评估链路
    service.setEnabledDomainsForTest("HOSPITAL");
    try {
      service.submit(commandWithInsurer("idem-scope", "YCBX", "H-9901", "FEE-01"));

      org.mockito.ArgumentCaptor<ClaimCase> captor =
          org.mockito.ArgumentCaptor.forClass(ClaimCase.class);
      verify(claimCaseRepository).insert(captor.capture());
      ClaimCase saved = captor.getValue();
      assertThat(saved.getStdHospitalCode()).as("启用域照常解析落标准码").isEqualTo("STD-RESOLVED");
      assertThat(saved.getStdDiagnosisCode()).as("禁用域（DISEASE）不参与解析，标准码留空而非静默兜底").isEmpty();
      // STP 闸门同样跳过禁用域：禁用域不应产生质量评估调用
      verify(codeMappingApplicationService, never()).stpGate("YCBX", "DISEASE");
    } finally {
      // 复位共享实例状态，避免泄漏到其它用例（默认 ALL＝全开）
      service.setEnabledDomainsForTest("ALL");
    }
  }

  @Test
  @DisplayName("判据⑨：miss 解决后重解析 → 明细回填标准码 + 挂起案件自动恢复")
  void rescanBackfillsAndResumesHungUpClaim() {
    // 案件：raw 医院码 + EVIDENCE 挂起（模拟收单时映射缺失）。
    // std 列用可变桩：assignStdCodes 是 void，mock 默认 no-op 不会真改状态，
    // 用 Answer 联动才能让「回填后重判完成度」的路径真实走通。
    ClaimCase claim = org.mockito.Mockito.mock(ClaimCase.class);
    // 案件未携带医院/诊断码（raw 空 = 该域无待解析项），唯一缺口是明细费用类别码——
    // 该域 miss 解决后案件即「全解析」，应自动恢复（部分解决不恢复的口径见实现注释）。
    when(claim.getHospitalCode()).thenReturn("");
    when(claim.getDiagnosisCode()).thenReturn("");
    when(claim.getHangUpStatus()).thenReturn(1);
    when(claim.getHangUpType()).thenReturn("EVIDENCE");
    when(claim.getId()).thenReturn(501L);

    ClaimItem pending =
        ClaimItem.of(
            601L,
            TENANT_ID,
            501L,
            "诊查费",
            "",
            ItemCategory.SERVICE,
            null,
            null,
            new BigDecimal("10.00"));
    pending.assignFeeCode("FEE-01");
    when(claimItemRepository.findPendingByFeeCode(TENANT_ID, "FEE-01"))
        .thenReturn(List.of(pending));
    when(claimCaseRepository.findById(501L)).thenReturn(claim);
    when(claimItemRepository.findByClaimId(501L)).thenReturn(List.of(pending));
    when(codeMappingApplicationService.resolve(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new CodeMappingApplicationService.ResolveView(
                "RESOLVED", "STD-FEE-001", 9L, "", "V3", false));

    service.onMissResolved(
        new com.bone.claim.domain.model.mapping.MappingMissResolvedEvent(
            TENANT_ID, "YCBX", "FEE_CATEGORY", "FEE-01", 77L));

    org.assertj.core.api.Assertions.assertThat(pending.getStdFeeCode())
        .as("重解析回填标准码（判据⑨）")
        .isEqualTo("STD-FEE-001");
    verify(claimItemRepository).save(pending);
    verify(claim).resume("SYSTEM");
    verify(claimCaseRepository).save(claim);
  }

  @Test
  @DisplayName("判据⑨幂等：已解析行不被重扫覆盖（重复消费不重复落账）")
  void rescanSkipsAlreadyResolvedItems() {
    when(claimItemRepository.findPendingByFeeCode(TENANT_ID, "FEE-01"))
        .thenReturn(List.of()); // 仓储条件 std=''，已解析行根本不进扫描集

    service.onMissResolved(
        new com.bone.claim.domain.model.mapping.MappingMissResolvedEvent(
            TENANT_ID, "YCBX", "FEE_CATEGORY", "FEE-01", 78L));

    verify(claimItemRepository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
  }
}
