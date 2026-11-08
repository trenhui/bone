package com.bone.claim.application;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 置信度校准报表（G4-1 数据飞轮下游的生产消费方，只读）。
 *
 * <p>此前域内核 {@link AiConfidenceCalibration} 只有单测引用、没有生产消费方——「阈值由数据决定」 停在文字上。本服务把 UNBLINDED+PAIRED
 * 盲标任务映射成校准样本，产出 ECE/Brier/切片明细与阈值 建议：结论仅供人工决策（minSamples=300/perSlice=30 为请求参数缺省），绝不自动改任何在线阈值/
 * 开关（AI 不碰钱）。
 *
 * <p>组件边界：解析与样本映射是纯函数（{@link BlindLabelCalibrationSamples}）；仓储查询是唯一 IO； 本服务无事务写。样本不足输出
 * INSUFFICIENT_SAMPLES 不编造数值。
 */
@Service
@RequiredArgsConstructor
public class AiCalibrationReportApplicationService {

  /** 单次取样的任务数上限（SQL :limit 的钳制上界，防全量导出）。 */
  public static final int MAX_LIMIT = 500;

  static final BigDecimal DEFAULT_CURRENT_THRESHOLD = new BigDecimal("0.70");
  static final BigDecimal DEFAULT_TARGET_ERROR_RATE = new BigDecimal("0.05");
  public static final int DEFAULT_MIN_SAMPLES = 300;
  public static final int DEFAULT_MIN_SAMPLES_PER_SLICE = 30;

  private final BlindLabelTaskRepository blindLabelTaskRepository;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * 校准报表（{@code calibration} 为域内核报告原样透传，字段语义见域内核 javadoc）。
   *
   * <p>回传实际生效的最小样本量，供审计与端到端验证「治理下限未被调用方下调」。
   */
  public record AiCalibrationReport(
      int requestedLimit,
      int fetchedTasks,
      int sampleCount,
      int unparseableSamples,
      AiConfidenceCalibration.CalibrationReport calibration,
      int effectiveMinSamples,
      int effectiveMinSamplesPerSlice) {}

  @Transactional(readOnly = true)
  public AiCalibrationReport report(Integer minSamples, Integer minSamplesPerSlice) {
    List<BlindLabelTask> tasks = blindLabelTaskRepository.findUnblindedPaired(MAX_LIMIT);
    BlindLabelCalibrationSamples.Mapping mapping = BlindLabelCalibrationSamples.from(tasks, mapper);
    int effectiveMin = minSamples == null ? DEFAULT_MIN_SAMPLES : minSamples;
    int effectiveSliceMin =
        minSamplesPerSlice == null ? DEFAULT_MIN_SAMPLES_PER_SLICE : minSamplesPerSlice;
    AiConfidenceCalibration.CalibrationReport calibration =
        AiConfidenceCalibration.evaluate(
            mapping.samples(),
            DEFAULT_CURRENT_THRESHOLD,
            DEFAULT_TARGET_ERROR_RATE,
            effectiveMin,
            effectiveSliceMin);
    return new AiCalibrationReport(
        MAX_LIMIT,
        tasks.size(),
        calibration.sampleCount(),
        mapping.unparseableSamples(),
        calibration,
        effectiveMin,
        effectiveSliceMin);
  }
}
