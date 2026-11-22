package com.bone.procurement.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.CapabilityView;
import com.bone.procurement.governance.application.KillSwitchView;
import com.bone.procurement.governance.application.SafetyApplicationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 反假绿：评测态必须来自 evalset 真源，能力注册态必须是可推理能力清单，二者均不可被裸配置抬升。 */
class AiReadinessTruthfulnessTest {

  private final AiReadinessEvaluator evaluator = new AiReadinessEvaluator();

  @Test
  void evalStateUnknownFailsClosed() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(true, true, false, null, true));

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.UNKNOWN);
    assertThat(view.reasons()).contains("EVAL_STATE_UNKNOWN");
  }

  @Test
  void noInferenceCapabilityDegrades() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(true, true, false, true, false));

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
    assertThat(view.reasons()).contains("INFERENCE_CAPABILITY_NOT_REGISTERED");
    assertThat(view.deploymentGatesPassed()).isFalse();
  }

  @Test
  void allFactsPassWithFrozenEvalsetCanBeAvailable() throws Exception {
    Path evalset = writeEvalset("frozen", false);
    AiReadinessView view =
        serviceWith(
                evalset.toString(),
                safety(true),
                governance(new CapabilityView("C1", "合同审查", "AVAILABLE", "u1", "甲", true)))
            .readiness();

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.AVAILABLE);
    assertThat(view.evalFrozen()).isTrue();
    assertThat(view.inferenceCapabilityRegistered()).isTrue();
    assertThat(view.deploymentGatesPassed()).isTrue();
  }

  @Test
  void seededDraftEvalsetNeverMarksFrozen() throws Exception {
    Path evalset = writeEvalset("seededDraft", true);
    AiReadinessView view =
        serviceWith(
                evalset.toString(),
                safety(true),
                governance(new CapabilityView("C1", "合同审查", "AVAILABLE", "u1", "甲", true)))
            .readiness();

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
    assertThat(view.reasons()).contains("EVAL_NOT_FROZEN");
    assertThat(view.evalFrozen()).isFalse();
  }

  @Test
  void missingEvalsetFailsClosed() {
    AiReadinessView view =
        serviceWith(
                "/definitely/missing/procura-evalset.json",
                safety(true),
                governance(new CapabilityView("C1", "合同审查", "AVAILABLE", "u1", "甲", true)))
            .readiness();

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.UNKNOWN);
    assertThat(view.reasons()).contains("EVAL_STATE_UNKNOWN");
  }

  @Test
  void noEnabledCapabilityDegrades() throws Exception {
    Path evalset = writeEvalset("frozen", false);
    AiReadinessView view =
        serviceWith(
                evalset.toString(),
                safety(true),
                governance(new CapabilityView("C1", "合同审查", "DISABLED", "u1", "甲", false)))
            .readiness();

    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
    assertThat(view.reasons()).contains("INFERENCE_CAPABILITY_NOT_REGISTERED");
    assertThat(view.inferenceCapabilityRegistered()).isFalse();
  }

  private static Path writeEvalset(String status, boolean needsReview) throws Exception {
    Path evalset = Files.createTempFile("procura-evalset", ".json");
    Files.writeString(
        evalset, "{\"status\":\"" + status + "\",\"needsOwnerReview\":" + needsReview + "}");
    return evalset;
  }

  private static SafetyApplicationService safety(boolean allowsAi) {
    SafetyApplicationService service = mock(SafetyApplicationService.class);
    when(service.currentKillSwitch())
        .thenReturn(new KillSwitchView("NORMAL", allowsAi, "u1", "e2e", null));
    return service;
  }

  private static AiGovernanceApplicationService governance(CapabilityView... capabilities) {
    AiGovernanceApplicationService service = mock(AiGovernanceApplicationService.class);
    when(service.listCapabilities()).thenReturn(List.of(capabilities));
    return service;
  }

  @SuppressWarnings("unchecked")
  private AiReadinessApplicationService serviceWith(
      String evalsetPath,
      SafetyApplicationService safety,
      AiGovernanceApplicationService governance) {
    ObjectProvider<SafetyApplicationService> safetyProvider = mock(ObjectProvider.class);
    ObjectProvider<AiGovernanceApplicationService> governanceProvider = mock(ObjectProvider.class);
    when(safetyProvider.getIfAvailable()).thenReturn(safety);
    when(governanceProvider.getIfAvailable()).thenReturn(governance);
    return new AiReadinessApplicationService(
        evaluator, safetyProvider, governanceProvider, "https://provider.example", evalsetPath);
  }
}
