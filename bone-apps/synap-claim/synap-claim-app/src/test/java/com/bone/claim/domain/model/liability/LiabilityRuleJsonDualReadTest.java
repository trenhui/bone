package com.bone.claim.domain.model.liability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.engine.LiabilityRule;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M2 ③ 3a：按键回落与双轨比对（零行为变更）。
 *
 * <p><b>为什么这些用例必须在实现之前写</b>：本 change 的核心风险不是"算错"，而是"判据看着对、其实没判别力"。 「整列判断」写法（JSON 非空就全部走
 * JSON）在所有键都齐全且与标量逐位相等时<b>表现完全正常</b>——② 的写入不变量保证了这一点。 只有"只配一个键"的稀疏行才暴露它。故用例必须先在稀疏行上红，再让实现转绿，否则等于没测。
 */
class LiabilityRuleJsonDualReadTest {

  private static final BigDecimal NINETY = new BigDecimal("90");
  private static final BigDecimal SCALAR_PAY = new BigDecimal("80");
  private static final BigDecimal DEDUCT = new BigDecimal("200");
  private static final BigDecimal LIMIT = new BigDecimal("5000");

  // ===================== 按键回落：禁止整列判断 =====================

  @Test
  @DisplayName("只配 pay_percent 一个键：该键取 JSON，其余两键取旧标量")
  void singleKeyFallsBackPerKey() {
    LiabilityRuleJsonView view = view(Map.of(LiabilityRuleJson.KEY_PAY_PERCENT, NINETY), Map.of());

    assertThat(view.resolve(LiabilityRuleJson.KEY_PAY_PERCENT, SCALAR_PAY))
        .isEqualByComparingTo(NINETY);
    assertThat(view.resolve(LiabilityRuleJson.KEY_LIABILITY_DEDUCT, DEDUCT))
        .isEqualByComparingTo(DEDUCT);
    assertThat(view.resolve(LiabilityRuleJson.KEY_LIABILITY_LIMIT, LIMIT))
        .isEqualByComparingTo(LIMIT);
  }

  @Test
  @DisplayName("JSON 为稀疏时空缺键必须回落，不得被当成「整列有值」")
  void sparseJsonDoesNotImplyAllKeysFromJson() {
    LiabilityRuleJsonView view = view(Map.of(LiabilityRuleJson.KEY_PAY_PERCENT, NINETY), Map.of());

    assertThat(view.has(LiabilityRuleJson.KEY_PAY_PERCENT)).isTrue();
    assertThat(view.has(LiabilityRuleJson.KEY_LIABILITY_DEDUCT)).isFalse();
    assertThat(view.has(LiabilityRuleJson.KEY_LIABILITY_LIMIT)).isFalse();
  }

  @Test
  @DisplayName("历史行 JSON 为 NULL：三键全部取旧标量，不产生任何差异")
  void historicalNullRowUsesScalarsOnly() {
    List<RuleValueDiff> diffs = new ArrayList<>();

    LiabilityRule dual =
        LiabilityRule.fromLegacy(
            "L1",
            "v1",
            "REIMBURSEMENT",
            SCALAR_PAY,
            DEDUCT,
            LIMIT,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            LiabilityReadMode.DUAL_READ,
            diffs);

    assertThat(diffs).isEmpty();
    assertThat(dual).isEqualTo(legacyRule(SCALAR_PAY, DEDUCT, LIMIT));
  }

  @Test
  @DisplayName("A 类键双空：JSON 缺失且标量为 null 必须拒绝并报出键名")
  void aClassKeyBothAbsentIsRejected() {
    LiabilityRuleJsonView view = view(Map.of(), Map.of());

    assertThatThrownBy(() -> view.resolve(LiabilityRuleJson.KEY_PAY_PERCENT, null))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining(LiabilityRuleJson.KEY_PAY_PERCENT);
  }

  @Test
  @DisplayName("标量为 0 与缺省 null 是两件事：0 是可计算值，null 才是缺值")
  void zeroScalarIsAValueNotAnAbsence() {
    LiabilityRuleJsonView view = view(Map.of(), Map.of());

    assertThat(view.resolve(LiabilityRuleJson.KEY_LIABILITY_DEDUCT, BigDecimal.ZERO))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  // ===================== B 类维度：缺键 = 不施加约束 =====================

  @Test
  @DisplayName("只配一个 B 类维度：只施加该维度，其余不当 0、不当必填、不拒绝")
  void singleDimensionAppliesOnlyItself() {
    Map<String, String> dims = new LinkedHashMap<>();
    dims.put("times_limit", "{\"perYear\":2}");
    LiabilityRuleJsonView view = view(Map.of(), dims);

    assertThat(view.dimension("times_limit")).isEqualTo("{\"perYear\":2}");
    assertThat(view.dimension("quota_controller")).isNull();
    assertThat(view.dimension("adjustment_rule")).isNull();
  }

  @Test
  @DisplayName("历史行不施加 B 类约束：不得把缺失的 B 类键判成 fail-closed")
  void historicalRowAppliesNoDimensions() {
    LiabilityRuleJsonView empty = LiabilityRuleJsonView.empty();

    for (String key : LiabilityRuleJson.DIMENSION_KEYS) {
      assertThat(empty.dimension(key)).isNull();
    }
  }

  // ===================== 双轨比对：只记录、不改金额 =====================

  @Test
  @DisplayName("注入差异：记录差异键与两侧数值，返回的仍是旧标量路径的规则")
  void dualReadRecordsDiffButReturnsLegacyRule() {
    LiabilityRuleJsonView view = view(Map.of(LiabilityRuleJson.KEY_PAY_PERCENT, NINETY), Map.of());
    List<RuleValueDiff> diffs = new ArrayList<>();

    LiabilityRule dual =
        LiabilityRule.fromLegacy(
            "L1",
            "v1",
            "REIMBURSEMENT",
            SCALAR_PAY,
            DEDUCT,
            LIMIT,
            false,
            null,
            null,
            null,
            null,
            null,
            view,
            LiabilityReadMode.DUAL_READ,
            diffs);

    assertThat(diffs).hasSize(1);
    RuleValueDiff diff = diffs.get(0);
    assertThat(diff.key()).isEqualTo(LiabilityRuleJson.KEY_PAY_PERCENT);
    assertThat(diff.legacyValue()).isEqualByComparingTo(SCALAR_PAY);
    assertThat(diff.jsonValue()).isEqualByComparingTo(NINETY);
    assertThat(dual).isEqualTo(legacyRule(SCALAR_PAY, DEDUCT, LIMIT));
  }

  @Test
  @DisplayName("MySQL 把 90.00 规范化成 90.0：数值相等即视为无差异，不产生误报")
  void decimalScaleDifferenceIsNotADiff() {
    LiabilityRuleJsonView view =
        view(Map.of(LiabilityRuleJson.KEY_PAY_PERCENT, new BigDecimal("90.0")), Map.of());
    List<RuleValueDiff> diffs = new ArrayList<>();

    LiabilityRule.fromLegacy(
        "L1",
        "v1",
        "REIMBURSEMENT",
        new BigDecimal("90.00"),
        DEDUCT,
        LIMIT,
        false,
        null,
        null,
        null,
        null,
        null,
        view,
        LiabilityReadMode.DUAL_READ,
        diffs);

    assertThat(diffs).isEmpty();
  }

  @Test
  @DisplayName("开关关闭：即便 JSON 与标量不同，也不比对、不记录")
  void legacyOnlyModeNeverCompares() {
    List<RuleValueDiff> diffs = new ArrayList<>();

    LiabilityRule rule =
        LiabilityRule.fromLegacy(
            "L1",
            "v1",
            "REIMBURSEMENT",
            SCALAR_PAY,
            DEDUCT,
            LIMIT,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            LiabilityReadMode.LEGACY_ONLY,
            diffs);

    assertThat(diffs).isEmpty();
    assertThat(rule).isEqualTo(legacyRule(SCALAR_PAY, DEDUCT, LIMIT));
  }

  // ===================== 夹具 =====================

  private static LiabilityRuleJsonView view(
      Map<String, BigDecimal> amounts, Map<String, String> dimensions) {
    return new LiabilityRuleJsonView(LiabilityRuleJson.CURRENT_SCHEMA_VERSION, amounts, dimensions);
  }

  private static LiabilityRule legacyRule(BigDecimal pay, BigDecimal deduct, BigDecimal limit) {
    return LiabilityRule.fromLegacy(
        "L1", "v1", "REIMBURSEMENT", pay, deduct, limit, false, null, null, null, null, null);
  }
}
