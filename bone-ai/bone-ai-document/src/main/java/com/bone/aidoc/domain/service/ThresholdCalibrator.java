package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.CalibrationReport;
import com.bone.aidoc.domain.model.CalibrationSample;
import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.LevelCalibration;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewDecision;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.RiskThreshold;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 离线阈值标定器（ADR-0046 PoC 第一优先级指标：关键字段错误放行率）。
 *
 * <p><b>口径一致性</b>：判定「某样本是否会被自动放行」时直接调用运行时的 {@link DefaultReviewPolicy#decide}，
 * 而不是另写一套阈值比较——否则标定出的阈值放进生产策略后行为不同，等于「标定白做」。
 *
 * <p><b>统计纪律</b>：验收目标（默认自动通过字段错误率 ≤ 0.01%）用 Wilson 95% 置信上界判定， 而非点估计。零错误的小样本上界仍很高，故 {@code
 * statisticallyProven=false}；同时给出 「零错误情形下证明该目标所需的最小自动通过样本数」，让样本量缺口可量化。
 *
 * <p>本工具只产出<b>建议阈值与证据</b>，不改写任何运行时配置；是否采纳由人结合样本代表性裁定。
 */
public class ThresholdCalibrator {

  /** ADR-0046 验收目标：自动通过字段错误率 ≤ 0.01%。 */
  public static final double DEFAULT_TARGET_FALSE_ACCEPT_RATE = 0.0001;

  /** 95% 双侧置信的 z 值。 */
  private static final double Z_95 = 1.959963984540054;

  private static final double[] CONF_GRID = grid(0.50, 1.00, 0.02);
  private static final double[] QUALITY_GRID = grid(0.00, 1.00, 0.05);

  private final double targetRate;

  public ThresholdCalibrator() {
    this(DEFAULT_TARGET_FALSE_ACCEPT_RATE);
  }

  /** 传入更宽松的目标可做探索性标定；低于 ADR 验收线的目标不得用于生产。 */
  public ThresholdCalibrator(double targetFalseAcceptRate) {
    if (Double.isNaN(targetFalseAcceptRate)
        || targetFalseAcceptRate <= 0.0
        || targetFalseAcceptRate >= 1.0) {
      throw new IllegalArgumentException("错误放行率目标须在 (0,1): " + targetFalseAcceptRate);
    }
    this.targetRate = targetFalseAcceptRate;
  }

  /**
   * Wilson score interval 的 95% 上界。
   *
   * <p>n=0 时返回 1.0（没有任何自动通过样本 ⇒ 无法证明任何错误率目标），这是「零样本零错误不可宣称达标」的数学落地。
   */
  public static double wilsonUpperBound(int falseAccepts, int autoPassed) {
    if (autoPassed <= 0) {
      return 1.0;
    }
    double n = autoPassed;
    double p = (double) falseAccepts / n;
    double z2 = Z_95 * Z_95;
    double denominator = 1.0 + z2 / n;
    double center = p + z2 / (2.0 * n);
    double margin = Z_95 * Math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n));
    return Math.min(1.0, (center + margin) / denominator);
  }

  /** 零错误情形下使 Wilson 上界不超过目标所需的最小自动通过样本数（二分求解）。 */
  public static int minimumSampleSizeForZeroErrors(double target) {
    long lo = 1;
    long hi = 10_000_000L;
    while (lo < hi) {
      long mid = (lo + hi) / 2;
      if (wilsonUpperBound(0, (int) mid) <= target) {
        hi = mid;
      } else {
        lo = mid + 1;
      }
    }
    return (int) lo;
  }

  public CalibrationReport calibrate(List<CalibrationSample> samples) {
    if (samples == null || samples.isEmpty()) {
      return CalibrationReport.empty(targetRate);
    }
    Map<RiskLevel, List<CalibrationSample>> grouped = new EnumMap<>(RiskLevel.class);
    for (CalibrationSample s : samples) {
      grouped.computeIfAbsent(s.riskLevel(), k -> new ArrayList<>()).add(s);
    }
    Map<RiskLevel, LevelCalibration> byLevel = new EnumMap<>(RiskLevel.class);
    grouped.forEach((level, list) -> byLevel.put(level, calibrateLevel(level, list)));
    int minSamplesPerLevel = minimumSampleSizeForZeroErrors(targetRate);
    return new CalibrationReport(
        targetRate, samples.size(), byLevel, warnings(samples.size(), byLevel, minSamplesPerLevel));
  }

  private LevelCalibration calibrateLevel(RiskLevel level, List<CalibrationSample> samples) {
    int required = minimumSampleSizeForZeroErrors(targetRate);
    Candidate proven = null;
    Candidate observed = null;
    for (double c : CONF_GRID) {
      for (double q : QUALITY_GRID) {
        RiskThreshold threshold = new RiskThreshold(c, q, c, q);
        DefaultReviewPolicy policy = new DefaultReviewPolicy(Map.of(level, threshold));
        int auto = 0;
        int bad = 0;
        for (CalibrationSample s : samples) {
          if (wouldAutoPass(s, policy)) {
            auto++;
            if (!s.correct()) {
              bad++;
            }
          }
        }
        if (auto == 0) {
          continue;
        }
        Candidate candidate = new Candidate(threshold, auto, bad, wilsonUpperBound(bad, auto));
        // 观测最优：错误放行率更低优先，其次自动通过更多、阈值更保守
        if (observed == null || betterObserved(candidate, observed)) {
          observed = candidate;
        }
        // 统计达标：Wilson 上界不超过目标，其中自动通过最多
        if (candidate.upper <= targetRate
            && (proven == null
                || candidate.auto > proven.auto
                || (candidate.auto == proven.auto
                    && candidate.threshold.autoPassConfidence()
                        > proven.threshold.autoPassConfidence()))) {
          proven = candidate;
        }
      }
    }

    boolean noSafeThreshold = observed != null && observed.bad > 0 && proven == null;
    RiskThreshold suggested =
        proven != null
            ? proven.threshold
            : (observed != null && !noSafeThreshold
                ? observed.threshold
                : new RiskThreshold(1.0, 1.0, 1.0, 1.0));
    Candidate result = proven != null ? proven : observed;
    int auto = result == null ? 0 : result.auto;
    int bad = result == null ? 0 : result.bad;
    double upper = result == null ? 1.0 : result.upper;
    return new LevelCalibration(
        level,
        suggested,
        samples.size(),
        auto,
        bad,
        auto == 0 ? 0.0 : (double) bad / auto,
        upper,
        proven != null,
        required,
        noSafeThreshold);
  }

  private static boolean betterObserved(Candidate a, Candidate b) {
    double ra = (double) a.bad / a.auto;
    double rb = (double) b.bad / b.auto;
    if (Double.compare(ra, rb) != 0) {
      return ra < rb;
    }
    if (a.auto != b.auto) {
      return a.auto > b.auto;
    }
    return a.threshold.autoPassConfidence() + a.threshold.autoPassQuality()
        > b.threshold.autoPassConfidence() + b.threshold.autoPassQuality();
  }

  /** 关键：复用运行时策略，保证标定口径与放行口径完全一致。 */
  private static boolean wouldAutoPass(CalibrationSample s, DefaultReviewPolicy policy) {
    OcrFieldEvidence evidence =
        new OcrFieldEvidence(
            s.fieldName(),
            "calibration-value",
            null,
            1,
            null,
            "calibration",
            s.confidence(),
            ConfidenceSource.OCR_ENGINE,
            ValidationStatus.PASSED,
            ReviewStatus.PENDING);
    FieldReviewContext ctx =
        new FieldReviewContext(evidence, s.qualityScore(), s.riskLevel(), CrossCheck.NOT_RUN);
    return policy.decide(ctx) == ReviewDecision.AUTO_PASS;
  }

  private List<String> warnings(
      int totalSamples, Map<RiskLevel, LevelCalibration> byLevel, int minSamplesPerLevel) {
    List<String> w = new ArrayList<>();
    if (totalSamples < 1000) {
      w.add("样本量 " + totalSamples + " < 1000，代表性不足；结论仅供开发自测");
    }
    byLevel.forEach(
        (level, c) -> {
          if (!c.statisticallyProven()) {
            w.add(
                level
                    + " 未通过统计证明（Wilson 95% 上界 "
                    + String.format("%.5f", c.wilsonUpperBound())
                    + " > 目标 "
                    + String.format("%.5f", targetRate)
                    + "；零错误也需至少 "
                    + c.minSamplesRequired()
                    + " 个自动通过字段样本），禁止作为生产阈值");
          }
          if (c.noSafeThreshold()) {
            w.add(level + " 所有候选阈值均存在错误放行样本，应强制人工复核或补充 L2 确定性规则");
          }
        });
    if (!isProductionReady(byLevel, minSamplesPerLevel)) {
      w.add(
          "标定报告未达生产就绪：四风险等级须全部通过统计证明且样本量≥Wilson最小样本("
              + minSamplesPerLevel
              + ")；未就绪的阈值不得作为生产配置（须重新标定或补充样本）");
    }
    return List.copyOf(w);
  }

  /** 与 {@code CalibrationReport.productionReady} 同口径：四等级全证明且样本量达标。 */
  private static boolean isProductionReady(
      Map<RiskLevel, LevelCalibration> byLevel, int minSamplesPerLevel) {
    if (byLevel.size() != RiskLevel.values().length) {
      return false;
    }
    return byLevel.values().stream()
        .allMatch(c -> c.statisticallyProven() && c.totalSamples() >= minSamplesPerLevel);
  }

  private record Candidate(RiskThreshold threshold, int auto, int bad, double upper) {}

  private static double[] grid(double from, double to, double step) {
    int count = (int) Math.floor((to - from) / step + 1e-9) + 1;
    double[] values = new double[count];
    for (int i = 0; i < count; i++) {
      values[i] = Math.min(to, from + i * step);
    }
    return values;
  }
}
