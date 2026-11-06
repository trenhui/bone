package com.bone.claim.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.config.ClaimThresholds;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 上下文富化验证：当 Spring 装配仓储后，{@link QlExpressRuleEnginePortAdapter#factsOf} 应按 claimId 拉取案卷/ 证据并暴露「30+
 * 风控-合规-临床」维度变量，使规则集新增维度可真实求值。
 *
 * <p>用 Mockito 注入仓储、加载真实 {@code claim-ruleset.json}（当前版本 claim-ruleset-v4），断言富化后的新维度规则 （R-SCOPE-01
 * / R-FRAUD-22 / R-ANOM-03 等）确实命中，证明富化路径非「编译通过却不执行」。
 */
class QlExpressRuleEnginePortAdapterEnrichmentTest {

  @Test
  @DisplayName("富化生效：超出一期范围的 claimType 触发 R-SCOPE-01 且 riskHit=true")
  void enrichmentActivatesNewDimensionRules() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("INPATIENT"); // 超出一期 OUTPATIENT 范围
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("");
    when(claim.getStdHospitalCode()).thenReturn("");
    when(claim.getDiagnosisCode()).thenReturn("");
    when(claim.getStdDiagnosisCode()).thenReturn("");
    when(claim.getMappingVersion()).thenReturn("");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-1")).thenReturn(List.of());
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(77L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            77L, "INPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    assertThat(result.available()).isTrue();
    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits())
        .anySatisfy(
            h -> {
              assertThat(h.code()).isEqualTo("R-SCOPE-01");
              assertThat(h.version()).isEqualTo("claim-ruleset-v4");
            });
    // 临床/目录缺失维度（risk=false）同时作为审计命中登记
    assertThat(result.hits()).extracting(RuleEnginePort.RuleHit::code).contains("R-FRAUD-07");
  }

  @Test
  @DisplayName("完整一期门诊案件：富化后无风险命中，硬闸通过（不误伤正常案件）")
  void cleanOutpatientCasePassesGate() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("OUTPATIENT");
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(88L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-1")).thenReturn(List.of());
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(88L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            88L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    assertThat(result.riskHit()).isFalse();
    assertThat(result.fraudGatePassed()).isTrue();
  }

  @Test
  @DisplayName("跨案重复索赔：历史 1 案(超 90 天) 触发 R-FRAUD-22 且 riskHit=true，R-ANOM-03 不命中")
  void crossCaseDuplicateTriggersFraudRule() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("OUTPATIENT");
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCase prior = mock(ClaimCase.class);
    when(prior.getId()).thenReturn(999L);
    when(prior.getCreatedAt()).thenReturn(Instant.now().minus(100, ChronoUnit.DAYS));

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-1")).thenReturn(List.of(prior));
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(77L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            77L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    assertThat(result.available()).isTrue();
    assertThat(result.hits()).extracting(RuleEnginePort.RuleHit::code).contains("R-FRAUD-22");
    assertThat(result.hits()).extracting(RuleEnginePort.RuleHit::code).doesNotContain("R-ANOM-03");
    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits())
        .filteredOn(h -> h.code().equals("R-FRAUD-22"))
        .extracting(RuleEnginePort.RuleHit::version)
        .containsExactly("claim-ruleset-v4");
  }

  @Test
  @DisplayName("近 90 天高频：3 案(10 天内) 触发 R-ANOM-03 审计命中；R-FRAUD-22 同时命中(risk=true 来自前者)")
  void frequencyAnomalyTriggersAuditRule() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("OUTPATIENT");
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-freq");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    List<ClaimCase> priors = new ArrayList<>();
    for (long id = 1; id <= 3; id++) {
      ClaimCase p = mock(ClaimCase.class);
      when(p.getId()).thenReturn(id);
      when(p.getCreatedAt()).thenReturn(Instant.now().minus(10, ChronoUnit.DAYS));
      priors.add(p);
    }

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-freq")).thenReturn(priors);
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(77L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            77L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    assertThat(result.hits()).extracting(RuleEnginePort.RuleHit::code).contains("R-ANOM-03");
    assertThat(result.hits()).extracting(RuleEnginePort.RuleHit::code).contains("R-FRAUD-22");
    // R-ANOM-03 仅审计留痕(risk=false)；本场景风险由 R-FRAUD-22(risk=true) 拉起
    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits())
        .filteredOn(h -> h.code().equals("R-ANOM-03"))
        .extracting(RuleEnginePort.RuleHit::version)
        .containsExactly("claim-ruleset-v4");
  }

  @Test
  @DisplayName("无历史案件：priorClaimCount/recentClaims90d 均为 0，R-FRAUD-22 / R-ANOM-03 均不误报")
  void noPriorClaimsNoFalsePositive() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("OUTPATIENT");
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-1")).thenReturn(List.of());
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(77L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            77L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    assertThat(result.hits())
        .extracting(RuleEnginePort.RuleHit::code)
        .doesNotContain("R-FRAUD-22", "R-ANOM-03");
    assertThat(result.riskHit()).isFalse();
    assertThat(result.fraudGatePassed()).isTrue();
  }

  @Test
  @DisplayName("排除本案自身：findByInsuredIdHash 仅含本案(id=ctx.claimId) 时 R-FRAUD-22/R-ANOM-03 不得误报")
  void excludesCurrentClaimFromHistory() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("OUTPATIENT");
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getTenantId()).thenReturn(1L);
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    // 历史集合里只有本案自身(id=77L) —— 必须被排除，否则必然误报 R-FRAUD-22
    ClaimCase selfClone = mock(ClaimCase.class);
    when(selfClone.getId()).thenReturn(77L);
    when(selfClone.getCreatedAt()).thenReturn(Instant.now().minus(10, ChronoUnit.DAYS));

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
    when(claimRepo.findByInsuredIdHash(1L, "hash-1")).thenReturn(List.of(selfClone));
    EvidenceRepository evRepo = mock(EvidenceRepository.class);
    when(evRepo.findByClaimId(77L)).thenReturn(List.of());

    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(adapter, "claimCaseRepository", claimRepo);
    ReflectionTestUtils.setField(adapter, "evidenceRepository", evRepo);
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ClassPathResource("claim-ruleset.json"));

    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            77L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

    RuleEnginePort.RuleCheckResult result = adapter.check(ctx);

    // 仅含自身 → 排除后 priorClaimCount=0 → 两条历史维度均不命中（关键：杜绝 100% 误报）
    assertThat(result.hits())
        .extracting(RuleEnginePort.RuleHit::code)
        .doesNotContain("R-FRAUD-22", "R-ANOM-03");
    assertThat(result.riskHit()).isFalse();
  }
}
