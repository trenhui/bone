package com.bone.procurement.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

/** 就绪度判词评估器：四类事实推导 AVAILABLE/DEGRADED/UNKNOWN，任一未知 fail-closed。 */
class AiReadinessEvaluatorTest {

  private final AiReadinessEvaluator evaluator = new AiReadinessEvaluator();

  @Test
  @DisplayName("评估器必须可被应用上下文装配")
  void evaluatorIsASpringBean() {
    assertThat(AiReadinessEvaluator.class.isAnnotationPresent(Component.class)).isTrue();
  }

  @Test
  @DisplayName("四类事实全部通过才返回 AVAILABLE")
  void allPassReturnsAvailable() {
    AiReadinessView view = evaluator.evaluate(AiReadinessFacts.allReady());
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.AVAILABLE);
    assertThat(view.deploymentGatesPassed()).isTrue();
    assertThat(view.authorizationNotice()).contains("不等于");
  }

  @Test
  @DisplayName("provider 未配置时不得 AVAILABLE")
  void providerNotConfiguredDegrades() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(false, true, false, true, true));
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
    assertThat(view.reasons()).contains("PROVIDER_NOT_CONFIGURED");
  }

  @Test
  @DisplayName("总闸关闭时不得 AVAILABLE")
  void killSwitchEngagedDegrades() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(true, true, true, true, true));
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
  }

  @Test
  @DisplayName("评测未转正时不得声称效果达标")
  void evalNotFrozenDegrades() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(true, true, false, false, true));
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.DEGRADED);
    assertThat(view.toString()).doesNotContain("效果达标");
  }

  @Test
  @DisplayName("治理未启用时判 UNKNOWN（无法判定，fail-closed）")
  void governanceDisabledIsUnknown() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(true, false, false, true, true));
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("任一事实未知时判 UNKNOWN 且保留原始码")
  void unknownFactFailsClosed() {
    AiReadinessView view = evaluator.evaluate(new AiReadinessFacts(null, true, false, true, true));
    assertThat(view.verdict()).isEqualTo(AiReadinessVerdict.UNKNOWN);
    assertThat(view.reasons().toString()).contains("UNKNOWN");
  }
}
