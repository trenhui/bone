package com.bone.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.procurement.advisory.application.AdvisoryApplicationService;
import com.bone.procurement.advisory.domain.model.advisory.AdvisoryResponse;
import com.bone.procurement.advisory.domain.model.advisory.AiAvailability;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import com.bone.procurement.governance.domain.service.FairnessRule;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import com.bone.procurement.qualification.domain.model.qualification.VerifyStatus;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * evalset BIDDOC-01 / QA-01 / RESP-01 源码落点：偏见条款不自动通过、知识问答不回传原文、AI 填字段不自动核验。
 *
 * <p>本类只断言当前工程已真实存在的 fail-closed 行为；evalset 中的 `NEED_MANUAL_REVIEW` 与四维体检尚未有 Java 载体，不在此伪造枚举或状态。
 */
class ProcuraCapabilityCoverageTest {

  @Test
  @DisplayName("discriminatoryClause_notAutoPassed")
  void discriminatoryClause_notAutoPassed() {
    FairnessDecision fairness =
        new FairnessRule()
            .evaluate(
                new BiasSignal(
                    "BIDDOC-01",
                    BiasSignal.OwnershipType.PRIVATE,
                    BiasSignal.RegionTier.EAST,
                    BiasSignal.ScaleTier.MEDIUM,
                    "国企优先，民企排除",
                    0.95));
    assertThat(fairness.biasDetected()).isTrue();
    assertThat(fairness.tier()).isEqualTo("MANUAL");
    assertThat(fairness.suggestionEmitted()).isFalse();

    AiGovernanceApplicationService governance = mock(AiGovernanceApplicationService.class);
    SafetyApplicationService safety = mock(SafetyApplicationService.class);
    when(safety.globalAllowsAi()).thenReturn(true);
    when(governance.decide("tender-health-check"))
        .thenReturn(
            GovernanceDecision.deny(
                new CapabilityCode("tender-health-check"),
                CapabilityState.DISABLED,
                GovernanceDecision.REASON_DISABLED));

    AdvisoryResponse response = new AdvisoryApplicationService(governance, safety).advise("M1");

    assertThat(response.aiAvailable()).isEqualTo(AiAvailability.UNKNOWN);
    assertThat(response.degradedTo()).isEqualTo("rules_only");
    assertThat(response.advisoryOnly()).isTrue();
  }

  @Test
  @DisplayName("qaOutputRawText_rejected")
  void qaOutputRawText_rejected() {
    AdvisoryResponse response = allowService("knowledge-qa", "M5").advise("M5");

    assertThat(response.evidenceRef()).startsWith("advisory-ref://");
    assertThat(response.toString()).doesNotContain("身份证").doesNotContain("银行账号");
    assertThat(response.hasDualLabels()).isTrue();
    assertThat(response.canRelease()).isTrue();
    assertThat(response.decidedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("aiFilledField_notAutoVerified")
  void aiFilledField_notAutoVerified() {
    Qualification qualification =
        Qualification.submit(
            1L,
            "SUP-RESP-01",
            "BUSINESS_LICENSE",
            "MASKED-RESP-01",
            new Date(System.currentTimeMillis() + 86400000L),
            "att-resp-01",
            "{\"aiFilled\":true}");

    assertThat(qualification.status()).isEqualTo(VerifyStatus.PENDING);
    assertThat(qualification.status().canSupportOnboarding()).isFalse();
    assertThatThrownBy(() -> qualification.reviewByHuman("  ", true, new Date()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(qualification.status()).isEqualTo(VerifyStatus.PENDING);
  }

  private static AdvisoryApplicationService allowService(String code, String capability) {
    AiGovernanceApplicationService governance = mock(AiGovernanceApplicationService.class);
    SafetyApplicationService safety = mock(SafetyApplicationService.class);
    when(safety.globalAllowsAi()).thenReturn(true);
    when(governance.decide(code)).thenReturn(GovernanceDecision.allow(new CapabilityCode(code)));
    return new AdvisoryApplicationService(governance, safety);
  }
}
