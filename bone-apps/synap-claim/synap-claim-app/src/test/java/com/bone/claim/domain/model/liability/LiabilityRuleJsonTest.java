package com.bone.claim.domain.model.liability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * `liability_rule_json` 值对象的不变量（M2 ② Shadow）。
 *
 * <p><b>为什么这组用例必须存在</b>：② 阶段标量是真源、JSON 是影子，"影子写歪"没有任何可观测症状—— 页面上正常、理算也正常（没人读它），直到 ③ 把读侧切到 JSON
 * 才按错值算钱。金额类错误最坏的形态就是静默算错， 所以这里把"影子必须能压回标量"钉成构造期与断言期的硬约束。
 *
 * <p>每条拒绝用例都同时断言 <b>消息里说明了原因</b>：只说"非法"的错误在排障时等于没说。
 */
class LiabilityRuleJsonTest {

  private static final BigDecimal PAY = new BigDecimal("90.00");
  private static final BigDecimal DEDUCT = new BigDecimal("200.00");
  private static final BigDecimal LIMIT = new BigDecimal("5000.00");

  @Test
  void ofScalars_projectsBackToTheSameAmounts() {
    LiabilityRuleJson json = LiabilityRuleJson.ofScalars(PAY, DEDUCT, LIMIT);

    assertThat(json.schemaVersion()).isEqualTo(LiabilityRuleJson.CURRENT_SCHEMA_VERSION);
    assertThat(json.project(LiabilityRuleJson.KEY_PAY_PERCENT)).isEqualByComparingTo(PAY);
    assertThat(json.project(LiabilityRuleJson.KEY_LIABILITY_DEDUCT)).isEqualByComparingTo(DEDUCT);
    assertThat(json.project(LiabilityRuleJson.KEY_LIABILITY_LIMIT)).isEqualByComparingTo(LIMIT);
    assertThat(json.dimensions()).isEmpty();
    assertThatCode(() -> json.assertProjectsTo(PAY, DEDUCT, LIMIT)).doesNotThrowAnyException();
  }

  /** 精度不同不等于值变了：{@code 90.0} 与 {@code 90.00} 是同一个金额，不得因此判红。 */
  @Test
  void assertProjectsTo_acceptsScaleDifference() {
    LiabilityRuleJson json =
        new LiabilityRuleJson(1, new BigDecimal("90.0"), DEDUCT, LIMIT, Map.of());

    assertThatCode(() -> json.assertProjectsTo(new BigDecimal("90.00"), DEDUCT, LIMIT))
        .doesNotThrowAnyException();
  }

  /** 差一分就必须炸，且消息里要能看出是哪个键、差多少。 */
  @Test
  void assertProjectsTo_rejectsValueMismatchAndNamesTheKey() {
    LiabilityRuleJson json =
        new LiabilityRuleJson(1, new BigDecimal("80"), DEDUCT, LIMIT, Map.of());

    assertThatThrownBy(() -> json.assertProjectsTo(PAY, DEDUCT, LIMIT))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("pay_percent")
        .hasMessageContaining("标量=90.00")
        .hasMessageContaining("JSON=80");
  }

  /**
   * 编辑路径保留 B 类维度。
   *
   * <p>反例（用 {@code ofScalars} 重建）会静默删光限定条件——"改个比例顺手取消白名单"， 页面上看不出变化，金额上立刻生效。
   */
  @Test
  void withProjection_keepsExistingDimensions() {
    LiabilityRuleJson json =
        new LiabilityRuleJson(
            1, PAY, DEDUCT, LIMIT, Map.of("restrict_scope", "{\"mode\":\"WHITELIST\"}"));

    LiabilityRuleJson edited = json.withProjection(new BigDecimal("80"), DEDUCT, LIMIT);

    assertThat(edited.project(LiabilityRuleJson.KEY_PAY_PERCENT)).isEqualByComparingTo("80");
    assertThat(edited.dimensions()).containsKey("restrict_scope");
    assertThat(edited.dimensions().get("restrict_scope")).contains("WHITELIST");
  }

  @Test
  void unknownSchemaVersion_isRejected() {
    assertThatThrownBy(() -> new LiabilityRuleJson(2, PAY, DEDUCT, LIMIT, Map.of()))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("schemaVersion=2")
        .hasMessageContaining("不得按旧语义解释");
  }

  /**
   * 缺 A 类键必须拒绝，不能按 0 兜底。
   *
   * <p>按 0 兜底的后果是"该责任一分不赔"，而且从库里、从页面上都看不出差别——静默少赔。
   */
  @Test
  void missingAmountKey_isRejected() {
    assertThatThrownBy(() -> new LiabilityRuleJson(1, PAY, null, LIMIT, Map.of()))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("liability_deduct")
        .hasMessageContaining("静默少赔");
  }

  @Test
  void unknownDimensionKey_isRejected() {
    assertThatThrownBy(
            () -> new LiabilityRuleJson(1, PAY, DEDUCT, LIMIT, Map.of("restrict_scop", "{}")))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("restrict_scop")
        .hasMessageContaining("不得被静默忽略");
  }

  /** 投影只对 3 个可映射键有意义：B 类维度没有标量来源，问它要投影属于用错 API。 */
  @Test
  void project_rejectsNonAmountKey() {
    assertThatThrownBy(() -> LiabilityRuleJson.ofScalars(PAY, DEDUCT, LIMIT).project("times_limit"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("times_limit")
        .hasMessageContaining("没有标量投影");
  }
}
