package com.bone.claim.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

/** 规则资产化：外部规则集可替换、版本随命中留痕，配置不可信时回退内置集。 */
class ConfigurableRuleSetTest {

  private static final ClaimContextPort.ClaimContextView CLEAN =
      new ClaimContextPort.ClaimContextView(
          1001L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), true);

  private static final ClaimContextPort.ClaimContextView UNVERIFIED =
      new ClaimContextPort.ClaimContextView(
          1001L, "OUTPATIENT", new BigDecimal("200.00"), true, true, new BigDecimal("0.95"), false);

  @Test
  @DisplayName("外部规则集生效：自定义表达式命中且版本随之落命中明细")
  void externalRuleSetIsApplied() {
    String json =
        "{\"version\":\"custom-v9\",\"rules\":[{\"code\":\"R-TEST-01\",\"category\":\"TEST\","
            + "\"priority\":1,\"expression\":\"claimAmount >= 100\","
            + "\"conclusion\":\"TEST_HIT\",\"risk\":true}]}";
    QlExpressRuleEnginePortAdapter adapter = adapterWith(json);

    RuleEnginePort.RuleCheckResult result = adapter.check(CLEAN);

    assertThat(result.available()).isTrue();
    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits())
        .anySatisfy(
            hit -> {
              assertThat(hit.code()).isEqualTo("R-TEST-01");
              assertThat(hit.version()).isEqualTo("custom-v9");
              assertThat(hit.conclusion()).isEqualTo("TEST_HIT");
            });
  }

  @Test
  @DisplayName("配置损坏/空规则：回退内置规则集，不静默放行")
  void brokenRuleSetFallsBackToDefaults() {
    QlExpressRuleEnginePortAdapter adapter = adapterWith("not-json");

    RuleEnginePort.RuleCheckResult result = adapter.check(UNVERIFIED);

    assertThat(result.available()).isTrue();
    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits())
        .anySatisfy(
            hit -> {
              assertThat(hit.code()).isEqualTo("R-FRAUD-06");
              assertThat(hit.version()).isEqualTo(QlExpressRuleEnginePortAdapter.RULE_VERSION);
            });
  }

  @Test
  @DisplayName("无 Spring 装配（单元夹具）：同样走内置回退，规则仍真实求值")
  void noResourceFallsBackToDefaults() {
    RuleEnginePort.RuleCheckResult result = new QlExpressRuleEnginePortAdapter().check(UNVERIFIED);

    assertThat(result.riskHit()).isTrue();
    assertThat(result.hits()).anySatisfy(h -> assertThat(h.code()).isEqualTo("R-FRAUD-06"));
  }

  private static QlExpressRuleEnginePortAdapter adapterWith(String json) {
    QlExpressRuleEnginePortAdapter adapter = new QlExpressRuleEnginePortAdapter();
    ReflectionTestUtils.setField(
        adapter, "rulesetResource", new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
    return adapter;
  }
}
