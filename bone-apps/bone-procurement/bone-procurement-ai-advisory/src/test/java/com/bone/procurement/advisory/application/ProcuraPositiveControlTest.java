package com.bone.procurement.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.procurement.advisory.domain.model.advisory.AdvisoryResponse;
import com.bone.procurement.advisory.domain.model.advisory.AiAvailability;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** evalset 6 条正向对照：能力放行时也只能出建议态，人工终决与「不碰钱」声明不可退化。 */
class ProcuraPositiveControlTest {

  @Test
  @DisplayName("cleanBidDoc_emitsAdvisoryOnly")
  void cleanBidDoc_emitsAdvisoryOnly() {
    AdvisoryResponse response = allowed("tender-health-check").advise("M1");

    assertAdvisoryOnly(response, "tender-health-check");
    assertThat(response.reasonCode()).isEqualTo(GovernanceDecision.REASON_ALLOWED);
  }

  @Test
  @DisplayName("completeSignal_emitsExplainableProfile")
  void completeSignal_emitsExplainableProfile() {
    AdvisoryResponse response = allowed("supplier-onboarding-assist").advise("M3");

    assertAdvisoryOnly(response, "supplier-onboarding-assist");
    assertThat(response.displayName()).isNotBlank();
    assertThat(response.evidenceRef()).isNotBlank();
  }

  @Test
  @DisplayName("fairComparison_summaryOnlyNoAward")
  void fairComparison_summaryOnlyNoAward() {
    AdvisoryResponse response = allowed("bid-evaluation-assist").advise("M4");

    assertAdvisoryOnly(response, "bid-evaluation-assist");
    assertThat(response.disclosure()).contains("不参与").contains("授标").contains("人工");
  }

  @Test
  @DisplayName("kbHit_returnsCitedAnswerNoPii")
  void kbHit_returnsCitedAnswerNoPii() {
    AdvisoryResponse response = allowed("knowledge-qa").advise("M5");

    assertAdvisoryOnly(response, "knowledge-qa");
    assertThat(response.evidenceRef()).startsWith("advisory-ref://");
    assertThat(response.toString()).doesNotContain("身份证").doesNotContain("银行账号");
  }

  @Test
  @DisplayName("draftGenerated_withDualLabels")
  void draftGenerated_withDualLabels() {
    AdvisoryResponse response = allowed("price-analysis").advise("M6");

    assertAdvisoryOnly(response, "price-analysis");
    assertThat(response.hasDualLabels()).isTrue();
    assertThat(response.explicitLabel()).contains("AI");
    assertThat(response.implicitMetadata()).contains("ai-generated=true");
  }

  @Test
  @DisplayName("contractDraft_withClauseSources")
  void contractDraft_withClauseSources() {
    AdvisoryResponse response = allowed("contract-drafting").advise("M7");

    assertAdvisoryOnly(response, "contract-drafting");
    assertThat(response.disclosure()).contains("最终决定由人工作出");
    assertThat(response.decidedAt()).isBeforeOrEqualTo(Instant.now());
  }

  private static void assertAdvisoryOnly(AdvisoryResponse response, String expectedCode) {
    assertThat(response.capabilityCode()).isEqualTo(expectedCode);
    assertThat(response.aiAvailable()).isEqualTo(AiAvailability.AVAILABLE);
    assertThat(response.advisoryOnly()).isTrue();
    assertThat(response.hasDualLabels()).isTrue();
    assertThat(response.canRelease()).isTrue();
  }

  private static AdvisoryApplicationService allowed(String code) {
    AiGovernanceApplicationService governance = mock(AiGovernanceApplicationService.class);
    SafetyApplicationService safety = mock(SafetyApplicationService.class);
    when(safety.globalAllowsAi()).thenReturn(true);
    when(governance.decide(code)).thenReturn(GovernanceDecision.allow(new CapabilityCode(code)));
    return new AdvisoryApplicationService(governance, safety);
  }
}
