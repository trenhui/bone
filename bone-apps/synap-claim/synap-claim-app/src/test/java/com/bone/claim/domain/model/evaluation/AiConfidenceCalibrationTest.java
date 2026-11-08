package com.bone.claim.domain.model.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationReport;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.LabelSource;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.Verdict;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 置信度校准内核判据测试。
 *
 * <p>最要紧的四条：空集给 null、非盲标样本整批拒、小样本不重定阈值、阈值只按「满足错误率上限 且覆盖最大」反解。每条都配了会转红的反向锚点（变异验证见本轮复核记录）。
 */
class AiConfidenceCalibrationTest {

  private static final BigDecimal CURRENT = new BigDecimal("0.85");
  private static final BigDecimal TARGET = new BigDecimal("0.02");
  private static final int MIN = 30;
  private static final int MIN_SLICE = 10;

  private static CalibrationSample blind(double confidence, boolean correct, String slice) {
    return new CalibrationSample(
        BigDecimal.valueOf(confidence), correct, slice, LabelSource.BLIND_LABEL, true);
  }

  private static List<CalibrationSample> cleanBatch(int perSlice) {
    List<CalibrationSample> list = new ArrayList<>();
    for (int i = 0; i < perSlice; i++) {
      list.add(blind(0.92, true, "OUTPATIENT"));
      list.add(blind(0.92, true, "INPATIENT"));
    }
    return list;
  }

  @Test
  @DisplayName("空集 → ECE/Brier/建议阈值全 null，NO_EVIDENCE，不得重定")
  void emptyGivesNullNotZero() {
    CalibrationReport report =
        AiConfidenceCalibration.evaluate(List.of(), CURRENT, TARGET, MIN, MIN_SLICE);

    assertThat(report.expectedCalibrationError()).isNull();
    assertThat(report.brierScore()).isNull();
    assertThat(report.recommendedThreshold()).isNull();
    assertThat(report.verdict()).isEqualTo(Verdict.NO_EVIDENCE);
    assertThat(report.verdict().permitsRetune()).isFalse();
  }

  @Test
  @DisplayName("弱标签（案件终态）样本整批拒——防 hidden feedback loop")
  void weakFinalStatusLabelIsRejected() {
    CalibrationSample weak =
        new CalibrationSample(
            BigDecimal.valueOf(0.9), true, "OUTPATIENT", LabelSource.WEAK_FINAL_STATUS, true);

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(List.of(weak), CURRENT, TARGET, 1, 1);

    assertThat(report.verdict()).isEqualTo(Verdict.UNTRUSTED_EVIDENCE);
    assertThat(report.expectedCalibrationError()).isNull();
    assertThat(report.basis()).contains("非独立盲标");
  }

  @Test
  @DisplayName("AI 自评 / LLM-as-judge 样本整批拒——B6 不得作唯一门禁")
  void selfEvaluatedLabelIsRejected() {
    CalibrationSample self =
        new CalibrationSample(
            BigDecimal.valueOf(0.9), true, "OUTPATIENT", LabelSource.AI_SELF_EVALUATION, true);

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(List.of(self), CURRENT, TARGET, 1, 1);

    assertThat(report.verdict()).isEqualTo(Verdict.UNTRUSTED_EVIDENCE);
  }

  @Test
  @DisplayName("冻结集未隔离的样本整批拒——冻结集参与调优会使回归门禁自证")
  void frozenSetLeakIsRejected() {
    CalibrationSample leaked =
        new CalibrationSample(
            BigDecimal.valueOf(0.9), true, "OUTPATIENT", LabelSource.BLIND_LABEL, false);

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(List.of(leaked), CURRENT, TARGET, 1, 1);

    assertThat(report.verdict()).isEqualTo(Verdict.UNTRUSTED_EVIDENCE);
  }

  @Test
  @DisplayName("null 元素不得被静默跳过")
  void nullElementIsRejected() {
    CalibrationReport report =
        AiConfidenceCalibration.evaluate(Collections.singletonList(null), CURRENT, TARGET, 1, 1);

    assertThat(report.verdict()).isEqualTo(Verdict.UNTRUSTED_EVIDENCE);
    assertThat(report.basis()).contains("null");
  }

  @Test
  @DisplayName("confidence 越界 / sliceKey 空 / source 为 null 在构造期拒绝")
  void malformedSampleRejectedAtConstruction() {
    assertThatThrownBy(
            () ->
                new CalibrationSample(
                    BigDecimal.valueOf(1.01), true, "S", LabelSource.BLIND_LABEL, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CalibrationSample(
                    BigDecimal.valueOf(-0.01), true, "S", LabelSource.BLIND_LABEL, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CalibrationSample(
                    BigDecimal.valueOf(0.9), true, " ", LabelSource.BLIND_LABEL, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CalibrationSample(BigDecimal.valueOf(0.9), true, "S", null, true))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("ECE / Brier 手算对账：全 0.92 且全对 ⇒ ECE=|0.92-1|=0.0800，Brier=0.0064")
  void eceAndBrierMatchHandCalculation() {
    List<CalibrationSample> batch = cleanBatch(20);

    // 0.92 落第 9 桶（int(0.92*10)=9），桶内均值 0.92、准确率 1.0 ⇒ gap 0.08；
    // Brier = (1-0.92)^2 = 0.0064。断言写死手算值，实现若退化为常量即会转红。
    assertThat(AiConfidenceCalibration.expectedCalibrationError(batch))
        .isEqualByComparingTo("0.0800");
    assertThat(AiConfidenceCalibration.brierScore(batch)).isEqualByComparingTo("0.0064");
  }

  @Test
  @DisplayName("完美校准样本（0.9 中 9 对 1 错且错者置信度 0）ECE 应低于全高置信错误样本")
  void eceDiscriminatesCalibrationQuality() {
    List<CalibrationSample> calibrated = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      calibrated.add(blind(1.0, true, "S"));
    }
    calibrated.add(blind(0.0, false, "S"));

    List<CalibrationSample> overconfident = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      overconfident.add(blind(1.0, true, "S"));
    }
    overconfident.add(blind(1.0, false, "S"));

    assertThat(AiConfidenceCalibration.expectedCalibrationError(calibrated))
        .isLessThan(AiConfidenceCalibration.expectedCalibrationError(overconfident));
  }

  @Test
  @DisplayName("confidence=1.0 落最后一桶，不越界；分桶样本数之和 == 样本总数")
  void bucketBoundaryAndCountInvariant() {
    List<CalibrationSample> batch = cleanBatch(20);
    CalibrationReport report =
        AiConfidenceCalibration.evaluate(batch, CURRENT, TARGET, MIN, MIN_SLICE);

    int sum = report.buckets().stream().mapToInt(b -> b.count()).sum();
    assertThat(sum).isEqualTo(batch.size());
    assertThat(report.buckets()).allSatisfy(b -> assertThat(b.index()).isBetween(0, 9));
  }

  @Test
  @DisplayName("样本不足 → INSUFFICIENT_SAMPLES，指标可观测但不得重定阈值")
  void insufficientSamplesNeverRetune() {
    List<CalibrationSample> batch = cleanBatch(5);

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(batch, CURRENT, TARGET, MIN, MIN_SLICE);

    assertThat(report.verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLES);
    assertThat(report.expectedCalibrationError()).isNotNull();
    assertThat(report.recommendedThreshold()).isNull();
    assertThat(report.verdict().permitsRetune()).isFalse();
  }

  @Test
  @DisplayName("总体达标但某切片样本不足 → SLICE_INCOMPLETE，不得用总体掩盖小切片")
  void thinSliceBlocksRetune() {
    List<CalibrationSample> batch = cleanBatch(20);
    batch.add(blind(0.92, true, "RARE"));

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(batch, CURRENT, TARGET, MIN, MIN_SLICE);

    assertThat(report.verdict()).isEqualTo(Verdict.SLICE_INCOMPLETE);
    assertThat(report.recommendedThreshold()).isNull();
  }

  @Test
  @DisplayName("全部样本判错 → 无候选阈值满足错误率上限，保持现行阈值不放宽")
  void noSafeThresholdRetainsCurrent() {
    List<CalibrationSample> batch = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      batch.add(blind(0.95, false, "OUTPATIENT"));
      batch.add(blind(0.95, false, "INPATIENT"));
    }

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(batch, CURRENT, TARGET, MIN, MIN_SLICE);

    assertThat(report.verdict()).isEqualTo(Verdict.NO_SAFE_THRESHOLD);
    assertThat(report.recommendedThreshold()).isNull();
    assertThat(report.basis()).contains("保持现行阈值");
  }

  @Test
  @DisplayName("正常批：反解出满足错误率上限且覆盖最大的最小阈值")
  void thresholdIsDerivedFromBlindLabels() {
    List<CalibrationSample> batch = cleanBatch(20);

    CalibrationReport report =
        AiConfidenceCalibration.evaluate(batch, CURRENT, TARGET, MIN, MIN_SLICE);

    assertThat(report.verdict()).isEqualTo(Verdict.THRESHOLD_PROPOSED);
    assertThat(report.recommendedThreshold()).isEqualByComparingTo("0.50");
    assertThat(report.coveredByRecommended()).isEqualTo(batch.size());
    assertThat(report.recommendedThreshold()).isLessThan(CURRENT);
    assertThat(report.verdict().permitsRetune()).isTrue();
    assertThat(report.basis()).contains("须签核后生效");
  }

  @Test
  @DisplayName("threshold 参数非法（负目标错误率 / 0 最小样本）构造期拒绝")
  void invalidParametersRejected() {
    assertThatThrownBy(
            () ->
                AiConfidenceCalibration.evaluate(
                    List.of(), CURRENT, new BigDecimal("-0.1"), MIN, MIN_SLICE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> AiConfidenceCalibration.evaluate(List.of(), CURRENT, TARGET, 0, MIN_SLICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("champion/challenger：证据不足 / Brier 退化 / ECE 退化 / 切片退化 / 错误预算击穿均不晋升")
  void challengerAdmissibleGuards() {
    List<CalibrationSample> good = cleanBatch(20);
    CalibrationReport champion =
        AiConfidenceCalibration.evaluate(good, CURRENT, TARGET, MIN, MIN_SLICE);
    BigDecimal zero = BigDecimal.ZERO;

    CalibrationReport insufficient =
        AiConfidenceCalibration.evaluate(cleanBatch(2), CURRENT, TARGET, MIN, MIN_SLICE);
    assertThat(AiConfidenceCalibration.challengerAdmissible(champion, insufficient, zero, TARGET))
        .isFalse();

    CalibrationReport worseBrier =
        new CalibrationReport(
            40,
            10,
            new BigDecimal("0.0500"),
            new BigDecimal("0.9999"),
            champion.buckets(),
            champion.slices(),
            CURRENT,
            new BigDecimal("0.50"),
            40,
            Verdict.THRESHOLD_PROPOSED,
            "test");
    assertThat(AiConfidenceCalibration.challengerAdmissible(champion, worseBrier, zero, TARGET))
        .isFalse();

    CalibrationReport worseEce =
        new CalibrationReport(
            40,
            10,
            new BigDecimal("0.9999"),
            new BigDecimal("0.0100"),
            champion.buckets(),
            champion.slices(),
            CURRENT,
            new BigDecimal("0.50"),
            40,
            Verdict.THRESHOLD_PROPOSED,
            "test");
    assertThat(AiConfidenceCalibration.challengerAdmissible(champion, worseEce, zero, TARGET))
        .isFalse();

    assertThat(
            AiConfidenceCalibration.challengerAdmissible(
                champion, champion, new BigDecimal("0.10"), TARGET))
        .isFalse();

    // 完全同质的两份报告：证据充分、无退化、无预算击穿 ⇒ 允许进入晋升评审（不是自动晋升）。
    assertThat(AiConfidenceCalibration.challengerAdmissible(champion, champion, zero, TARGET))
        .isTrue();
  }

  @Test
  @DisplayName("切片退化单独拦截：challenger 同切片 Brier 变差不许晋升")
  void sliceRegressionBlocksPromotion() {
    List<CalibrationSample> good = cleanBatch(20);
    CalibrationReport champion =
        AiConfidenceCalibration.evaluate(good, CURRENT, TARGET, MIN, MIN_SLICE);

    List<AiConfidenceCalibration.SliceStat> degraded = new ArrayList<>(champion.slices());
    degraded.set(
        0,
        new AiConfidenceCalibration.SliceStat(
            degraded.get(0).sliceKey(),
            degraded.get(0).count(),
            degraded.get(0).brierScore().add(BigDecimal.ONE),
            degraded.get(0).accuracy()));
    CalibrationReport challenger =
        new CalibrationReport(
            40,
            10,
            champion.expectedCalibrationError(),
            champion.brierScore(),
            champion.buckets(),
            degraded,
            CURRENT,
            new BigDecimal("0.50"),
            40,
            Verdict.THRESHOLD_PROPOSED,
            "test");

    assertThat(
            AiConfidenceCalibration.challengerAdmissible(
                champion, challenger, BigDecimal.ZERO, TARGET))
        .isFalse();
  }
}
