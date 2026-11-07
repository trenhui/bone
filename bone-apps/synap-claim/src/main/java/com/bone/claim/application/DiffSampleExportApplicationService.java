package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.InspectionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 后督 DIFF 回流样本导出（零 DDL 的「可回溯事实源」投影）。
 *
 * <p><b>为什么读已落库的抽检单，而不是消费内存事件</b>： {@code InspectionApplicationService.conclude()} 已把 DIFF
 * 结论与明细同业务事务原子落库到 {@code t_claim_inspection}；而 {@code SpringDomainEventPublisher} 只做进程内 publish，
 * 事件重启即丢、多实例互不相通。以落库记录为事实源，回流视图才具备可回溯、可按时间窗 重放、可审计的性质，且不需要新增表/迁移（避开 L3 DDL 审批边界）。
 *
 * <p><b>诚实边界</b>：导出的是「后督差异索引」，<b>不是独立盲标金标准</b>（迁移 0122 明确要求二者物理分离：抽检员在流程内看得到 AI 建议，其结论不能当金标准）；
 * 不含任何准确率 / 一致率 / 自动化率数值；无样本时返回空列表，绝不编造 0% 或占位行。 真正的「差异 → 评测集消费 → 盲标 → 阈值校准」仍属 Phase B /
 * V2，不得据此宣称闭环完成。
 *
 * <p><b>为什么单独一个端点而不并入 {@code EvalSampleExportApplicationService}</b>： 后者导出「AI 建议 vs 案件终态」弱标签，字段语义来自
 * {@code DecisionExecution}+{@code ClaimCase}； 往它的 {@code EvalSampleExport} record
 * 里加字段会改公共签名。故用独立响应类型。
 */
@Service
@RequiredArgsConstructor
public class DiffSampleExportApplicationService {

  /** 与 SQL 模板 LIMIT 一致；打满即标 truncated，不静默裁剪。 */
  public static final int MAX_SAMPLES = 500;

  /** 与评测导出同一护栏口径：离线批处理入口，跨月全量扫描等于拖库后门。 */
  public static final int MAX_WINDOW_DAYS = 31;

  private final InspectionRepository inspectionRepository;

  /** 单条回流样本索引（不含长文本明细，按 inspectionNo 回查既有表即可）。 */
  public record Sample(
      String inspectionNo,
      Long claimId,
      String diffType,
      String sampleType,
      String action,
      String fingerprint,
      Instant inspectedAt) {}

  public record Export(
      Instant windowFrom,
      Instant windowTo,
      int requestedLimit,
      int returned,
      boolean truncated,
      List<Sample> samples) {
    public Export {
      samples = samples == null ? List.of() : List.copyOf(samples);
    }
  }

  public Export export(Instant from, Instant to, int limit) {
    requireWindow(from, to);
    requireLimit(limit);
    List<InspectionRecord> records = inspectionRepository.findDiffConcludedInWindow(from, to);
    List<InspectionRecord> safe = records == null ? List.of() : records;
    boolean truncated = safe.size() >= MAX_SAMPLES;
    List<Sample> projected = safe.stream().limit(limit).map(this::toSample).toList();
    return new Export(from, to, limit, projected.size(), truncated, projected);
  }

  private Sample toSample(InspectionRecord record) {
    String diffType = nullToEmpty(record.getDiffType());
    return new Sample(
        nullToEmpty(record.getInspectionNo()),
        record.getClaimId(),
        diffType,
        sampleTypeOf(diffType),
        nullToEmpty(record.getActionTaken()),
        fingerprintOf(record),
        record.getInspectedAt());
  }

  /** 三类归因与 {@code AiEvalSampleCollectedEvent.SampleType} 保持同口径，避免两处漂移。 */
  private static String sampleTypeOf(String diffType) {
    if ("AI_MISJUDGE".equals(diffType)) {
      return "EVAL_SET";
    }
    if ("QUOTA_ABNORMAL".equals(diffType)) {
      return "CONTROL_FIX";
    }
    return "RULE_FIX";
  }

  /**
   * 幂等指纹：同租户 + 同抽检单 + 同差异类型只应产生一个样本（16 位 hex）。
   *
   * <p>与事件的 {@code SHA-256(tenantId:inspectionNo:diffType)} 取前 8 字节口径一致。
   */
  private static String fingerprintOf(InspectionRecord record) {
    String raw =
        record.getTenantId()
            + ":"
            + nullToEmpty(record.getInspectionNo())
            + ":"
            + nullToEmpty(record.getDiffType());
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用，无法生成回流样本幂等指纹", ex);
    }
  }

  private static void requireWindow(Instant from, Instant to) {
    if (from == null || to == null) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID, "时间窗必填：from 与 to 均为 ISO-8601 时刻");
    }
    if (!from.isBefore(to)) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID, "时间窗非法：from 必须早于 to");
    }
    if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_WINDOW_DAYS)) > 0) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID,
          "时间窗过大：最多 " + MAX_WINDOW_DAYS + " 天（当前 " + Duration.between(from, to).toDays() + " 天）");
    }
  }

  private static void requireLimit(int limit) {
    if (limit < 1 || limit > MAX_SAMPLES) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID,
          "limit 越界：允许 1.." + MAX_SAMPLES + "，当前 " + limit);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
