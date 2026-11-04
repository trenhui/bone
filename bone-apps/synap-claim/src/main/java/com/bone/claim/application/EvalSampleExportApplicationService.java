package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 评测样本导出（AI 原生「三方消费」里评测侧的出口）。
 *
 * <p><b>为什么需要它</b>：AI 的结构化认知已能驱动流程（业务侧消费）、已落决策轨迹（审计侧消费），但 <b>没有任何通道把「AI
 * 判断过什么、最后实际发生了什么」变成评测样本</b>。没有这条通道，冻结集只能是 手写骨架，准确率永远停在 {@code declared}——评测侧等于零消费。
 *
 * <p><b>导出的是「弱标签」，不是金标准</b>：这里的标签来自案件最终状态（{@code FINISHED} / {@code
 * CLOSED_REJECTED}），它混合了人工判断、时效、客户撤诉等因素，<b>不等于核赔员的独立判定</b>。 因此本服务只做「AI 建议 vs
 * 最终结果」的一致性统计，供基线参考与样本筛选；真正的金标准需要 G4 的独立盲标 （人工在不看 AI 建议的前提下重判）。把弱标签当金标准用，会重演 Hidden Feedback
 * Loops——人工复核受 AI 建议影响，偏差被当成 ground truth 放大。
 *
 * <p><b>红线</b>：只读导出，不写任何业务表；不含可直接识别到人的字段（无报案号、无被保人假名化 ID）； 导出标记 {@code eval-only}，禁止回流训练（与冻结集的
 * train/tune 互斥约束对齐）。
 */
@Service
@RequiredArgsConstructor
public class EvalSampleExportApplicationService {

  /** 单次导出上限：与 SQL 模板里的 LIMIT 保持一致，服务层再按请求条数截断。 */
  public static final int MAX_SAMPLES = 500;

  /** 时间窗上限：评测导出是离线批处理入口，允许跨月全量扫描等于给了个拖库后门。 */
  public static final int MAX_WINDOW_DAYS = 31;

  /** 样本键的域分隔前缀（非密钥）：只用于防止对雪花 ID 的直接反查，不宣称密码学匿名化。 */
  private static final String SAMPLE_KEY_SALT = "bone-claim-eval-v1:";

  private static final DateTimeFormatter ISO_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private final DecisionExecutionRepository decisionExecutionRepository;
  private final ClaimCaseRepository claimCaseRepository;

  /**
   * 导出指定时间窗内的评测样本。
   *
   * @param from 窗口起点（含）；不得晚于 {@code to}
   * @param to 窗口终点（不含）
   * @param limit 期望条数（1..{@link #MAX_SAMPLES}）；实际可能少于请求值
   * @throws IllegalArgumentException 参数越界（由 Controller 转为 400，不静默裁剪）
   */
  public EvalSampleExport export(Instant from, Instant to, int limit) {
    requireWindow(from, to);
    requireLimit(limit);

    List<DecisionExecution> executions =
        decisionExecutionRepository.findConvergedInWindow(from, to);
    if (executions == null || executions.isEmpty()) {
      return EvalSampleExport.empty(from, to, limit);
    }

    List<Long> claimIds =
        executions.stream().map(DecisionExecution::getClaimId).filter(Objects::nonNull).toList();
    Map<Long, ClaimCase> casesById = new LinkedHashMap<>();
    if (!claimIds.isEmpty()) {
      // findByIds 由 SDK 自动排除软删行；软删案件的状态与金额可能已被清理，参与统计会污染基线。
      List<ClaimCase> cases = claimCaseRepository.findByIds(claimIds);
      if (cases != null) {
        for (ClaimCase claim : cases) {
          if (claim != null) {
            casesById.put(claim.getId(), claim);
          }
        }
      }
    }

    List<EvalSampleExport.Sample> samples = new ArrayList<>();
    boolean truncated = false;
    for (DecisionExecution execution : executions) {
      if (samples.size() >= limit) {
        truncated = true;
        break;
      }
      ClaimCase claim = casesById.get(execution.getClaimId());
      if (claim == null) {
        // 案件缺失（跨库/已物理清理）时不猜测标签，直接跳过，避免向评测集注入无标签噪声。
        continue;
      }
      samples.add(toSample(execution, claim));
    }

    return new EvalSampleExport(
        from, to, limit, samples.size(), truncated, hashSamples(samples), samples);
  }

  /** AI 建议与最终结果的一致性判定（纯函数，供单测与变异验证）。 */
  static Agreement judge(String aiDecisionResult, String finalStatus) {
    if (aiDecisionResult == null || finalStatus == null) {
      return Agreement.UNKNOWN_NOT_CONVERGED;
    }
    boolean finished = "FINISHED".equals(finalStatus);
    boolean rejected = "CLOSED_REJECTED".equals(finalStatus);
    if (!finished && !rejected) {
      return Agreement.UNKNOWN_NOT_CONVERGED;
    }
    if ("AUTO_APPROVE".equals(aiDecisionResult)) {
      return finished ? Agreement.AGREE : Agreement.DISAGREE;
    }
    if ("SUGGEST_REJECT".equals(aiDecisionResult)) {
      return rejected ? Agreement.AGREE : Agreement.DISAGREE;
    }
    // MANUAL_REQUIRED / NEED_MORE_EVIDENCE：AI 没有给出可比较的二元结论。
    // 这里绝不能算「一致」——把「AI 没判断」记成「AI 判断对了」是准确率造假的经典形态。
    return Agreement.UNKNOWN_NO_BINARY_VERDICT;
  }

  private static EvalSampleExport.Sample toSample(DecisionExecution execution, ClaimCase claim) {
    String finalStatus = claim.getStatus();
    Agreement agreement = judge(execution.getDecisionResult(), finalStatus);
    return new EvalSampleExport.Sample(
        sampleKey(execution.getClaimId()),
        claim.getClaimType(),
        execution.getDecisionResult(),
        execution.getDecisionTier(),
        execution.getAiConfidence(),
        execution.getModelVersion(),
        execution.getPromptVersion(),
        execution.getTriageReason(),
        finalStatus,
        claim.getApprovedAmount(),
        claim.getRejectType(),
        humanInvolved(claim),
        agreement.name(),
        agreement.basis());
  }

  /** 有任一审核人即视为人工介入过（空串/空白 = 从未分配）。 */
  private static boolean humanInvolved(ClaimCase claim) {
    return notBlank(claim.getFirstReviewerId()) || notBlank(claim.getSecondReviewerId());
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  /** 样本键：雪花 ID 的加盐哈希前缀，可去重、不可直接读出案件号。 */
  static String sampleKey(Long claimId) {
    return sha256Hex(SAMPLE_KEY_SALT + claimId).substring(0, 16);
  }

  /**
   * 样本集快照哈希：按样本键排序后拼接受控字段，保证同一批数据在任何机器上算出同一指纹。
   *
   * <p>注意它<b>不是</b>冻结集哈希：冻结集哈希覆盖人工采集的 payload，用于「样本被改动即拒绝评测」；
   * 这里覆盖的是「本次导出的投影」，用于核对两次导出是否同源、是否被中间环节篡改。
   */
  static String hashSamples(List<EvalSampleExport.Sample> samples) {
    if (samples == null || samples.isEmpty()) {
      return sha256Hex("");
    }
    StringBuilder canonical = new StringBuilder();
    samples.stream()
        .sorted(Comparator.comparing(EvalSampleExport.Sample::sampleKey))
        .forEach(
            sample ->
                canonical
                    .append(sample.sampleKey())
                    .append('|')
                    .append(nullToEmpty(sample.aiDecisionResult()))
                    .append('|')
                    .append(nullToEmpty(sample.aiDecisionTier()))
                    .append('|')
                    .append(
                        sample.aiConfidence() == null ? "" : sample.aiConfidence().toPlainString())
                    .append('|')
                    .append(nullToEmpty(sample.aiModelVersion()))
                    .append('|')
                    .append(nullToEmpty(sample.aiPromptVersion()))
                    .append('|')
                    .append(nullToEmpty(sample.triageReason()))
                    .append('|')
                    .append(nullToEmpty(sample.finalStatus()))
                    .append('|')
                    .append(
                        sample.approvedAmount() == null
                            ? ""
                            : sample.approvedAmount().toPlainString())
                    .append('|')
                    .append(nullToEmpty(sample.rejectType()))
                    .append('|')
                    .append(sample.humanInvolved())
                    .append('|')
                    .append(nullToEmpty(sample.agreement()))
                    .append('\n'));
    return sha256Hex(canonical.toString());
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

  private static String sha256Hex(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JDK 必备算法；走到这里说明运行时被裁剪，必须炸而不是静默返回空哈希。
      throw new IllegalStateException("运行时缺少 SHA-256", e);
    }
  }

  /** 一致性判定结果（带受控说明，直接进导出供审计追溯判定依据）。 */
  enum Agreement {
    AGREE("AI 建议与案件最终状态一致"),
    DISAGREE("AI 建议与案件最终状态相反"),
    UNKNOWN_NOT_CONVERGED("案件未收敛到终态，标签未定"),
    UNKNOWN_NO_BINARY_VERDICT("AI 未给出可比二元结论（转人工/补件），不计入准确率");

    private final String basis;

    Agreement(String basis) {
      this.basis = basis;
    }

    String basis() {
      return basis;
    }
  }

  /**
   * 一次导出的完整结果（清单 + 样本）。
   *
   * <p>{@code splitPolicy} 恒为 {@code eval-only}：这些样本已经「被 AI 判断过」，用于训练会让模型在自己的
   * 判断上继续学习，形成自我强化；要训练必须走独立采集且与冻结集互斥。
   */
  public record EvalSampleExport(
      Instant windowFrom,
      Instant windowTo,
      int requestedLimit,
      int returned,
      boolean truncated,
      String samplesHash,
      List<Sample> samples) {

    static EvalSampleExport empty(Instant from, Instant to, int limit) {
      return new EvalSampleExport(from, to, limit, 0, false, hashSamples(List.of()), List.of());
    }

    @JsonProperty("generatedAt")
    public String generatedAt() {
      return ISO_UTC.format(Instant.now());
    }

    /** 派生字段：Jackson 只序列化 record 组件与 getter 形式，故显式声明属性名。 */
    @JsonProperty("labelSource")
    public String labelSource() {
      return "FINAL_STATUS（弱标签：来自案件终态，非独立盲标金标准）";
    }

    /** 派生字段：显式声明，避免「文档说有 splitPolicy、返回体里没有」的静默缺失。 */
    @JsonProperty("splitPolicy")
    public String splitPolicy() {
      return "eval-only（禁止用于训练/调优）";
    }

    /** 单条样本：只含评测必需字段，不含报案号/被保人标识等可直接识别到人的信息。 */
    public record Sample(
        String sampleKey,
        String claimType,
        String aiDecisionResult,
        String aiDecisionTier,
        BigDecimal aiConfidence,
        String aiModelVersion,
        String aiPromptVersion,
        String triageReason,
        String finalStatus,
        BigDecimal approvedAmount,
        String rejectType,
        boolean humanInvolved,
        String agreement,
        String agreementBasis) {}
  }
}
