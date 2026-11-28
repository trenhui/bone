package com.bone.claim.application;

import com.bone.claim.application.port.out.MrmModelCardPort;
import com.bone.claim.application.port.out.MrmModelCardPort.DriftObservation;
import com.bone.claim.application.port.out.MrmModelCardPort.LoadStatus;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationReport;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.Verdict;
import com.bone.claim.domain.model.evaluation.AiModelCard;
import com.bone.claim.domain.model.evaluation.AiModelCard.DriftVerdict;
import com.bone.claim.domain.model.evaluation.AiModelCard.InventoryReport;
import com.bone.claim.domain.model.evaluation.AiModelCard.ModelCard;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * MRM 治理运行期只读查询（模型卡 / 漂移四态 / champion-challenger / 按维度偏差监测）。
 *
 * <p><b>真实内核、不另写口径</b>：模型清单与漂移判据分别调用 {@link AiModelCard#inventory} 与 {@link AiModelCard#drift}；
 * 样本来自 {@link BlindLabelTaskRepository#findUnblindedPaired} 经 {@link BlindLabelCalibrationSamples}
 * 映射，与校准报表同源。 配置无 PSI 观测时如实 {@code NO_DATA}；金标样本为 0 时如实 {@code NO_EVIDENCE}，样本不足时 {@code
 * INSUFFICIENT_SAMPLES} 且所有偏差数值/结论为 null。
 *
 * <p><b>不伪造对比</b>：本模块没有 champion / challenger 两个真实模型快照产物，只有 AI 建议与独立盲标结论。 因此本服务只给「证据可用性」与「无优劣结论」，
 * 不把盲标当成第二个模型、不调用 {@code ChampionChallengerComparator} 去拼一个自比较的胜负结论。 机构等级维度在样本载荷中没有真实字段，查询该维度返回
 * {@code DIMENSION_UNAVAILABLE} 并说明需要样本携带对应字段，绝不臆造分组。
 *
 * <p><b>只读边界</b>：无事务写、不落库、不改在线配置、不碰资金/监管签署。仓储是唯一 IO。
 */
@Service
@RequiredArgsConstructor
public class MrmGovernanceApplicationService {

  /** 单次取样的上界，沿用校准报表口径（防全量导出）。 */
  public static final int MAX_LIMIT = AiCalibrationReportApplicationService.MAX_LIMIT;

  /** 每个维度的治理下限：HTTP 入口只允许上调；超过上限按上限保护。 */
  public static final int DEFAULT_MIN_SAMPLES_PER_SLICE = 30;

  public static final int MAX_MIN_SAMPLES_PER_SLICE = 500;

  private static final String DIMENSION_CHANNEL = "CHANNEL";
  private static final String DIMENSION_DECISION_RESULT = "DECISION_RESULT";
  private static final String DIMENSION_INSTITUTION_LEVEL = "INSTITUTION_LEVEL";
  private static final int RATE_SCALE = 4;

  private final MrmModelCardPort registry;
  private final BlindLabelTaskRepository blindLabelTaskRepository;
  private final ObjectMapper mapper = new ObjectMapper();

  /** 治理总览：清单状态 + inventory + 漂移四态 + champion-challenger 证据状态。 */
  public record GovernanceReport(
      LocalDate asOf,
      String status,
      String modelCardStatus,
      List<ModelCard> modelCards,
      InventoryReport inventory,
      BigDecimal psi,
      BigDecimal driftWatchRatio,
      BigDecimal driftThreshold,
      DriftVerdict driftVerdict,
      ChampionChallengerStatus championChallenger,
      String note) {}

  /** 对比状态：conclusion 恒为 null——无真实双模型快照时不得给优劣结论。 */
  public record ChampionChallengerStatus(
      String status, int sampleCount, String evidenceVerdict, String reason, String conclusion) {}

  /** 偏差监测视图：每组样本量可见；样本不足时数值与结论一律 null。 */
  public record BiasMonitoringReport(
      String dimension,
      int effectiveMinSamplesPerSlice,
      int totalSamples,
      String status,
      List<BiasSlice> slices,
      String note) {}

  public record BiasSlice(
      String dimension,
      String sliceKey,
      int sampleCount,
      String verdict,
      Integer agreeCount,
      BigDecimal agreementRate,
      String conclusion,
      String note) {}

  /**
   * minSamplesPerSlice 的缺省与上限保护：null 取 30，任意正值可用（离线实验口径，与校准报表服务一致），超过 500 封顶。 对外 HTTP 入口的 30 治理下限由
   * {@code MrmGovernanceController.guardedMinSamples} 施加，防止调用方下调口径。
   */
  public static int effectiveMinSamplesPerSlice(Integer requested) {
    if (requested == null) {
      return DEFAULT_MIN_SAMPLES_PER_SLICE;
    }
    return Math.max(1, Math.min(requested, MAX_MIN_SAMPLES_PER_SLICE));
  }

  /** 治理总览（{@code asOf} 为 null 时由调用方补业务日）。 */
  public GovernanceReport governance(LocalDate asOf) {
    Objects.requireNonNull(asOf, "asOf 不可为 null");
    List<ModelCard> cards = registry.cards();
    InventoryReport inventory = AiModelCard.inventory(cards, asOf);
    DriftObservation drift = registry.drift();
    DriftVerdict driftVerdict = resolveDrift(drift);

    List<CalibrationSample> samples = loadSamples();
    ChampionChallengerStatus comparison = championChallengerOf(samples);

    LoadStatus loadStatus = registry.status();
    String status =
        loadStatus == LoadStatus.NO_MODEL_CARDS
            ? "NO_MODEL_CARDS"
            : loadStatus == LoadStatus.CONFIG_UNAVAILABLE
                ? "MODEL_CARDS_CONFIG_UNAVAILABLE"
                : !inventory.clean() ? "INVENTORY_BLOCKED" : "MODEL_CARDS_LOADED";
    String note =
        "只读观测：空清单/未验证卡不代表治理健康；漂移无 PSI 观测为 NO_DATA；"
            + "本模块无真实 champion/challenger 双模型快照，不给优劣结论；查询不碰资金与监管签署。";
    return new GovernanceReport(
        asOf,
        status,
        loadStatus.name(),
        cards,
        inventory,
        drift == null ? null : drift.psi(),
        drift == null ? null : drift.watchRatio(),
        drift == null ? null : drift.threshold(),
        driftVerdict,
        comparison,
        note);
  }

  /**
   * 按真实维度分组的偏差监测；{@code minSamplesPerSlice} 缺省 30，超限按上限保护。
   *
   * <p>只支持能从真实样本取到的 {@code CHANNEL} / {@code DECISION_RESULT}；其余维度（含机构等级）如实返回 {@code
   * DIMENSION_UNAVAILABLE}，不猜测、不补值。
   */
  public BiasMonitoringReport biasMonitoring(String dimension, Integer minSamplesPerSlice) {
    int effectiveMin = effectiveMinSamplesPerSlice(minSamplesPerSlice);
    String dim =
        dimension == null || dimension.isBlank()
            ? DIMENSION_CHANNEL
            : dimension.trim().toUpperCase(Locale.ROOT);
    if (!DIMENSION_CHANNEL.equals(dim) && !DIMENSION_DECISION_RESULT.equals(dim)) {
      return new BiasMonitoringReport(
          dim,
          effectiveMin,
          0,
          "DIMENSION_UNAVAILABLE",
          List.of(),
          DIMENSION_INSTITUTION_LEVEL.equals(dim)
              ? "机构等级维度当前样本载荷不含该字段，无法真实分组；需样本携带机构等级字段后才可用，绝不臆造。"
              : "该维度无法从现有盲标样本真实取得，需样本携带对应字段后才可用。");
    }

    List<BlindLabelCalibrationSamples.DimensionSample> samples = loadDimensionSamples();
    if (samples.isEmpty()) {
      return new BiasMonitoringReport(
          dim, effectiveMin, 0, "NO_EVIDENCE", List.of(), "无独立盲标成对样本：不给任何偏差数值或优劣结论。");
    }

    Map<String, List<BlindLabelCalibrationSamples.DimensionSample>> grouped = new LinkedHashMap<>();
    for (BlindLabelCalibrationSamples.DimensionSample sample : samples) {
      String key =
          DIMENSION_CHANNEL.equals(dim) ? sample.sample().sliceKey() : sample.decisionResult();
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(sample);
    }
    List<BiasSlice> slices = new ArrayList<>();
    boolean allSufficient = true;
    for (Map.Entry<String, List<BlindLabelCalibrationSamples.DimensionSample>> e :
        grouped.entrySet()) {
      int count = e.getValue().size();
      boolean sufficient = count >= effectiveMin;
      allSufficient &= sufficient;
      long agree = e.getValue().stream().filter(s -> s.sample().correct()).count();
      slices.add(
          sufficient
              ? new BiasSlice(
                  dim,
                  e.getKey(),
                  count,
                  "OBSERVABLE",
                  (int) agree,
                  rate(agree, count),
                  null,
                  "一致率为真实盲标观测值，非优劣结论。")
              : new BiasSlice(
                  dim,
                  e.getKey(),
                  count,
                  "INSUFFICIENT_SAMPLES",
                  null,
                  null,
                  null,
                  "样本量低于阈值，不给偏差数值或结论。"));
    }
    String status = allSufficient ? "OBSERVABLE" : "INSUFFICIENT_SAMPLES";
    return new BiasMonitoringReport(
        dim,
        effectiveMin,
        samples.size(),
        status,
        List.copyOf(slices),
        "按真实盲标样本分组；样本量逐组可见，低样本切片 fail-closed。");
  }

  private DriftVerdict resolveDrift(DriftObservation drift) {
    if (drift == null
        || drift.psi() == null
        || drift.watchRatio() == null
        || drift.threshold() == null) {
      // 没有观测或缺阈值：如实 NO_DATA，不另写一套阈值口径。
      return DriftVerdict.NO_DATA;
    }
    return AiModelCard.drift(drift.psi(), drift.watchRatio(), drift.threshold());
  }

  private ChampionChallengerStatus championChallengerOf(List<CalibrationSample> samples) {
    if (samples.isEmpty()) {
      return new ChampionChallengerStatus("NO_EVIDENCE", 0, null, "NO_BLIND_LABEL_PAIRS", null);
    }
    CalibrationReport calibration =
        AiConfidenceCalibration.evaluate(
            samples,
            AiCalibrationReportApplicationService.DEFAULT_CURRENT_THRESHOLD,
            AiCalibrationReportApplicationService.DEFAULT_TARGET_ERROR_RATE,
            AiCalibrationReportApplicationService.DEFAULT_MIN_SAMPLES,
            AiCalibrationReportApplicationService.DEFAULT_MIN_SAMPLES_PER_SLICE);
    Verdict verdict = calibration.verdict();
    if (verdict == Verdict.NO_EVIDENCE) {
      return new ChampionChallengerStatus("NO_EVIDENCE", 0, verdict.name(), "NO_GOLD", null);
    }
    if (verdict == Verdict.INSUFFICIENT_SAMPLES || verdict == Verdict.UNTRUSTED_EVIDENCE) {
      return new ChampionChallengerStatus(
          "INSUFFICIENT_SAMPLES", samples.size(), verdict.name(), "EVIDENCE_NOT_READY", null);
    }
    return new ChampionChallengerStatus(
        "EVIDENCE_NO_COMPARISON",
        samples.size(),
        verdict.name(),
        "CHAMPION_CHALLENGER_ARTIFACT_ABSENT",
        null);
  }

  private List<CalibrationSample> loadSamples() {
    List<BlindLabelTask> tasks = blindLabelTaskRepository.findUnblindedPaired(MAX_LIMIT);
    return BlindLabelCalibrationSamples.from(tasks, mapper).samples();
  }

  private List<BlindLabelCalibrationSamples.DimensionSample> loadDimensionSamples() {
    List<BlindLabelTask> tasks = blindLabelTaskRepository.findUnblindedPaired(MAX_LIMIT);
    return BlindLabelCalibrationSamples.fromWithDimensions(tasks, mapper);
  }

  private static BigDecimal rate(long agree, int total) {
    return BigDecimal.valueOf(agree)
        .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
  }
}
