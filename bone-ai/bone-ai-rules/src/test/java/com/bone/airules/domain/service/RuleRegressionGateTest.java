package com.bone.airules.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RegressionCase;
import com.bone.airules.domain.service.RuleRegressionGate.RegressionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RuleRegressionGate：发布前正/负例回归，任一不符即不通过。 */
class RuleRegressionGateTest {

  private final RuleEngine engine = mock(RuleEngine.class);
  private final RuleRegressionGate gate = new RuleRegressionGate(engine);

  private void stub(boolean hit) {
    when(engine.evaluate(anyString(), anyMap(), anyBoolean()))
        .thenReturn(RuleEngine.RuleEvaluation.of(hit, "trace", 1));
  }

  @Test
  void passes_when_all_cases_match() {
    stub(true);
    RegressionResult r =
        gate.evaluate(
            "amount >= 10000",
            List.of(
                RegressionCase.positive(Map.of("amount", 20000), "大额应命中"),
                RegressionCase.positive(Map.of("amount", 50000), "超大额应命中")));
    assertThat(r.passed()).isTrue();
    assertThat(r.cases()).hasSize(2);
  }

  @Test
  void fails_when_positive_case_misses() {
    stub(false); // 表达式对所有样本都 miss → 正例失败
    RegressionResult r =
        gate.evaluate(
            "amount >= 10000", List.of(RegressionCase.positive(Map.of("amount", 20000), "大额应命中")));
    assertThat(r.passed()).isFalse();
    assertThat(r.cases().get(0).label()).isEqualTo("大额应命中");
    assertThat(r.cases().get(0).expectHit()).isTrue();
    assertThat(r.cases().get(0).actualHit()).isFalse();
  }

  @Test
  void fails_when_negative_case_hits() {
    stub(true); // 表达式对所有样本都 hit → 负例失败
    RegressionResult r =
        gate.evaluate(
            "amount >= 10000", List.of(RegressionCase.negative(Map.of("amount", 100), "小额不应命中")));
    assertThat(r.passed()).isFalse();
    assertThat(r.cases().get(0).passed()).isFalse();
  }

  @Test
  void eval_error_counts_as_failure() {
    when(engine.evaluate(anyString(), anyMap(), anyBoolean()))
        .thenReturn(RuleEngine.RuleEvaluation.error("parse error", 1));
    RegressionResult r =
        gate.evaluate("bad (", List.of(RegressionCase.positive(Map.of(), "编译错误样本")));
    assertThat(r.passed()).isFalse();
    assertThat(r.cases().get(0).detail()).contains("EVAL_ERROR");
  }

  @Test
  void empty_cases_pass_directly() {
    RegressionResult r = gate.evaluate("amount >= 1", List.of());
    assertThat(r.passed()).isTrue();
    assertThat(r.cases()).isEmpty();
  }
}
