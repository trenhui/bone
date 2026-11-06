package com.bone.claim.domain.model.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.evaluation.AiModelCard.DriftVerdict;
import com.bone.claim.domain.model.evaluation.AiModelCard.InventoryReport;
import com.bone.claim.domain.model.evaluation.AiModelCard.ModelCard;
import com.bone.claim.domain.model.evaluation.AiModelCard.Verification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 模型卡 / 清单 / 漂移判据测试：构造期强约束 + 三道防线 + 空值语义。 */
class AiModelCardTest {

  private static final String HASH = "a".repeat(64);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
  private static final BigDecimal WATCH = new BigDecimal("0.10");
  private static final BigDecimal BREACH = new BigDecimal("0.20");

  private static ModelCard card(String id, Verification v, LocalDate review) {
    return new ModelCard(
        id,
        "claim-assess-v5",
        "门诊小额快赔的票据事实抽取与差异提示",
        "自有脱敏票据 / 授权 / 去标识化 / 2026Q3",
        HASH,
        "金额段×医院等级×渠道×风险标记",
        "不支持手写体与无文本层影像；临床合理性依赖目录数据",
        "连续两期漂移 BREACH 或独立验证失效",
        "claim-mrm",
        v,
        review);
  }

  @Test
  @DisplayName("最小字段齐全 + 独立验证 ⇒ 允许进入放量档")
  void verifiedCardEligible() {
    ModelCard c = card("deepseek-flash@v1", Verification.INDEPENDENT_VERIFIED, TODAY.plusMonths(3));

    assertThat(c.eligibleForProduction(TODAY)).isTrue();
    assertThat(c.reviewOverdue(TODAY)).isFalse();
  }

  @Test
  @DisplayName("仅一线自测不得进生产（三道防线不可跳级）")
  void selfTestOnlyNotEligible() {
    ModelCard c = card("m@1", Verification.SELF_TEST_ONLY, TODAY.plusMonths(3));

    assertThat(c.eligibleForProduction(TODAY)).isFalse();
    assertThat(Verification.INTERNAL_AUDITED.atLeastIndependentVerification()).isTrue();
  }

  @Test
  @DisplayName("复验到期即失去准入资格，即使已独立验证")
  void overdueReviewBlocksEligibility() {
    ModelCard c = card("m@1", Verification.INDEPENDENT_VERIFIED, TODAY.minusDays(1));

    assertThat(c.reviewOverdue(TODAY)).isTrue();
    assertThat(c.eligibleForProduction(TODAY)).isFalse();
  }

  @Test
  @DisplayName("必填字段空 / 局限为空 / 退役条件为空 → 构造期拒绝")
  void incompleteCardRejected() {
    assertThatThrownBy(
            () ->
                new ModelCard(
                    "",
                    "v",
                    "u",
                    "d",
                    HASH,
                    "s",
                    "l",
                    "r",
                    "o",
                    Verification.INDEPENDENT_VERIFIED,
                    TODAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ModelCard(
                    "m",
                    "v",
                    "u",
                    "d",
                    HASH,
                    "s",
                    " ",
                    "r",
                    "o",
                    Verification.INDEPENDENT_VERIFIED,
                    TODAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ModelCard(
                    "m",
                    "v",
                    "u",
                    "d",
                    HASH,
                    "s",
                    "l",
                    "",
                    "o",
                    Verification.INDEPENDENT_VERIFIED,
                    TODAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("evalSetHash 非 64 位小写 sha256 → 构造期拒绝（与冻结集 V3 对齐）")
  void malformedEvalSetHashRejected() {
    assertThatThrownBy(
            () ->
                new ModelCard(
                    "m",
                    "v",
                    "u",
                    "d",
                    "ABC",
                    "s",
                    "l",
                    "r",
                    "o",
                    Verification.INDEPENDENT_VERIFIED,
                    TODAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ModelCard(
                    "m",
                    "v",
                    "u",
                    "d",
                    "A".repeat(64),
                    "s",
                    "l",
                    "r",
                    "o",
                    Verification.INDEPENDENT_VERIFIED,
                    TODAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("清单：重复标识 / 未验证 / 复验到期 / null 条目分别计数，不混成一个数")
  void inventoryCountsBlockersSeparately() {
    List<ModelCard> cards =
        Arrays.asList(
            card("m@1", Verification.INDEPENDENT_VERIFIED, TODAY.plusMonths(1)),
            card("m@1", Verification.SELF_TEST_ONLY, TODAY.minusDays(1)),
            null);

    InventoryReport report = AiModelCard.inventory(cards, TODAY);

    assertThat(report.total()).isEqualTo(3);
    assertThat(report.duplicateIds()).isEqualTo(1);
    assertThat(report.unverified()).isEqualTo(1);
    assertThat(report.reviewOverdue()).isEqualTo(1);
    assertThat(report.clean()).isFalse();
    assertThat(report.blockers()).hasSize(4);
  }

  @Test
  @DisplayName("清单全绿：无重复、均已独立验证、均未到期")
  void cleanInventory() {
    InventoryReport report =
        AiModelCard.inventory(
            List.of(card("m@1", Verification.INDEPENDENT_VERIFIED, TODAY.plusMonths(1))), TODAY);

    assertThat(report.clean()).isTrue();
    assertThat(report.blockers()).isEmpty();
  }

  @Test
  @DisplayName("漂移：null → NO_DATA（不是 WITHIN）；边界含等于；WATCH/BREACH 阻断晋升")
  void driftSemantics() {
    assertThat(AiModelCard.drift(null, WATCH, BREACH)).isEqualTo(DriftVerdict.NO_DATA);
    assertThat(AiModelCard.drift(new BigDecimal("0.05"), WATCH, BREACH))
        .isEqualTo(DriftVerdict.WITHIN_TOLERANCE);
    assertThat(AiModelCard.drift(WATCH, WATCH, BREACH)).isEqualTo(DriftVerdict.WATCH);
    assertThat(AiModelCard.drift(BREACH, WATCH, BREACH)).isEqualTo(DriftVerdict.BREACH);
    assertThat(DriftVerdict.WATCH.blocksPromotion()).isTrue();
    assertThat(DriftVerdict.BREACH.blocksPromotion()).isTrue();
    assertThat(DriftVerdict.NO_DATA.blocksPromotion()).isFalse();
    assertThat(DriftVerdict.WITHIN_TOLERANCE.blocksPromotion()).isFalse();
  }

  @Test
  @DisplayName("漂移参数非法（负 PSI / 观察阈值不小于阻断阈值）拒绝")
  void invalidDriftParametersRejected() {
    assertThatThrownBy(() -> AiModelCard.drift(new BigDecimal("-0.1"), WATCH, BREACH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AiModelCard.drift(BigDecimal.ZERO, BREACH, BREACH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
