package com.bone.claim.domain.model.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 置信度校准与阈值重定内核（G4-1 数据飞轮下游 / A0-1 评测 harness 的计算层）。
 *
 * <p><b>为什么这一层必须是可证伪的代码，而不是一段文档</b>：PRD 与 Rulebook 把 0.7 / 0.85 写成常量，
 * 阈值从「假设」变成「事实」的唯一路径是用<b>独立盲标</b>样本算 ECE / Brier，再按目标错误率反解阈值 （Google《ML Test Score》校准项、B6
 * eval-driven、NIST AI RMF MEASURE）。此前设计文档 §11.3 已把 「阈值校准」列为 L3 待办，但代码侧无消费方，导致「阈值由数据决定」始终停在文字上。
 *
 * <p><b>四道防假绿闸</b>（每道都有对应单测与变异锚点）：
 *
 * <ul>
 *   <li><b>只认独立盲标金标准</b>：弱标签（案件终态）与 AI 自评样本一律在构造期拒绝。用弱标签算校准， 等于拿「AI 说自动通过 → 系统自动通过 →
 *       终态一致」当正确，把自动化偏差喂回阈值 （Sculley《Hidden Technical Debt》hidden feedback loops）；
 *   <li><b>冻结集不得参与调优</b>：样本须显式标记 {@code frozenSetExcluded}，否则拒绝。冻结集一旦 参与阈值拟合，回归门禁就退化成自证；
 *   <li><b>空集给 null 不给 0</b>：ECE=0 是「校准完美」，null 是「没测过」。报表把 null 静默变 0 会让冷启动期看起来像模型已校准；
 *   <li><b>样本不足不重定阈值、不晋升 challenger</b>：{@code 1/1} 的 ECE 没有统计意义，小样本 重定阈值会把一次偶然差异固化成长期资金策略。
 * </ul>
 *
 * <p><b>AI 不碰钱</b>：本内核只产出 {@code recommendedThreshold} 建议与受控原因码，不写配置、
 * 不改状态机、不触发自动放款。阈值影响资金路径，落地须经产品/架构签核（§12.2）。
 *
 * <p><b>拓扑不变</b>：这是评测侧的离线计算，不进决策图、不新增节点（B2 workflow 纪律）。
 */
public final class AiConfidenceCalibration {

  /** 等宽分桶数；ECE 对分桶数敏感，固定下来才能让两次报告可比。 */
  public static final int BUCKET_COUNT = 10;

  /** 阈值扫描下界 / 上界 / 步长：覆盖资金侧实际可达的置信度区间。 */
  public static final BigDecimal THRESHOLD_FLOOR = new BigDecimal("0.50");

  public static final BigDecimal THRESHOLD_CEILING = new BigDecimal("0.95");

  public static final BigDecimal THRESHOLD_STEP = new BigDecimal("0.05");

  private static final int SCALE = 4;

  private AiConfidenceCalibration() {}

  /** 标签来源：只有独立盲标可作为校准与阈值重定的金标准。 */
  public enum LabelSource {
    /** 标注人在屏蔽 AI 痕迹后独立重判（{@code BlindLabelTask} 解盲产出）。 */
    BLIND_LABEL,
    /** 案件终态反推的弱标签（{@code EvalSampleExportApplicationService} 产出）。 */
    WEAK_FINAL_STATUS,
    /** 模型自评 / LLM-as-judge 结论（B6：专业领域一致率仅 64–68%，不得作唯一门禁）。 */
    AI_SELF_EVALUATION
  }

  /** 判据结论（顺序即优先级：先判「证据能不能用」，再判「能不能改」）。 */
  public enum Verdict {
    /** 无任何样本。 */
    NO_EVIDENCE,
    /** 样本含弱标签/自评，或冻结集未隔离：证据不可信。 */
    UNTRUSTED_EVIDENCE,
    /** 有样本但少于最小样本量。 */
    INSUFFICIENT_SAMPLES,
    /** 存在切片样本量不足：总体指标会掩盖小样本切片。 */
    SLICE_INCOMPLETE,
    /** 证据充分但无候选阈值满足目标错误率：保持现有阈值。 */
    NO_SAFE_THRESHOLD,
    /** 证据充分，产出阈值建议（仅建议，不自动生效）。 */
    THRESHOLD_PROPOSED;

    /** 是否允许据此重定阈值。 */
    public boolean permitsRetune() {
      return this == THRESHOLD_PROPOSED;
    }
  }

  /** 单条校准样本：置信度 + 盲标判定的正确性 + 切片键 + 来源 + 冻结集隔离标记。 */
  public record CalibrationSample(
      BigDecimal confidence,
      boolean correct,
      String sliceKey,
      LabelSource source,
      boolean frozenSetExcluded) {

    public CalibrationSample {
      Objects.requireNonNull(confidence, "confidence 不可为 null");
      if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException("confidence 须在 [0,1]：" + confidence);
      }
      Objects.requireNonNull(sliceKey, "sliceKey 不可为 null");
      if (sliceKey.isBlank()) {
        throw new IllegalArgumentException("sliceKey 不可为空");
      }
      Objects.requireNonNull(source, "source 不可为 null");
      // 来源与冻结集隔离由 evaluate 在入口整批校验：一条污染样本不得污染整批计算，
      // 且这样「拒绝弱标签/自评」本身有可执行的单测，而不是只活在一句注释里。
      // 这里仍保留完整字段，使样本可构造、可断言判据确实拒绝它。
    }
  }

  /** 单个置信度分桶的校准明细。 */
  public record BucketStat(
      int index, int count, BigDecimal meanConfidence, BigDecimal accuracy, BigDecimal gap) {}

  /** 单个切片的聚合明细（防总体指标掩盖小样本切片）。 */
  public record SliceStat(String sliceKey, int count, BigDecimal brierScore, BigDecimal accuracy) {}

  /** 校准报告：所有数值在证据不足时保持可观测，但 verdict 明确禁止据此改动。 */
  public record CalibrationReport(
      int sampleCount,
      int bucketCount,
      BigDecimal expectedCalibrationError,
      BigDecimal brierScore,
      List<BucketStat> buckets,
      List<SliceStat> slices,
      BigDecimal currentThreshold,
      BigDecimal recommendedThreshold,
      int coveredByRecommended,
      Verdict verdict,
      String basis) {

    public CalibrationReport {
      buckets = List.copyOf(buckets);
      slices = List.copyOf(slices);
    }
  }

  /**
   * 计算校准报告并反解阈值建议。
   *
   * @param samples 盲标成对样本；空集是合法输入，返回 {@code NO_EVIDENCE} 而非抛异常
   * @param currentThreshold 现行自动通过阈值
   * @param targetErrorRate 自动通过错误率上限（抽检口径，非全量口径）
   * @param minSamples 全局最小样本量
   * @param minSamplesPerSlice 每个切片的最小样本量
   */
  public static CalibrationReport evaluate(
      List<CalibrationSample> samples,
      BigDecimal currentThreshold,
      BigDecimal targetErrorRate,
      int minSamples,
      int minSamplesPerSlice) {
    Objects.requireNonNull(samples, "samples 不可为 null");
    Objects.requireNonNull(currentThreshold, "currentThreshold 不可为 null");
    Objects.requireNonNull(targetErrorRate, "targetErrorRate 不可为 null");
    if (minSamples < 1 || minSamplesPerSlice < 1) {
      throw new IllegalArgumentException("最小样本量须为正");
    }
    if (targetErrorRate.compareTo(BigDecimal.ZERO) < 0
        || targetErrorRate.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("targetErrorRate 须在 [0,1]");
    }

    if (samples.isEmpty()) {
      return new CalibrationReport(
          0,
          BUCKET_COUNT,
          null,
          null,
          List.of(),
          List.of(),
          currentThreshold,
          null,
          0,
          Verdict.NO_EVIDENCE,
          "无盲标样本：ECE/Brier 为 null（不是 0），不得重定阈值");
    }

    List<CalibrationSample> trusted = new ArrayList<>(samples.size());
    for (CalibrationSample s : samples) {
      if (s == null) {
        return untrusted("样本列表含 null 元素");
      }
      if (s.source() != LabelSource.BLIND_LABEL || !s.frozenSetExcluded()) {
        return untrusted("样本来源非独立盲标或未隔离冻结集");
      }
      trusted.add(s);
    }

    BigDecimal ece = expectedCalibrationError(trusted);
    BigDecimal brier = brierScore(trusted);
    List<BucketStat> buckets = buckets(trusted);
    List<SliceStat> slices = slices(trusted);

    if (trusted.size() < minSamples) {
      return report(
          trusted,
          ece,
          brier,
          buckets,
          slices,
          currentThreshold,
          null,
          0,
          Verdict.INSUFFICIENT_SAMPLES,
          "样本 " + trusted.size() + " < 最小样本量 " + minSamples + "：仅观测，不得重定阈值");
    }
    boolean anySliceThin = slices.stream().anyMatch(sl -> sl.count() < minSamplesPerSlice);
    if (anySliceThin) {
      return report(
          trusted,
          ece,
          brier,
          buckets,
          slices,
          currentThreshold,
          null,
          0,
          Verdict.SLICE_INCOMPLETE,
          "存在切片样本量不足 " + minSamplesPerSlice + "：总体指标不得掩盖小样本切片");
    }

    ThresholdCandidate best = scanThreshold(trusted, targetErrorRate);
    if (best == null) {
      return report(
          trusted,
          ece,
          brier,
          buckets,
          slices,
          currentThreshold,
          null,
          0,
          Verdict.NO_SAFE_THRESHOLD,
          "无候选阈值满足目标错误率 " + targetErrorRate + "：保持现行阈值，不得放宽");
    }
    return report(
        trusted,
        ece,
        brier,
        buckets,
        slices,
        currentThreshold,
        best.threshold(),
        best.covered(),
        Verdict.THRESHOLD_PROPOSED,
        "盲标样本反解阈值：满足错误率上限且覆盖最大；仅为建议，须签核后生效");
  }

  /**
   * champion / challenger 晋升判定（A4-1，B6 趋势比绝对值重要）。
   *
   * <p>challenger 获准晋升须<b>同时</b>满足：证据充分、Brier 不退化、ECE 不退化、无切片退化、 现行错误预算未击穿。任一不满足即保留
   * champion——避免一次抖动把生产模型换掉。
   */
  public static boolean challengerAdmissible(
      CalibrationReport champion,
      CalibrationReport challenger,
      BigDecimal errorRate,
      BigDecimal errorRateTarget) {
    Objects.requireNonNull(champion, "champion 不可为 null");
    Objects.requireNonNull(challenger, "challenger 不可为 null");
    Objects.requireNonNull(errorRate, "errorRate 不可为 null");
    Objects.requireNonNull(errorRateTarget, "errorRateTarget 不可为 null");
    if (!champion.verdict().permitsRetune() || !challenger.verdict().permitsRetune()) {
      return false;
    }
    if (errorRate.compareTo(errorRateTarget) > 0) {
      return false;
    }
    if (challenger.brierScore() == null
        || champion.brierScore() == null
        || challenger.expectedCalibrationError() == null
        || champion.expectedCalibrationError() == null) {
      return false;
    }
    if (challenger.brierScore().compareTo(champion.brierScore()) > 0) {
      return false;
    }
    if (challenger.expectedCalibrationError().compareTo(champion.expectedCalibrationError()) > 0) {
      return false;
    }
    // 切片退化：challenger 任一已有切片不得比 champion 同切片更差（新切片单独观察，不阻断）。
    Map<String, BigDecimal> championSlices = sliceBrier(champion);
    for (SliceStat sl : challenger.slices()) {
      BigDecimal before = championSlices.get(sl.sliceKey());
      if (before != null && sl.brierScore().compareTo(before) > 0) {
        return false;
      }
    }
    return true;
  }

  /** ECE：等宽分桶，按桶内样本数加权的 |平均置信度 − 实际准确率|。 */
  public static BigDecimal expectedCalibrationError(List<CalibrationSample> samples) {
    Objects.requireNonNull(samples, "samples 不可为 null");
    if (samples.isEmpty()) {
      return null;
    }
    BigDecimal weighted = BigDecimal.ZERO;
    for (BucketStat b : buckets(samples)) {
      weighted =
          weighted.add(
              b.gap()
                  .multiply(BigDecimal.valueOf(b.count()))
                  .divide(BigDecimal.valueOf(samples.size()), SCALE + 2, RoundingMode.HALF_UP));
    }
    return weighted.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** Brier Score：mean((confidence − outcome)²)，outcome = 盲标判定正确 ? 1 : 0。 */
  public static BigDecimal brierScore(List<CalibrationSample> samples) {
    Objects.requireNonNull(samples, "samples 不可为 null");
    if (samples.isEmpty()) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (CalibrationSample s : samples) {
      BigDecimal residual = s.correct() ? BigDecimal.ONE.subtract(s.confidence()) : s.confidence();
      sum = sum.add(residual.multiply(residual));
    }
    return sum.divide(BigDecimal.valueOf(samples.size()), SCALE, RoundingMode.HALF_UP);
  }

  private static List<BucketStat> buckets(List<CalibrationSample> samples) {
    @SuppressWarnings("unchecked")
    List<CalibrationSample>[] bins = new List[BUCKET_COUNT];
    for (int i = 0; i < BUCKET_COUNT; i++) {
      bins[i] = new ArrayList<>();
    }
    for (CalibrationSample s : samples) {
      int idx = s.confidence().multiply(BigDecimal.valueOf(BUCKET_COUNT)).intValue();
      // confidence == 1.0 会算出 10，归入最后一桶（边界含等于）。
      bins[Math.min(idx, BUCKET_COUNT - 1)].add(s);
    }
    List<BucketStat> result = new ArrayList<>(BUCKET_COUNT);
    for (int i = 0; i < BUCKET_COUNT; i++) {
      List<CalibrationSample> bin = bins[i];
      if (bin.isEmpty()) {
        continue;
      }
      BigDecimal confSum = BigDecimal.ZERO;
      int correct = 0;
      for (CalibrationSample s : bin) {
        confSum = confSum.add(s.confidence());
        if (s.correct()) {
          correct++;
        }
      }
      BigDecimal mean = confSum.divide(BigDecimal.valueOf(bin.size()), SCALE, RoundingMode.HALF_UP);
      BigDecimal accuracy =
          BigDecimal.valueOf(correct)
              .divide(BigDecimal.valueOf(bin.size()), SCALE, RoundingMode.HALF_UP);
      result.add(new BucketStat(i, bin.size(), mean, accuracy, mean.subtract(accuracy).abs()));
    }
    return result;
  }

  private static List<SliceStat> slices(List<CalibrationSample> samples) {
    Map<String, List<CalibrationSample>> grouped = new LinkedHashMap<>();
    for (CalibrationSample s : samples) {
      grouped.computeIfAbsent(s.sliceKey(), k -> new ArrayList<>()).add(s);
    }
    List<SliceStat> result = new ArrayList<>();
    for (Map.Entry<String, List<CalibrationSample>> e : grouped.entrySet()) {
      List<CalibrationSample> group = e.getValue();
      long correct = group.stream().filter(CalibrationSample::correct).count();
      BigDecimal accuracy =
          BigDecimal.valueOf(correct)
              .divide(BigDecimal.valueOf(group.size()), SCALE, RoundingMode.HALF_UP);
      result.add(new SliceStat(e.getKey(), group.size(), brierScore(group), accuracy));
    }
    result.sort(Comparator.comparing(SliceStat::sliceKey));
    return result;
  }

  private static Map<String, BigDecimal> sliceBrier(CalibrationReport report) {
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    for (SliceStat s : report.slices()) {
      map.put(s.sliceKey(), s.brierScore());
    }
    return map;
  }

  private record ThresholdCandidate(BigDecimal threshold, int covered) {}

  /**
   * 阈值扫描：在候选阈值中选「满足错误率上限且覆盖样本最多」的最小阈值。
   *
   * <p>为何取最小阈值：更低的阈值覆盖更多案件，等价于更宽的资金敞口，因此只在错误率达标时 才允许下探；任何不满足者一律不选。返回 null 表示连最严阈值都不达标，此时保持现行配置。
   */
  private static ThresholdCandidate scanThreshold(
      List<CalibrationSample> samples, BigDecimal targetErrorRate) {
    ThresholdCandidate best = null;
    for (BigDecimal t = THRESHOLD_FLOOR;
        t.compareTo(THRESHOLD_CEILING) <= 0;
        t = t.add(THRESHOLD_STEP)) {
      BigDecimal candidate = t;
      List<CalibrationSample> covered =
          samples.stream().filter(s -> s.confidence().compareTo(candidate) >= 0).toList();
      if (covered.isEmpty()) {
        continue;
      }
      long incorrect = covered.stream().filter(s -> !s.correct()).count();
      BigDecimal errorRate =
          BigDecimal.valueOf(incorrect)
              .divide(BigDecimal.valueOf(covered.size()), SCALE, RoundingMode.HALF_UP);
      if (errorRate.compareTo(targetErrorRate) > 0) {
        continue;
      }
      if (best == null || covered.size() > best.covered()) {
        best = new ThresholdCandidate(t, covered.size());
      }
    }
    return best;
  }

  private static CalibrationReport report(
      List<CalibrationSample> trusted,
      BigDecimal ece,
      BigDecimal brier,
      List<BucketStat> buckets,
      List<SliceStat> slices,
      BigDecimal currentThreshold,
      BigDecimal recommended,
      int covered,
      Verdict verdict,
      String basis) {
    return new CalibrationReport(
        trusted.size(),
        BUCKET_COUNT,
        ece,
        brier,
        buckets,
        slices,
        currentThreshold,
        recommended,
        covered,
        verdict,
        basis);
  }

  private static CalibrationReport untrusted(String reason) {
    return new CalibrationReport(
        0,
        BUCKET_COUNT,
        null,
        null,
        List.of(),
        List.of(),
        null,
        null,
        0,
        Verdict.UNTRUSTED_EVIDENCE,
        reason);
  }
}
