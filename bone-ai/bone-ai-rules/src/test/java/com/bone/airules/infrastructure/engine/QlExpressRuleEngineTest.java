package com.bone.airules.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airules.domain.gateway.RuleEngine;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QLExpress 引擎：声明式规则求值 + 异常按未命中 + 表达式追踪采集。 */
class QlExpressRuleEngineTest {

  private final QlExpressRuleEngine engine = new QlExpressRuleEngine();

  @Test
  void evaluates_declarative_threshold() {
    RuleEngine.RuleEvaluation ev =
        engine.evaluate("amount >= 10000", Map.of("amount", 15000), false);
    assertThat(ev.hit()).isTrue();
    assertThat(ev.errorMessage()).isNull();
  }

  @Test
  void evaluates_string_match() {
    RuleEngine.RuleEvaluation ev =
        engine.evaluate("status == 'URGENT'", Map.of("status", "URGENT"), false);
    assertThat(ev.hit()).isTrue();
  }

  @Test
  void misses_when_false() {
    RuleEngine.RuleEvaluation ev = engine.evaluate("amount >= 10000", Map.of("amount", 500), false);
    assertThat(ev.hit()).isFalse();
  }

  @Test
  void error_treated_as_miss() {
    RuleEngine.RuleEvaluation ev = engine.evaluate("unknownFn(x)", Map.of("x", 1), false);
    assertThat(ev.hit()).isFalse();
    assertThat(ev.errorMessage()).isNotNull();
  }

  @Test
  void trace_mode_does_not_break_evaluation() {
    RuleEngine.RuleEvaluation ev =
        engine.evaluate("amount >= 10000", Map.of("amount", 20000), true);
    assertThat(ev.hit()).isTrue();
    assertThat(ev.errorMessage()).isNull();
  }

  @Test
  void sandbox_blocks_external_class_access() {
    // 沙箱（isolation）必须阻断 import / 反射 / 外部类静态调用：否则规则表达式即成 RCE 面。
    // 若隔离失效，下方会真的执行 System.getProperty 并返回非空串→命中；断言其被拒（miss + 错误）。
    RuleEngine.RuleEvaluation ev =
        engine.evaluate("java.lang.System.getProperty('java.version') != null", Map.of(), false);
    assertThat(ev.hit()).isFalse();
    assertThat(ev.errorMessage()).isNotNull();
  }
}
