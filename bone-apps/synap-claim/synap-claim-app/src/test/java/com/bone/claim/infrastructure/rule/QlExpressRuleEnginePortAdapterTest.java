package com.bone.claim.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G2-1 规则引擎接线验证：证明核责规则是<b>真实执行</b>而非一期桩的默认值，且命中明细可直接用于留痕。
 *
 * <p>变异判据：改掉任一规则表达式（如把 {@code verifyPassed == false} 改成恒 false）， 对应用例必须转红；全绿而删实现不红说明该用例无判别力。
 */
class QlExpressRuleEnginePortAdapterTest {

  private final QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();

  private static ClaimContextPort.ClaimContextView ctx(
      String amount, boolean parsed, String ocr, boolean verifyPassed) {
    return new ClaimContextPort.ClaimContextView(
        1001L,
        "MEDICAL",
        new BigDecimal(amount),
        true,
        parsed,
        ocr == null ? null : new BigDecimal(ocr),
        verifyPassed);
  }

  private static List<String> codes(RuleEnginePort.RuleCheckResult result) {
    return result.hits().stream().map(RuleEnginePort.RuleHit::code).toList();
  }

  @Test
  @DisplayName("G2-1：规则真实执行 ⇒ available=true，干净案件零命中且硬闸通过")
  void cleanCaseIsReallyEvaluated() {
    var result = adapter.check(ctx("3200.00", true, "0.95", true));

    assertThat(result.available()).isTrue();
    assertThat(result.hits()).isEmpty();
    assertThat(result.riskHit()).isFalse();
    assertThat(result.fraudGatePassed()).isTrue();
  }

  @Test
  @DisplayName("验真未过命中 R-FRAUD-06，硬闸不过且命中明细可留痕")
  void verifyFailureHitsFraudRule() {
    var result = adapter.check(ctx("3200.00", true, "0.95", false));

    assertThat(codes(result)).contains("R-FRAUD-06");
    assertThat(result.riskHit()).isTrue();
    assertThat(result.fraudGatePassed()).isFalse();
    RuleEnginePort.RuleHit hit = result.hits().get(0);
    assertThat(hit.version()).isEqualTo(QlExpressRuleEnginePortAdapter.RULE_VERSION);
    assertThat(hit.category()).isEqualTo("FRAUD");
    assertThat(hit.conclusion()).isEqualTo("VERIFY_NOT_PASSED");
    assertThat(hit.inputSnapshot()).contains("\"verifyPassed\":false");
  }

  @Test
  @DisplayName("OCR 置信度低命中 R-OCR-01；null 不得误判为低")
  void lowOcrConfidenceHitsButNullDoesNot() {
    assertThat(codes(adapter.check(ctx("3200.00", true, "0.65", true)))).contains("R-OCR-01");
    assertThat(codes(adapter.check(ctx("3200.00", true, null, true)))).doesNotContain("R-OCR-01");
  }

  @Test
  @DisplayName("证据未解析命中 R-EVID-01")
  void evidenceNotParsedHits() {
    assertThat(codes(adapter.check(ctx("3200.00", false, "0.95", true)))).contains("R-EVID-01");
  }

  @Test
  @DisplayName("大额只作复核档，不计入反欺诈风险（避免与 AMOUNT_OVER_LIMIT 重复归因）")
  void largeAmountIsNotRisk() {
    var result = adapter.check(ctx("15000.00", true, "0.95", true));

    assertThat(codes(result)).contains("R-AMOUNT-01");
    assertThat(result.riskHit()).isFalse();
  }

  @Test
  @DisplayName("F1：calculatePayout 仍只是分诊估算额，绝不进入资金链路")
  void payoutRemainsTriageEstimate() {
    assertThat(adapter.calculatePayout(ctx("3200.00", true, "0.95", true)))
        .isEqualByComparingTo("3200.00");
  }
}
