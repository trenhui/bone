package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityReadMode;
import com.bone.claim.domain.model.liability.LiabilityRuleJson;
import com.bone.claim.domain.model.liability.LiabilityRuleJsonView;
import com.bone.claim.domain.model.liability.RuleValueDiff;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 读侧解码与读开关（M2 ③ 3a）。
 *
 * <p>两份断言各自钉住一个容易失守的边界：
 *
 * <ul>
 *   <li><b>宽松解码只放宽"键可以缺"</b>，不放宽版本、键集合与键形态——否则就是把 fail-closed 换成了"错了也不说"；
 *   <li><b>开关关闭时连解析都不做</b>——用"喂一段非法 JSON 也不抛"来证明，而不是靠口头承诺。
 * </ul>
 */
class LiabilityRuleJsonReadSideTest {

  // ===================== 宽松解码 =====================

  @Test
  @DisplayName("历史行 NULL：解码为空视图，不报错")
  void nullColumnDecodesToEmptyView() {
    assertThat(LiabilityRuleJsonCodec.decodeView(null).amounts()).isEmpty();
  }

  @Test
  @DisplayName("稀疏 JSON：只解出实际存在的键，缺的键留给回落")
  void sparseJsonKeepsOnlyPresentKeys() {
    LiabilityRuleJsonView view =
        LiabilityRuleJsonCodec.decodeView("{\"schemaVersion\":1,\"pay_percent\":90}");

    assertThat(view.amounts()).containsOnlyKeys(LiabilityRuleJson.KEY_PAY_PERCENT);
    assertThat(view.resolve(LiabilityRuleJson.KEY_PAY_PERCENT, BigDecimal.ONE))
        .isEqualByComparingTo("90");
    assertThat(view.resolve(LiabilityRuleJson.KEY_LIABILITY_LIMIT, new BigDecimal("5000")))
        .isEqualByComparingTo("5000");
  }

  @Test
  @DisplayName("未知版本：读取期拒绝并报出版本号")
  void unknownVersionRejected() {
    assertThatThrownBy(
            () -> LiabilityRuleJsonCodec.decodeView("{\"schemaVersion\":2,\"pay_percent\":90}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("schemaVersion=2");
  }

  @Test
  @DisplayName("未知键：读取期拒绝并报出键名，不得忽略后继续")
  void unknownKeyRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decodeView(
                    "{\"schemaVersion\":1,\"pay_percent\":90,\"pay_percnet\":80}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("pay_percnet");
  }

  @Test
  @DisplayName("非法 JSON：读取期拒绝，不得当作「整列为空」走旧路径")
  void illegalJsonRejected() {
    assertThatThrownBy(() -> LiabilityRuleJsonCodec.decodeView("{not-json"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("不是合法 JSON");
  }

  @Test
  @DisplayName("超集形态（对象/数组）在读取期仍被拒绝：它是 ④ 才解锁的表达")
  void supersetShapeRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decodeView(
                    "{\"schemaVersion\":1,\"pay_percent\":{\"0-1000\":80}}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining(LiabilityRuleJson.KEY_PAY_PERCENT);
  }

  // ===================== 读开关 =====================

  @Test
  @DisplayName("开关关闭：非法 JSON 也不解析、不抛、不挂视图（零行为变更可证）")
  void disabledGateNeverParses() {
    AdjLiability liability = new AdjLiability();
    liability.attachRuleJsonShadow("{这不是 JSON");

    new LiabilityRuleReadGate(false).attach(liability);

    assertThat(liability.getRuleView()).isNull();
    assertThat(liability.getRuleReadMode()).isEqualTo(LiabilityReadMode.LEGACY_ONLY);
  }

  @Test
  @DisplayName("开关打开：解析并挂载 DUAL_READ 模式")
  void enabledGateAttachesDualReadView() {
    AdjLiability liability = new AdjLiability();
    liability.attachRuleJsonShadow("{\"schemaVersion\":1,\"pay_percent\":90}");

    new LiabilityRuleReadGate(true).attach(liability);

    assertThat(liability.getRuleView()).isNotNull();
    assertThat(liability.getRuleView().has(LiabilityRuleJson.KEY_PAY_PERCENT)).isTrue();
    assertThat(liability.getRuleReadMode()).isEqualTo(LiabilityReadMode.DUAL_READ);
  }

  @Test
  @DisplayName("开关打开且 JSON 非法：读取期 fail-closed，不静默回落旧路径")
  void enabledGateFailsClosedOnBadJson() {
    AdjLiability liability = new AdjLiability();
    liability.attachRuleJsonShadow("{这不是 JSON");

    // 读侧失败必须变成受控业务错误（带键名/版本号），而不是冒成 500 的运行时异常
    assertThatThrownBy(() -> new LiabilityRuleReadGate(true).attach(liability))
        .isInstanceOf(com.bone.core.exception.BizException.class)
        .hasMessageContaining("不是合法 JSON");
  }

  @Test
  @DisplayName("差异只计数与记录，不抛异常、不改金额；取走后不重复记录")
  void diffsAreCountedThenDrained() {
    AdjLiability liability = new AdjLiability();
    liability.collectRuleDiffs(
        List.of(
            new RuleValueDiff(
                "L1",
                "v1",
                LiabilityRuleJson.KEY_PAY_PERCENT,
                new BigDecimal("80"),
                new BigDecimal("90"))));
    LiabilityRuleReadGate gate = new LiabilityRuleReadGate(true);

    gate.logDiffs(liability);

    assertThat(gate.diffCount()).isEqualTo(1);
    assertThat(liability.drainRuleDiffs()).isEmpty();
  }
}
