package com.bone.claim.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 上下文富化验证：当 Spring 装配仓储后，{@link QlExpressRuleEnginePortAdapter#factsOf} 应按 claimId 拉取案卷/ 证据并暴露「30+
 * 风控-合规-临床」维度变量，使规则集新增维度可真实求值。
 *
 * <p>用 Mockito 注入仓储、加载真实 {@code claim-ruleset.json}，断言富化后的新维度规则（R-SCOPE-01 等）确实命中，
 * 证明富化路径非「编译通过却不执行」。
 */
class QlExpressRuleEnginePortAdapterEnrichmentTest {

  @Test
  @DisplayName("富化生效：超出一期范围的 claimType 触发 R-SCOPE-01 且 riskHit=true")
  void enrichmentActivatesNewDimensionRules() {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getClaimType()).thenReturn("INPATIENT"); // 超出一期 OUTPATIENT 范围
    when(claim.getPolicyNo()).thenReturn("P-1");
    when(claim.getInsuredIdHash()).thenReturn("hash-1");
    when(claim.getHospitalCode()).thenReturn("");
    when(claim.getStdHospitalCode()).thenReturn("");
    when(claim.getDiagnosisCode()).thenReturn("");
    when(claim.getStdDiagnosisCode()).thenReturn("");
    when(claim.getMappingVersion()).thenReturn("");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview()).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(77L)).thenReturn(claim);
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
              assertThat(h.version()).isEqualTo("claim-ruleset-v3");
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
    when(claim.getHospitalCode()).thenReturn("H1");
    when(claim.getStdHospitalCode()).thenReturn("STD-H1");
    when(claim.getDiagnosisCode()).thenReturn("D1");
    when(claim.getStdDiagnosisCode()).thenReturn("STD-D1");
    when(claim.getMappingVersion()).thenReturn("v1");
    when(claim.consentWithdrawn()).thenReturn(false);
    when(claim.isManualReviewRequired()).thenReturn(false);
    when(claim.requiresSecondReview()).thenReturn(false);
    when(claim.getOutInsureTime()).thenReturn(Instant.now());
    when(claim.getCreatedAt()).thenReturn(Instant.now());
    when(claim.getSourceChannel()).thenReturn("self");

    ClaimCaseRepository claimRepo = mock(ClaimCaseRepository.class);
    when(claimRepo.findById(88L)).thenReturn(claim);
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
}
