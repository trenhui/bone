package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 理算引擎 V1 语义测试（九步主流程 · 附录 A 数值复算 · fail-closed 边界）。
 *
 * <p><b>验收准则（V1 附录 A 的教训）</b>：原文三个示例最终赔付都被 {@code min(…, 责任额度 1000)} 截断成 1000， 所以"总自费误用 90% 而非
 * 70%"这个错误在示例里<b>不产生任何可见差异</b>——用这些示例做验收，算错了也会通过。
 * 故本类的所有用例<b>一律把责任/险种/计划额度设到不成为瓶颈</b>（1e8），只让被断言的那一环生效。
 */
class AdjustmentEngineTest {

  private static final BigDecimal NO_CAP = new BigDecimal("100000000");

  // ===================== 附录 A：三个示例的数值复算 =====================

  @Nested
  @DisplayName("附录 A 数值复算（示例 A1/A2/A3）")
  class AppendixA {

    /**
     * A(1) 无免赔：合理 1925.33×90% + 总自费 631.88×90%。
     *
     * <p>原文合计 2301.49（其中 568.70 是笔误，正确 568.69）。
     */
    @Test
    @DisplayName("A(1) 无免赔：1732.80 + 568.69 = 2301.49")
    void exampleA1() {
      LiabilityRule rule =
          rule(
              "90",
              ratios(
                  entry(FeePools.Bucket.REASONABLE, "90", null),
                  entry(FeePools.Bucket.TOTAL_SELF_PAY, "90", null)),
              DeductibleRule.none(),
              NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, rows("1925.33", "0", "631.88"), rule, Accumulation.FIRST));

      assertThat(line(outcome, "REASONABLE").feePayout()).isEqualByComparingTo("1732.80");
      assertThat(line(outcome, "TOTAL_SELF_PAY").feePayout()).isEqualByComparingTo("568.69");
      assertThat(outcome.totalAmount()).isEqualByComparingTo("2301.49");
    }

    /**
     * A(2) 责任级免赔 100：先按比例算出各类理算金额、<b>相加后</b>再减一次免赔。
     *
     * <p>原文用 90% 算总自费（配置是 70%），得 2201.50；按配置应为 1732.80 + 442.32 − 100 = 2075.12。 这个差额在原文示例里被「责任额度
     * 1000」截断掩盖了——本用例不设瓶颈才看得见。
     */
    @Test
    @DisplayName("A(2) 责任级免赔：1732.80 + 442.32 − 100 = 2075.12（原文误用 90% 得 2201.50）")
    void exampleA2() {
      LiabilityRule rule =
          rule(
              "90",
              ratios(
                  entry(FeePools.Bucket.REASONABLE, "90", null),
                  entry(FeePools.Bucket.TOTAL_SELF_PAY, "70", null)),
              DeductibleRule.annualAbsolute(new BigDecimal("100")),
              NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, rows("1925.33", "0", "631.88"), rule, Accumulation.FIRST));

      assertThat(line(outcome, "REASONABLE").ratioPayout()).isEqualByComparingTo("1732.80");
      assertThat(line(outcome, "TOTAL_SELF_PAY").ratioPayout()).isEqualByComparingTo("442.32");
      assertThat(outcome.totalAmount())
          .as("若此处为 2201.50，说明总自费误用了 90%（配置是 70%）= 原文的错误被复现")
          .isEqualByComparingTo("2075.12");
      assertThat(outcome.deductibleApplied()).isEqualByComparingTo("100.00");
    }

    /**
     * A(3) 费用类型级免赔（合理 200 / 自付二 100 / 总自费 100）：各类<b>各自</b>扣对应免赔。
     *
     * <p>(1925.33×90% − 200) + (273.42×80% − 100) + (631.88×70% − 100) = 1532.80 + 118.74 + 342.32
     * = 1993.86。
     */
    @Test
    @DisplayName("A(3) 费用类型级免赔：1532.80 + 118.74 + 342.32 = 1993.86")
    void exampleA3() {
      DeductibleRule perType =
          DeductibleRule.perFeeType(
              Map.of(
                  FeePools.Bucket.REASONABLE,
                  new BigDecimal("200"),
                  FeePools.Bucket.SELF_PAY_2,
                  new BigDecimal("100"),
                  FeePools.Bucket.TOTAL_SELF_PAY,
                  new BigDecimal("100")),
              DeductibleRule.Target.CALCULATED_AMOUNT);
      LiabilityRule rule =
          rule(
              "90",
              ratios(
                  entry(FeePools.Bucket.REASONABLE, "90", null),
                  entry(FeePools.Bucket.SELF_PAY_2, "80", null),
                  entry(FeePools.Bucket.TOTAL_SELF_PAY, "70", null)),
              perType,
              NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, rows("1925.33", "273.42", "631.88"), rule, Accumulation.FIRST));

      assertThat(line(outcome, "REASONABLE").feePayout()).isEqualByComparingTo("1532.80");
      assertThat(line(outcome, "SELF_PAY_2").feePayout()).isEqualByComparingTo("118.74");
      assertThat(line(outcome, "TOTAL_SELF_PAY").feePayout()).isEqualByComparingTo("342.32");
      assertThat(outcome.totalAmount())
          .as("若为 2120.24，说明总自费误用 90%；若为 2175.12，说明按责任级扣了一次而非按类型各扣")
          .isEqualByComparingTo("1993.86");
    }
  }

  // ===================== 免赔六维 =====================

  @Nested
  @DisplayName("免赔：类型 × 对象 × 粒度（V1 §6）")
  class Deductible {

    /** I5：相对免赔「够了就一分不减」，与绝对免赔的区别必须在门槛两侧都能看见。 */
    @Test
    @DisplayName("I5 相对免赔跨门槛不减少赔付（1500 费用、1000 免赔：相对赔 1500，绝对赔 500）")
    void relativeDeductibleSkipsInsteadOfSubtracting() {
      LiabilityRule relative =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.relativeAnnual(new BigDecimal("1000")),
              NO_CAP);
      LiabilityRule absolute =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.annualAbsolute(new BigDecimal("1000")),
              NO_CAP);

      assertThat(payout(relative, "1500.00"))
          .as("相对免赔：应赔 1500 > 门槛 1000 ⇒ 一分不减")
          .isEqualByComparingTo("1500.00");
      assertThat(payout(absolute, "1500.00"))
          .as("绝对免赔：应赔 1500 − 门槛 1000 = 500")
          .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("I5 门槛下方：相对与绝对都全免赔（800 ≤ 1000 → 赔 0，消抵 800）")
    void belowThresholdBothZero() {
      for (DeductibleRule rule :
          List.of(
              DeductibleRule.relativeAnnual(new BigDecimal("1000")),
              DeductibleRule.annualAbsolute(new BigDecimal("1000")))) {
        AdjustmentEngine.Outcome outcome =
            AdjustmentEngine.calculate(
                AdjustmentEngine.Request.of(
                    1L,
                    rows("800.00", "0", "0"),
                    rule(
                        "100",
                        ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
                        rule,
                        NO_CAP),
                    Accumulation.FIRST));
        assertThat(outcome.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(outcome.deductibleApplied()).isEqualByComparingTo("800.00");
      }
    }

    /**
     * 免赔对象决定运算顺序（§6.3）：同一份输入，两个对象算出不同金额。
     *
     * <p>费用 1000、比例 80%、免赔 200：理算金额口径 = 800−200 = 600；发票费用口径 = (1000−200)×80% = 640。
     */
    @Test
    @DisplayName("免赔对象：理算金额口径 600 vs 发票费用口径 640，差 40 必须可辨")
    void deductibleTargetChangesOrder() {
      LiabilityRule calculatedTarget =
          rule(
              "80",
              ratios(entry(FeePools.Bucket.REASONABLE, "80", null)),
              targetAbsolute("200", DeductibleRule.Target.CALCULATED_AMOUNT),
              NO_CAP);
      LiabilityRule invoiceTarget =
          rule(
              "80",
              ratios(entry(FeePools.Bucket.REASONABLE, "80", null)),
              targetAbsolute("200", DeductibleRule.Target.INVOICE_AMOUNT),
              NO_CAP);

      assertThat(payout(calculatedTarget, "1000.00")).isEqualByComparingTo("600.00");
      assertThat(payout(invoiceTarget, "1000.00")).isEqualByComparingTo("640.00");
    }
  }

  // ===================== 控额七层 =====================

  @Nested
  @DisplayName("控额：七层逐层截断（V1 §1.2 / §4.3）")
  class Quota {

    @Test
    @DisplayName("§4.3 两个 Min 嵌套：各类费用额度互不侵占（总自费额度用尽不影响合理金额）")
    void perBucketCapDoesNotSwallowOtherBuckets() {
      PayRatioTable table =
          ratios(
              entry(FeePools.Bucket.REASONABLE, "100", null),
              entry(FeePools.Bucket.TOTAL_SELF_PAY, "100", "300"));
      LiabilityRule rule = rule("100", table, DeductibleRule.none(), NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, rows("1000.00", "0", "800.00"), rule, Accumulation.FIRST));

      assertThat(line(outcome, "REASONABLE").feePayout()).isEqualByComparingTo("1000.00");
      assertThat(line(outcome, "TOTAL_SELF_PAY").feePayout())
          .as("总自费受本格费用限额 300 截断，但不得吞掉合理金额的可用额")
          .isEqualByComparingTo("300.00");
      assertThat(outcome.totalAmount()).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("险种额度与计划额度逐层生效：取最紧的一层（1200 责任内、险种 900、计划 500 → 500）")
    void productAndPlanLayersBind() {
      LiabilityRule rule =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none(),
              NO_CAP);
      AdjustmentEngine.Request request =
          AdjustmentEngine.Request.of(1L, rows("1200.00", "0", "0"), rule, Accumulation.FIRST)
              .withExtraLayers(
                  List.of(
                      new QuotaStack.LayerQuota(
                          QuotaStack.Layer.PRODUCT, "PROD-1", new BigDecimal("900")),
                      new QuotaStack.LayerQuota(
                          QuotaStack.Layer.PLAN, "PLAN-1", new BigDecimal("500"))));

      AdjustmentEngine.Outcome outcome = AdjustmentEngine.calculate(request);

      assertThat(outcome.totalAmount())
          .as("责任额度不封顶 ⇒ 由计划层 500 兜底，而不是取险种 900")
          .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("跨案已用额度：责任额度 1 万已用 9000 ⇒ 本案最多赔 1000")
    void crossCaseLiabilityUsage() {
      LiabilityRule rule =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none(),
              "10000");
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L,
                  rows("5000.00", "0", "0"),
                  rule,
                  Accumulation.of(BigDecimal.ZERO, new BigDecimal("9000"), true)));

      assertThat(outcome.totalAmount()).isEqualByComparingTo("1000.00");
    }
  }

  // ===================== 比例查表 =====================

  @Nested
  @DisplayName("赔付比例：多维查表与 fail-closed（V1 §5）")
  class Ratio {

    @Test
    @DisplayName("累计区间：历史 1500 + 本票 600 = 2100 落入 [2000,∞) 档，取 70%")
    void accumulationRangeSelectsRatio() {
      PayRatioTable table =
          PayRatioTable.of(
              List.of(
                  new PayRatioTable.Entry(
                      "[0,1000)",
                      new PayRatioTable.RatioSelector(
                          FeePools.Bucket.REASONABLE,
                          null,
                          AmountRange.of("0", "1000"),
                          null,
                          null,
                          null),
                      new BigDecimal("90"),
                      null),
                  new PayRatioTable.Entry(
                      "[1000,2000)",
                      new PayRatioTable.RatioSelector(
                          FeePools.Bucket.REASONABLE,
                          null,
                          AmountRange.of("1000", "2000"),
                          null,
                          null,
                          null),
                      new BigDecimal("80"),
                      null),
                  new PayRatioTable.Entry(
                      "[2000,∞)",
                      new PayRatioTable.RatioSelector(
                          FeePools.Bucket.REASONABLE,
                          null,
                          AmountRange.atLeast("2000"),
                          null,
                          null,
                          null),
                      new BigDecimal("70"),
                      null)));
      LiabilityRule rule = rule("90", table, DeductibleRule.none(), NO_CAP);
      AdjustmentEngine.Request request =
          new AdjustmentEngine.Request(
              1L,
              rows("600.00", "0", "0"),
              rule,
              Accumulation.FIRST,
              "v1",
              Map.of(FeePools.Bucket.REASONABLE, new BigDecimal("1500")),
              new BigDecimal("600"),
              null,
              null,
              null,
              List.of());

      AdjustmentEngine.Outcome outcome = AdjustmentEngine.calculate(request);

      assertThat(outcome.totalAmount())
          .as("累计 2100 命中 70% 档；若得 540 说明错取了 90% 档（累计口径没加历史）")
          .isEqualByComparingTo("420.00");
    }

    @Test
    @DisplayName("未命中不回落默认值：比例表缺该费用类型 ⇒ RATIO_NOT_MATCHED（fail-closed）")
    void unmatchedRatioFailsClosed() {
      LiabilityRule rule =
          rule(
              "90",
              ratios(entry(FeePools.Bucket.REASONABLE, "90", null)),
              DeductibleRule.none(),
              NO_CAP);

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                          1L, rows("0", "0", "500.00"), rule, Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("RATIO_NOT_MATCHED");
    }

    @Test
    @DisplayName("区间重叠视为配置错误：装载期即拒绝（I3）")
    void overlappingRangesRejected() {
      PayRatioTable table =
          PayRatioTable.of(
              List.of(
                  new PayRatioTable.Entry(
                      "A",
                      new PayRatioTable.RatioSelector(
                          FeePools.Bucket.REASONABLE,
                          null,
                          AmountRange.of("0", "2000"),
                          null,
                          null,
                          null),
                      new BigDecimal("90"),
                      null),
                  new PayRatioTable.Entry(
                      "B",
                      new PayRatioTable.RatioSelector(
                          FeePools.Bucket.REASONABLE,
                          null,
                          AmountRange.of("1000", "3000"),
                          null,
                          null,
                          null),
                      new BigDecimal("80"),
                      null)));

      assertThatThrownBy(table::validate)
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("RATIO_AMBIGUOUS");
      assertThat(
              AmountRange.pairwiseDisjoint(
                  List.of(AmountRange.of("0", "1000"), AmountRange.of("1000", "2000"))))
          .as("相邻半开区间 [0,1000) 与 [1000,2000) 不重叠")
          .isTrue();
    }
  }

  // ===================== 次限额 =====================

  @Nested
  @DisplayName("次限额：先分组、各组独立理算（V1 §7.3）")
  class Occurrence {

    @Test
    @DisplayName("分组键：同日同院同科室为一组，跨日拆两组（不得总额取 min）")
    void groupingSplitsByDay() {
      List<OccurrenceGrouper.Fact> facts =
          List.of(
              new OccurrenceGrouper.Fact(
                  "CL1", java.time.LocalDate.of(2026, 1, 1), "H1", "D1", null),
              new OccurrenceGrouper.Fact(
                  "CL1", java.time.LocalDate.of(2026, 1, 1), "H1", "D1", null),
              new OccurrenceGrouper.Fact(
                  "CL1", java.time.LocalDate.of(2026, 1, 2), "H1", "D1", null));

      assertThat(OccurrenceGrouper.group(OccurrenceGrouper.Basis.SAME_DAY, facts)).hasSize(2);
      assertThat(OccurrenceGrouper.group(OccurrenceGrouper.Basis.SAME_DAY_HOSPITAL_DEPT, facts))
          .hasSize(2);
      assertThat(OccurrenceGrouper.group(OccurrenceGrouper.Basis.PER_CLAIM, facts)).hasSize(1);
    }

    @Test
    @DisplayName("配了次限额却没配「次」的定义 ⇒ 装载期拒绝（V1 Q-04：配了不生效）")
    void occurrenceLimitWithoutBasisRejected() {
      assertThatThrownBy(
              () ->
                  OccurrenceGrouper.requireConsistent(
                      OccurrenceGrouper.Basis.NONE, new BigDecimal("300")))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("永远不会生效");
    }

    @Test
    @DisplayName("次限额必须真的截断：单次 1000、次限额 300 ⇒ 赔 300（配了不生效就是超赔）")
    void occurrenceLimitCaps() {
      LiabilityRule rule =
          ruleWithLimits(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  new BigDecimal("300"),
                  BigDecimal.ZERO),
              LiabilityRule.QuotaPolicy.defaults(),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, List.of(row(2001L, "IN", "1000.00", "0", "0")), rule, Accumulation.FIRST));

      assertThat(outcome.totalAmount())
          .as("若为 1000.00，说明次限额只是配置字段、没有接进控额栈")
          .isEqualByComparingTo("300.00");
    }

    /**
     * 次限额是"每次各一份"，责任额度是"全案一本账"——两者混用会一个多赔一个少赔。
     *
     * <p>两个「次」各 400、次限额 1000（不生效）、责任额度 500：正确 500；若责任额度按组重置则 800（超赔）。
     */
    @Test
    @DisplayName("两个「次」共享责任额度：400 + 400 受 500 封顶 ⇒ 赔 500（不是 800）")
    void liabilitySharedAcrossOccurrences() {
      LiabilityRule rule =
          occurrenceRule(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  new BigDecimal("500"),
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  new BigDecimal("1000"),
                  BigDecimal.ZERO),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, twoRows("400.00", "400.00"), rule, Accumulation.FIRST),
              twoDayFacts());

      assertThat(outcome.totalAmount())
          .as("若为 800.00，说明责任额度被每个「次」重置了一次（跨组共享失效 ⇒ 超赔）")
          .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("次限额按组各算：两组各 400、次限额 300 ⇒ 600（不是总额 800 取 min = 300）")
    void occurrenceLimitAppliesPerGroup() {
      LiabilityRule rule =
          occurrenceRule(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  new BigDecimal("300"),
                  BigDecimal.ZERO),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, twoRows("400.00", "400.00"), rule, Accumulation.FIRST),
              twoDayFacts());

      assertThat(outcome.totalAmount())
          .as("若为 300.00，说明对总额取了一次 min（次限额被当成年度限额）")
          .isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("年度免赔跨「次」共享余额：两组各 8000、免赔 1 万 ⇒ 8000 被吃光 + 第二组扣 2000 = 赔 6000")
    void annualDeductibleSharedAcrossOccurrences() {
      LiabilityRule rule =
          occurrenceRule(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.annualAbsolute(new BigDecimal("10000")));

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, twoRows("8000.00", "8000.00"), rule, Accumulation.FIRST),
              twoDayFacts());

      assertThat(outcome.totalAmount())
          .as("若为 0.00，说明第二组又重扣了一次满额免赔（多扣 = 客户少赔）")
          .isEqualByComparingTo("6000.00");
      assertThat(outcome.deductibleApplied()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("按次免赔每组独立：两组各 1000、每次免赔 500 ⇒ 各扣 500，赔 1000")
    void perClaimDeductibleResetsPerOccurrence() {
      DeductibleRule perClaim =
          new DeductibleRule(
              true,
              DeductibleRule.Kind.ABSOLUTE,
              DeductibleRule.Basis.PER_CLAIM,
              DeductibleRule.Form.AMOUNT,
              DeductibleRule.Offset.MEDICAL_AND_THIRD_PARTY,
              DeductibleRule.Target.CALCULATED_AMOUNT,
              DeductibleRule.Granularity.LIABILITY,
              new BigDecimal("500"),
              Money.ZERO,
              0,
              Map.of());
      LiabilityRule rule =
          occurrenceRule(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              perClaim);

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, twoRows("1000.00", "1000.00"), rule, Accumulation.FIRST),
              twoDayFacts());

      assertThat(outcome.totalAmount())
          .as("若为 1500.00，说明按次免赔被账本跨组递减（第二组没扣）")
          .isEqualByComparingTo("1000.00");
      assertThat(outcome.deductibleApplied()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("分组维度缺失必须拒绝：配了「同日」却没有发票日期 ⇒ 并成一组会少赔")
    void missingDimensionFailsClosed() {
      LiabilityRule rule =
          occurrenceRule(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  new BigDecimal("300"),
                  BigDecimal.ZERO),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                          1L, twoRows("400.00", "400.00"), rule, Accumulation.FIRST)))
          .as("不传分组事实时不得静默按一次算")
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("OCCURRENCE_DIMENSION_MISSING");
    }

    /**
     * 发票级维度缺失 ≠ 必须拒绝：一赔案一发票（有发票号即可证），案内开票日期恒定， 缺日期时分组结果与配置语义相同。此时拒绝等于把"证据未回填"当成"规则不成立"，转人工率 100%。
     */
    @Test
    @DisplayName("有发票号可证单票 ⇒ 发票日期缺失降级为同一次，不拒绝且留痕")
    void invoiceLevelDimensionDegradesWhenProvable() {
      LiabilityRule rule = occurrenceRule(OccurrenceGrouper.Basis.SAME_DAY, "300");
      List<ClaimWithItemsProjection> rows = twoRows("400.00", "400.00");
      // 发票号是赔案级唯一标识：有它才能证明"案内只有一张票"，从而证明开票日期恒定。
      rows.forEach(row -> row.setInvoiceNo("NO-2026-0001"));

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(1L, rows, rule, Accumulation.FIRST));

      assertThat(outcome.totalAmount())
          .as("降级后整案算一次：400 受次限额 300 截断（若为 600，说明被拆成了两次 ⇒ 多赔）")
          .isEqualByComparingTo("300.00");
      assertThat(outcome.snapshot())
          .as("降级必须进快照：否则'证据没回填'与'规则如此'在审计上长得一模一样")
          .contains("\"singleInvoiceProvable\":true")
          .contains("\"degraded\":[\"发票日期缺失");
      assertThat(outcome.diagnostics()).anyMatch(d -> d.startsWith("次分组降级："));
    }

    @Test
    @DisplayName("无发票号则不可证单票 ⇒ 仍拒绝（多天多票混装会少赔，不能按假设并成一组）")
    void invoiceLevelDimensionRejectsWhenNotProvable() {
      LiabilityRule rule = occurrenceRule(OccurrenceGrouper.Basis.SAME_DAY, "300");
      List<ClaimWithItemsProjection> rows = twoRows("400.00", "400.00");

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(1L, rows, rule, Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("OCCURRENCE_DIMENSION_MISSING");
    }

    @Test
    @DisplayName("科室是行级维度：即使可证单票，缺科室也必须拒绝（一张票可跨科室）")
    void lineLevelDimensionNeverDegrades() {
      LiabilityRule rule = occurrenceRule(OccurrenceGrouper.Basis.SAME_DAY_HOSPITAL_DEPT, "300");
      List<ClaimWithItemsProjection> rows = twoRows("400.00", "400.00");
      rows.forEach(row -> row.setInvoiceNo("NO-2026-0001"));
      List<OccurrenceGrouper.Fact> facts =
          List.of(
              new OccurrenceGrouper.Fact(
                  "CL001", java.time.LocalDate.of(2026, 1, 1), "H1", "", null),
              new OccurrenceGrouper.Fact(
                  "CL001", java.time.LocalDate.of(2026, 1, 1), "H1", "", null));

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(1L, rows, rule, Accumulation.FIRST), facts))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("OCCURRENCE_DIMENSION_MISSING")
          .hasMessageContaining("就诊科室");
    }

    @Test
    @DisplayName("同案内维度部分有值 ⇒ 数据不自洽，拒绝（按有无分组会多赔一次次限额）")
    void partialDimensionRejects() {
      LiabilityRule rule = occurrenceRule(OccurrenceGrouper.Basis.SAME_DAY, "300");
      List<ClaimWithItemsProjection> rows = twoRows("400.00", "400.00");
      rows.forEach(row -> row.setInvoiceNo("NO-2026-0001"));
      List<OccurrenceGrouper.Fact> facts =
          List.of(
              new OccurrenceGrouper.Fact(
                  "CL001", java.time.LocalDate.of(2026, 1, 1), "H1", "", null),
              new OccurrenceGrouper.Fact("CL001", null, "H1", "", null));

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(1L, rows, rule, Accumulation.FIRST), facts))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("OCCURRENCE_DIMENSION_INCONSISTENT");
    }

    /**
     * 接线证明：不显式传 facts 时，分组维度必须来自投影的真实列。
     *
     * <p>此前 {@code factsOf} 恒返回"一赔案一次"，配了"同院同科室"的责任要么被拒、要么少赔， 而这条路径（{@code Adjustment.apply}
     * 走的正是它）根本没有机会读到科室。
     */
    @Test
    @DisplayName("分组维度取自投影列：同日同院不同科室 ⇒ 两组各受次限额 300 ⇒ 赔 600")
    void groupingDimensionsComeFromProjection() {
      LiabilityRule rule = occurrenceRule(OccurrenceGrouper.Basis.SAME_DAY_HOSPITAL_DEPT, "300");
      List<ClaimWithItemsProjection> rows = twoRows("400.00", "400.00");
      for (int i = 0; i < rows.size(); i++) {
        ClaimWithItemsProjection row = rows.get(i);
        row.setClaimNo("CL001");
        row.setInvoiceNo("NO-2026-0001");
        row.setInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        row.setHospitalCode("H1");
        row.setItemDepartment(i == 0 ? "D1" : "D2");
      }

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(1L, rows, rule, Accumulation.FIRST));

      assertThat(outcome.totalAmount())
          .as("若为 300.00，说明科室没有从投影进到分组（两个科室被并成一次 ⇒ 少赔）")
          .isEqualByComparingTo("600.00");
      assertThat(outcome.snapshot()).contains("\"groups\":2");
    }
  }

  // ===================== 额度层接线 =====================

  @Nested
  @DisplayName("额度层接线：配了必须生效，否则拒绝（V1 §1.2 七层）")
  class LayerWiring {

    @Test
    @DisplayName("险种/计划额度声明了却无可用余额 ⇒ LIMIT_LAYER_UNDEFINED（不得静默忽略）")
    void declaredUpperLayerWithoutSourceRejected() {
      LiabilityRule rule =
          ruleWithLimits(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  new BigDecimal("900"),
                  new BigDecimal("500"),
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO),
              LiabilityRule.QuotaPolicy.defaults(),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                          1L, rows("1200.00", "0", "0"), rule, Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("LIMIT_LAYER_UNDEFINED");
    }

    @Test
    @DisplayName("可用余额超过配置额度 ⇒ 两个数必有一个错，拒绝")
    void availableExceedsConfiguredLayerRejected() {
      LiabilityRule rule =
          ruleWithLimits(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED,
                  BigDecimal.ZERO,
                  NO_CAP,
                  BigDecimal.ZERO,
                  new BigDecimal("500"),
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO),
              LiabilityRule.QuotaPolicy.defaults(),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                              1L, rows("1200.00", "0", "0"), rule, Accumulation.FIRST)
                          .withExtraLayers(
                              List.of(
                                  new QuotaStack.LayerQuota(
                                      QuotaStack.Layer.PLAN, "PLAN-1", new BigDecimal("900"))))))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("超过配置额度");
    }

    @Test
    @DisplayName("个人额度配在「仅责任额度」方式下 ⇒ 无消耗路径，拒绝（配了不生效）")
    void personalLimitWithoutSequentialModeRejected() {
      LiabilityRule rule =
          ruleWithLimits(
              new LiabilityRule.LimitSet(
                  LiabilityRule.InsuredForm.FIXED_PLUS_PERSONAL,
                  new BigDecimal("800"),
                  NO_CAP,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO),
              LiabilityRule.QuotaPolicy.defaults(),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none());

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                          1L, rows("1200.00", "0", "0"), rule, Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("没有消耗路径");
    }
  }

  // ===================== 诊断与重复赔付 =====================

  @Nested
  @DisplayName("可观测性：诊断落库 + 三方重复赔付闸门")
  class Observability {

    @Test
    @DisplayName("诊断+净扣必须进快照：第三方已付从可赔基数扣除，且诊断对运维可见")
    void diagnosticsPersistedInSnapshot() {
      LiabilityRule rule =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none(),
              NO_CAP);
      // valid=1000 为毛额合理费用，第三方已付 200 ⇒ 可赔基数净扣为 800
      ClaimWithItemsProjection row = row(2001L, "IN", "1000.00", "0", "0");
      row.setItemThirdPartyPaid(new BigDecimal("200.00"));
      row.setItemAmount(new BigDecimal("1000.00"));

      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(1L, List.of(row), rule, Accumulation.FIRST));

      assertThat(outcome.snapshot())
          .as("诊断不落库 = 风险对运维不可见")
          .contains("\"diagnostics\"")
          .contains("三方已赔");
      assertThat(outcome.totalAmount())
          .as("不可获利：净基数 800 × 100% = 800，而非毛额 1000")
          .isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("统筹+第三方已付超过合理费用 ⇒ 拒绝理算（不可获利：已付不得超损失）")
    void priorPaidExceedsReasonableRejected() {
      LiabilityRule rule =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none(),
              NO_CAP);
      // 合理 1000，但统筹+第三方已付记了 1500（超过合理，不可能）⇒ 拒绝，防 D6 超赔
      ClaimWithItemsProjection row = row(2001L, "IN", "1000.00", "0", "0");
      row.setItemAmount(new BigDecimal("1000.00"));
      row.setItemPoolingAmount(new BigDecimal("1500.00"));

      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(1L, List.of(row), rule, Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("INVOICE_SETTLEMENT_INCONSISTENT");
    }
  }

  // ===================== V1 边界：显式拒绝 =====================

  @Nested
  @DisplayName("V1 边界：能力缺失一律 fail-closed，不猜算法")
  class Boundaries {

    @Test
    @DisplayName("定额给付型 / 津贴给付型：V1 无规则 ⇒ LIABILITY_FORM_UNSUPPORTED")
    void nonReimbursementFormRejected() {
      for (LiabilityRule.LiabilityForm form :
          List.of(
              LiabilityRule.LiabilityForm.FIXED_AMOUNT, LiabilityRule.LiabilityForm.ALLOWANCE)) {
        LiabilityRule rule =
            new LiabilityRule(
                "L",
                "v1",
                form,
                false,
                EnumSet.allOf(FeePools.Bucket.class),
                0,
                DeductibleRule.none(),
                ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
                LiabilityRule.LimitSet.liabilityOnly(NO_CAP),
                LiabilityRule.QuotaPolicy.defaults(),
                LiabilityRule.OccurrenceSpec.none(),
                LiabilityRule.Relation.none(),
                LiabilityRule.LimitedConditions.disabled());
        assertThatThrownBy(rule::validate)
            .isInstanceOf(RuleGap.class)
            .hasMessageContaining("LIABILITY_FORM_UNSUPPORTED");
      }
    }

    @Test
    @DisplayName("共享保额 / 先赔后赔 / 日限额 / 外部控额：各自给出专属原因码，不静默照算")
    void unsupportedRelationsRejected() {
      assertThatThrownBy(() -> ruleWithRelation("SHARED-1", "").validate())
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("SHARED_LIMIT_UNSUPPORTED");
      assertThatThrownBy(() -> ruleWithRelation("", "L-PRIOR").validate())
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("PRIORITY_CHAIN_UNSUPPORTED");
      assertThatThrownBy(
              () ->
                  new LiabilityRule(
                          "L",
                          "v1",
                          LiabilityRule.LiabilityForm.REIMBURSEMENT,
                          false,
                          EnumSet.allOf(FeePools.Bucket.class),
                          0,
                          DeductibleRule.none(),
                          ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
                          new LiabilityRule.LimitSet(
                              LiabilityRule.InsuredForm.FIXED,
                              BigDecimal.ZERO,
                              NO_CAP,
                              BigDecimal.ZERO,
                              BigDecimal.ZERO,
                              BigDecimal.ZERO,
                              BigDecimal.ZERO,
                              new BigDecimal("500")),
                          LiabilityRule.QuotaPolicy.defaults(),
                          LiabilityRule.OccurrenceSpec.none(),
                          LiabilityRule.Relation.none(),
                          LiabilityRule.LimitedConditions.disabled())
                      .validate())
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("DAILY_LIMIT_UNSUPPORTED");
      assertThatThrownBy(
              () ->
                  new LiabilityRule(
                          "L",
                          "v1",
                          LiabilityRule.LiabilityForm.REIMBURSEMENT,
                          false,
                          EnumSet.allOf(FeePools.Bucket.class),
                          0,
                          DeductibleRule.none(),
                          ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
                          LiabilityRule.LimitSet.liabilityOnly(NO_CAP),
                          new LiabilityRule.QuotaPolicy(
                              LiabilityRule.AccountType.INSURER,
                              QuotaStack.DeductionMode.LIABILITY_ONLY),
                          LiabilityRule.OccurrenceSpec.none(),
                          LiabilityRule.Relation.none(),
                          LiabilityRule.LimitedConditions.disabled())
                      .validate())
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("EXTERNAL_QUOTA_UNSUPPORTED");
    }

    @Test
    @DisplayName("等待期内出险：不进理算（§4.1 前置校验）")
    void waitingPeriodBlocks() {
      LiabilityRule rule =
          new LiabilityRule(
              "L",
              "v1",
              LiabilityRule.LiabilityForm.REIMBURSEMENT,
              false,
              EnumSet.allOf(FeePools.Bucket.class),
              30,
              DeductibleRule.none(),
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              LiabilityRule.LimitSet.liabilityOnly(NO_CAP),
              LiabilityRule.QuotaPolicy.defaults(),
              LiabilityRule.OccurrenceSpec.none(),
              LiabilityRule.Relation.none(),
              LiabilityRule.LimitedConditions.disabled());
      AdjustmentEngine.Request request =
          new AdjustmentEngine.Request(
              1L,
              rows("500.00", "0", "0"),
              rule,
              Accumulation.FIRST,
              "v1",
              Map.of(),
              null,
              null,
              null,
              10,
              List.of());

      assertThatThrownBy(() -> AdjustmentEngine.calculate(request))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("WAITING_PERIOD_NOT_MET");
    }
  }

  // ===================== 归因与不变量 =====================

  @Nested
  @DisplayName("归因与不变量（V1 §9）")
  class Invariants {

    @Test
    @DisplayName("I1 分位守恒：Σ 明细实付 ≡ 总额（多费用池也不丢分）")
    void linesSumToTotal() {
      PayRatioTable table =
          ratios(
              entry(FeePools.Bucket.REASONABLE, "90", null),
              entry(FeePools.Bucket.SELF_PAY_2, "80", null),
              entry(FeePools.Bucket.TOTAL_SELF_PAY, "70", null));
      LiabilityRule rule =
          rule("90", table, DeductibleRule.annualAbsolute(new BigDecimal("33")), NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L, rows("111.11", "222.22", "333.33"), rule, Accumulation.FIRST));

      BigDecimal sum = Money.ZERO;
      for (AdjustmentEngine.Line line : outcome.lines()) {
        sum = sum.add(line.feePayout());
      }
      assertThat(sum).isEqualByComparingTo(outcome.totalAmount());
    }

    @Test
    @DisplayName("逐费用项分位归因（GAP⑨）：同一池的多笔费用各出一行、Σ 行 ≡ 池，各池按自身比例分位")
    void perItemAttributionSplitsWithinPool() {
      // 合理金额 300+700=1000 ×90% = 900 ⇒ 270 / 630；自付二 100+300=400 ×80% = 320 ⇒ 80 / 240
      LiabilityRule rule =
          rule(
              "90",
              ratios(
                  entry(FeePools.Bucket.REASONABLE, "90", null),
                  entry(FeePools.Bucket.SELF_PAY_2, "80", null)),
              DeductibleRule.none(),
              NO_CAP);
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L,
                  List.of(
                      row(2001L, "IN", "300.00", "100.00", "0"),
                      row(2002L, "IN", "700.00", "300.00", "0")),
                  rule,
                  Accumulation.FIRST));

      assertThat(
              outcome.lines().stream()
                  .map(
                      line ->
                          line.itemId()
                              + "/"
                              + line.bucket()
                              + "="
                              + line.feePayout().stripTrailingZeros().toPlainString())
                  .toList())
          .as("逐行归因须落到费用项（缺 itemId 就追溯不回票面收费项），且分摊额守恒于池")
          .containsExactly(
              "2001/REASONABLE=270",
              "2002/REASONABLE=630",
              "2001/SELF_PAY_2=80",
              "2002/SELF_PAY_2=240");
      assertThat(outcome.totalAmount()).isEqualByComparingTo("1220.00");
    }

    @Test
    @DisplayName("零赔付归因：责任额度年度用尽 ⇒ ZERO_LIMIT_EXHAUSTED（区别于单次超限）")
    void zeroReasonDistinguishesExhausted() {
      LiabilityRule rule =
          rule(
              "100",
              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
              DeductibleRule.none(),
              "5000");
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L,
                  rows("3000.00", "0", "0"),
                  rule,
                  Accumulation.of(BigDecimal.ZERO, new BigDecimal("5000"), true)));

      assertThat(outcome.totalAmount()).isEqualByComparingTo("0.00");
      assertThat(outcome.zeroReason()).isEqualTo("ZERO_LIMIT_EXHAUSTED");
      assertThat(outcome.rejectType()).isEqualTo("09");
    }

    @Test
    @DisplayName("入参校验：全是赔案头行（无费用明细）必须拒绝，不得按 0 算出一个「合法」结果")
    void headOnlyRowsRejected() {
      assertThatThrownBy(
              () ->
                  AdjustmentEngine.calculate(
                      AdjustmentEngine.Request.of(
                          1L,
                          List.of(headOnlyRow()),
                          rule(
                              "100",
                              ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
                              DeductibleRule.none(),
                              NO_CAP),
                          Accumulation.FIRST)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("INVALID_INPUT");
    }

    @Test
    @DisplayName("快照自包含：物化累计前值、比例命中、免赔分支、控额步骤（I7/I8 可复算）")
    void snapshotMaterializesInputs() {
      LiabilityRule rule =
          rule(
              "90",
              ratios(entry(FeePools.Bucket.REASONABLE, "90", null)),
              DeductibleRule.none(),
              "10000");
      AdjustmentEngine.Outcome outcome =
          AdjustmentEngine.calculate(
              AdjustmentEngine.Request.of(
                  1L,
                  rows("1000.00", "0", "0"),
                  rule,
                  Accumulation.of(new BigDecimal("0"), new BigDecimal("1000"), true)));

      assertThat(outcome.snapshot())
          .contains(
              "\"deductibleUsedBefore\"",
              "\"limitUsedBefore\"",
              "\"ratioHits\"",
              "\"deductibleBranches\"",
              "\"pools\"");
      assertThat(outcome.snapshot()).contains("\"limitUsedBefore\":1000");
    }
  }

  // ===================== 夹具 =====================

  private static BigDecimal payout(LiabilityRule rule, String valid) {
    return AdjustmentEngine.calculate(
            AdjustmentEngine.Request.of(1L, rows(valid, "0", "0"), rule, Accumulation.FIRST))
        .totalAmount();
  }

  /**
   * 取指定费用池的**池级**归因金额。
   *
   * <p>GAP⑨ 逐行分位归因落地后，一个费用池会按费用项拆成多行（Σ 各行 ≡ 池）， 故这里必须按池聚合而不是取首行——取首行会静默读到"第一笔费用的份额"，断言看起来仍绿却是错的。
   */
  private static AdjustmentEngine.Line line(AdjustmentEngine.Outcome outcome, String bucket) {
    return outcome.lines().stream()
        .filter(l -> l.bucket().equals(bucket))
        .reduce(AdjustmentEngineTest::merge)
        .orElseThrow(() -> new AssertionError("缺少费用池归因行：" + bucket + "，实际 " + outcome.lines()));
  }

  private static AdjustmentEngine.Line merge(
      AdjustmentEngine.Line left, AdjustmentEngine.Line right) {
    return new AdjustmentEngine.Line(
        left.bucket(),
        left.bucketLabel(),
        left.scope(),
        left.feeCalculate().add(right.feeCalculate()),
        left.feeDeduct().add(right.feeDeduct()),
        left.ratioPayout().add(right.ratioPayout()),
        left.feeOccupied().add(right.feeOccupied()),
        left.feePayout().add(right.feePayout()),
        left.feeRemain().add(right.feeRemain()),
        left.ratioPercent(),
        left.zeroReason(),
        left.rejectType(),
        null,
        null);
  }

  private static LiabilityRule rule(
      String payPercent, PayRatioTable ratios, DeductibleRule deductible, String liabilityLimit) {
    return rule(payPercent, ratios, deductible, new BigDecimal(liabilityLimit));
  }

  private static LiabilityRule rule(
      String payPercent,
      PayRatioTable ratios,
      DeductibleRule deductible,
      BigDecimal liabilityLimit) {
    return new LiabilityRule(
        "L-TEST",
        "v1",
        LiabilityRule.LiabilityForm.REIMBURSEMENT,
        false,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        deductible,
        ratios,
        LiabilityRule.LimitSet.liabilityOnly(liabilityLimit),
        LiabilityRule.QuotaPolicy.defaults(),
        LiabilityRule.OccurrenceSpec.none(),
        LiabilityRule.Relation.none(),
        LiabilityRule.LimitedConditions.disabled());
  }

  /** 自定义额度集（用于验证险种/计划/次限额/个人额度是否真的接线）。 */
  private static LiabilityRule ruleWithLimits(
      LiabilityRule.LimitSet limits,
      LiabilityRule.QuotaPolicy quota,
      PayRatioTable ratios,
      DeductibleRule deductible) {
    return new LiabilityRule(
        "L-TEST",
        "v1",
        LiabilityRule.LiabilityForm.REIMBURSEMENT,
        false,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        deductible,
        ratios,
        limits,
        quota,
        LiabilityRule.OccurrenceSpec.none(),
        LiabilityRule.Relation.none(),
        LiabilityRule.LimitedConditions.disabled());
  }

  /** 按「同日」分组、次限额取自额度集的责任规则。 */
  private static LiabilityRule occurrenceRule(
      LiabilityRule.LimitSet limits, PayRatioTable ratios, DeductibleRule deductible) {
    return new LiabilityRule(
        "L-TEST",
        "v1",
        LiabilityRule.LiabilityForm.REIMBURSEMENT,
        false,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        deductible,
        ratios,
        limits,
        LiabilityRule.QuotaPolicy.defaults(),
        new LiabilityRule.OccurrenceSpec(
            OccurrenceGrouper.Basis.SAME_DAY, limits.occurrenceLimit()),
        LiabilityRule.Relation.none(),
        LiabilityRule.LimitedConditions.disabled());
  }

  /** 「次」分组规则（100% 赔付、无免赔、责任额度不限）：只调「次」的定义与次限额。 */
  private static LiabilityRule occurrenceRule(
      OccurrenceGrouper.Basis basis, String occurrenceLimit) {
    return new LiabilityRule(
        "L-TEST",
        "v1",
        LiabilityRule.LiabilityForm.REIMBURSEMENT,
        false,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        DeductibleRule.none(),
        ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
        new LiabilityRule.LimitSet(
            LiabilityRule.InsuredForm.FIXED,
            BigDecimal.ZERO,
            NO_CAP,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(occurrenceLimit),
            BigDecimal.ZERO),
        LiabilityRule.QuotaPolicy.defaults(),
        new LiabilityRule.OccurrenceSpec(basis, new BigDecimal(occurrenceLimit)),
        LiabilityRule.Relation.none(),
        LiabilityRule.LimitedConditions.disabled());
  }

  /** 两天各一张票（同一赔案、不同发票日期）。 */
  private static List<ClaimWithItemsProjection> twoRows(String first, String second) {
    return List.of(row(2001L, "IN", first, "0", "0"), row(2002L, "IN", second, "0", "0"));
  }

  private static List<OccurrenceGrouper.Fact> twoDayFacts() {
    return List.of(
        new OccurrenceGrouper.Fact("CL001", java.time.LocalDate.of(2026, 1, 1), "H1", "D1", null),
        new OccurrenceGrouper.Fact("CL001", java.time.LocalDate.of(2026, 1, 2), "H1", "D1", null));
  }

  private static LiabilityRule ruleWithRelation(String sharedCode, String priorUuid) {
    return new LiabilityRule(
        "L",
        "v1",
        LiabilityRule.LiabilityForm.REIMBURSEMENT,
        false,
        EnumSet.allOf(FeePools.Bucket.class),
        0,
        DeductibleRule.none(),
        ratios(entry(FeePools.Bucket.REASONABLE, "100", null)),
        LiabilityRule.LimitSet.liabilityOnly(NO_CAP),
        LiabilityRule.QuotaPolicy.defaults(),
        LiabilityRule.OccurrenceSpec.none(),
        new LiabilityRule.Relation(sharedCode, priorUuid),
        LiabilityRule.LimitedConditions.disabled());
  }

  private static DeductibleRule targetAbsolute(String amount, DeductibleRule.Target target) {
    return new DeductibleRule(
        true,
        DeductibleRule.Kind.ABSOLUTE,
        DeductibleRule.Basis.ANNUAL,
        DeductibleRule.Form.AMOUNT,
        DeductibleRule.Offset.MEDICAL_AND_THIRD_PARTY,
        target,
        DeductibleRule.Granularity.LIABILITY,
        new BigDecimal(amount),
        Money.ZERO,
        0,
        Map.of());
  }

  private static PayRatioTable ratios(PayRatioTable.Entry... entries) {
    return PayRatioTable.of(List.of(entries));
  }

  private static PayRatioTable.Entry entry(
      FeePools.Bucket bucket, String percent, String feeLimit) {
    return new PayRatioTable.Entry(
        bucket.name(),
        new PayRatioTable.RatioSelector(bucket, null, null, null, null, null),
        new BigDecimal(percent),
        feeLimit == null ? null : new BigDecimal(feeLimit));
  }

  /** 三条明细：合理金额 / 自付二 / 总自费（对应附录 A 的三列）。 */
  private static List<ClaimWithItemsProjection> rows(
      String reasonable, String selfPay2, String totalSelfPay) {
    return List.of(row(2001L, "IN", reasonable, selfPay2, totalSelfPay));
  }

  /** 单行携带三个费用池的金额（贴近真实票面：一张发票的三类费用在一次明细里）。 */
  private static ClaimWithItemsProjection row(
      long itemId, String socialFlag, String reasonable, String selfPay2, String totalSelfPay) {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(1L);
    p.setClaimNo("CL001");
    p.setItemId(itemId);
    p.setItemName("费用" + itemId);
    p.setItemSocialFlag(socialFlag);
    p.setItemAmount(
        new BigDecimal(reasonable).add(new BigDecimal(selfPay2)).add(new BigDecimal(totalSelfPay)));
    p.setItemValidAmount(new BigDecimal(reasonable));
    p.setItemSelfPay1(new BigDecimal(reasonable));
    p.setItemSelfPay2(new BigDecimal(selfPay2));
    p.setItemSelfPayC(new BigDecimal(totalSelfPay));
    return p;
  }

  private static ClaimWithItemsProjection headOnlyRow() {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(1L);
    p.setItemId(null);
    p.setItemValidAmount(new BigDecimal("100.00"));
    return p;
  }
}
