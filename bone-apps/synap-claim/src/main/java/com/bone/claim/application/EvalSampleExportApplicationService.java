package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        from,
        to,
        limit,
        samples.size(),
        truncated,
        hashSamples(samples),
        summarize(samples),
        samples);
  }

  /**
   * 按判定态聚合，并给出「可计算一致率的样本数」。
   *
   * <p><b>{@code evaluableCount == 0} 时必须让调用方看见</b>：那是「没有可比结论」，不是「一致率 0%」。前者说明这批数据
   * 根本不支持任何准确率结论（例如当期 AI 全部转人工），后者说明模型全错——两者的处置动作完全相反。若不出聚合字段， 调用方要么自己数（各写一套、口径漂移），要么把空结果当「达标」。
   *
   * <p>{@code agreementRate} 在无可比结论时返回 {@code null} 而不是 {@code 0}：0 会被下游当作有效测量值写进看板与回归基线， {@code
   * null} 会迫使下游显式判空（NaN / 除零在多数报表里也是「静默变 0」）。
   */
  static AgreementSummary summarize(List<EvalSampleExport.Sample> samples) {
    if (samples == null || samples.isEmpty()) {
      return AgreementSummary.empty();
    }
    int agree = 0;
    int disagree = 0;
    int noBinary = 0;
    int notConverged = 0;
    int aiNotInvolved = 0;
    int aiUndetermined = 0;
    int aiParticipated = 0;
    for (EvalSampleExport.Sample sample : samples) {
      // valueOf 遇未知取值直接抛（快速失败）；下面的求和校验兜住「新增判定态却忘了归类」。
      switch (Agreement.valueOf(sample.agreement())) {
        case AGREE -> agree++;
        case DISAGREE -> disagree++;
        case UNKNOWN_NO_BINARY_VERDICT -> noBinary++;
        case UNKNOWN_NOT_CONVERGED -> notConverged++;
        case UNKNOWN_AI_NOT_INVOLVED -> aiNotInvolved++;
        case UNKNOWN_AI_PARTICIPATION_UNDETERMINED -> aiUndetermined++;
      }
      if (AiParticipation.PARTICIPATED.name().equals(sample.aiParticipation())) {
        aiParticipated++;
      }
    }
    int counted = agree + disagree + noBinary + notConverged + aiNotInvolved + aiUndetermined;
    if (counted != samples.size()) {
      // 新增判定态而这里没跟上时，六态之和会小于样本数。静默漏统会让一致率虚高、总量看着正常，
      // 所以宁可让整次导出失败，也不输出一个自己都对不上账的聚合。
      throw new IllegalStateException(
          "判定态聚合与样本数不一致：" + counted + " != " + samples.size() + "，存在未归类的判定态");
    }
    int evaluable = agree + disagree;
    BigDecimal rate =
        evaluable == 0
            ? null
            : BigDecimal.valueOf(agree)
                .divide(BigDecimal.valueOf(evaluable), 4, RoundingMode.HALF_UP);
    // AI 参与率：分析「AI 到底覆盖了多少案件」的唯一指标。为 0 时一致率无论多少都没有意义。
    BigDecimal coverage =
        BigDecimal.valueOf(aiParticipated)
            .divide(BigDecimal.valueOf(samples.size()), 4, RoundingMode.HALF_UP);
    return new AgreementSummary(
        agree,
        disagree,
        noBinary,
        notConverged,
        aiNotInvolved,
        aiUndetermined,
        evaluable,
        rate,
        aiParticipated,
        coverage);
  }

  /**
   * AI 参与度判定（纯函数）。
   *
   * <p><b>为什么不能只看执行状态</b>：{@code SUCCESS} 只说明整条图的确定性节点都跑通了，不说明 LLM 节点真的产出了结论； 而 {@code FALLBACK} 说明
   * LLM 节点降级了——此时的 {@code decision_result} 是<b>规则引擎</b>的结论。 把降级行的结论与案件终态比对，算出来的是规则引擎的一致率，却会被当成「AI
   * 准确率」报出去。
   *
   * <p><b>为什么还要看模型版本</b>：降级时执行器会把规则置信度回填进 {@code ai_confidence}（既有语义）， 因此「有置信度」不能证明 AI 跑过；模型版本由 LLM
   * 节点成功时自报并回填，缺失即视为无法证明参与。 判不清就落 {@link AiParticipation#UNDETERMINED}，不计入分母——方向必须是保守的。
   */
  static AiParticipation participation(String executionStatus, String modelVersion) {
    if ("FALLBACK".equals(executionStatus)) {
      return AiParticipation.DEGRADED;
    }
    if ("SUCCESS".equals(executionStatus) && notBlank(modelVersion)) {
      return AiParticipation.PARTICIPATED;
    }
    return AiParticipation.UNDETERMINED;
  }

  /**
   * AI 建议与最终结果的一致性判定（纯函数，供单测与变异验证）。
   *
   * <p>参与度闸门在判定态之前：AI 没参与时，连「AI 建议是什么」都不成立，不能进任何一致/不一致的分支。
   */
  static Agreement judge(
      String aiDecisionResult, String finalStatus, AiParticipation participation) {
    if (participation == AiParticipation.DEGRADED) {
      return Agreement.UNKNOWN_AI_NOT_INVOLVED;
    }
    if (participation == AiParticipation.UNDETERMINED) {
      return Agreement.UNKNOWN_AI_PARTICIPATION_UNDETERMINED;
    }
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
    AiParticipation participation =
        participation(execution.getStatus(), execution.getModelVersion());
    Agreement agreement = judge(execution.getDecisionResult(), finalStatus, participation);
    return new EvalSampleExport.Sample(
        sampleKey(execution.getClaimId()),
        claim.getClaimType(),
        execution.getDecisionResult(),
        execution.getDecisionTier(),
        // 降级时 ai_confidence 存的是规则置信度（执行器语义），导出成「AI 置信度」等于给规则结论披 AI 外衣。
        participation == AiParticipation.PARTICIPATED ? execution.getAiConfidence() : null,
        execution.getModelVersion(),
        execution.getPromptVersion(),
        participation.name(),
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
                    // 参与度必须进哈希：同一批案件在「AI 参与」与「降级」两种状态下判据完全不同，
                    // 不进哈希会让两份语义不同的导出算出同一个指纹。
                    .append(nullToEmpty(sample.aiParticipation()))
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
    UNKNOWN_NO_BINARY_VERDICT("AI 未给出可比二元结论（转人工/补件），不计入准确率"),
    UNKNOWN_AI_NOT_INVOLVED("AI 未真实参与：本次执行为规则路径降级，其结论是规则引擎结论，不是 AI 建议，不计入 AI 一致率"),
    UNKNOWN_AI_PARTICIPATION_UNDETERMINED("无法证明 AI 真实参与（执行状态非降级但缺模型版本留痕），不计入 AI 一致率");

    private final String basis;

    Agreement(String basis) {
      this.basis = basis;
    }

    String basis() {
      return basis;
    }
  }

  /** AI 参与度：决定这条样本能不能进「AI 一致率」，与判定态（一致/不一致）是正交的两件事。 */
  enum AiParticipation {
    /** 执行未降级且有模型版本留痕：结论可归因于 AI。 */
    PARTICIPATED,
    /** 执行自报降级（FALLBACK）：结论来自规则路径，归因于 AI 就是造假。 */
    DEGRADED,
    /** 执行成功但拿不到模型版本：不能证明 AI 跑过，保守落此态。 */
    UNDETERMINED
  }

  /**
   * 一次导出的判定态分布与 AI 覆盖率。
   *
   * @param evaluableCount {@code agree + disagree}，即真正能进入一致率计算的样本数
   * @param agreementRate 弱标签一致率（{@code agree / evaluableCount}，4 位小数）；{@code evaluableCount == 0}
   *     时为 {@code null}
   * @param aiParticipatedCount AI 真实参与的样本数（判定态之外的正交维度）
   * @param aiCoverage {@code aiParticipatedCount / 返回样本数}；0 表示这批数据里 AI 一次都没参与
   */
  public record AgreementSummary(
      int agree,
      int disagree,
      int unknownNoBinaryVerdict,
      int unknownNotConverged,
      int unknownAiNotInvolved,
      int unknownAiParticipationUndetermined,
      int evaluableCount,
      BigDecimal agreementRate,
      int aiParticipatedCount,
      BigDecimal aiCoverage) {

    static AgreementSummary empty() {
      return new AgreementSummary(0, 0, 0, 0, 0, 0, 0, null, 0, null);
    }

    /** 派生字段：显式声明名称，避免下游把 {@code agreementRate} 直接当模型准确率对外报。 */
    @JsonProperty("rateMeaning")
    public String rateMeaning() {
      return "弱标签一致率（AI 建议 vs 案件终态）；evaluableCount=0 时 agreementRate 为 null，表示无可比结论，不是 0%";
    }

    /** 派生字段：先看覆盖率再看一致率——0 覆盖下的「一致率」只是规则引擎的自评。 */
    @JsonProperty("coverageMeaning")
    public String coverageMeaning() {
      return "AI 参与率 = aiParticipatedCount / 返回样本数；降级样本不计入，覆盖率为 0 时任何准确率结论都不成立";
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
      AgreementSummary agreementSummary,
      List<Sample> samples) {

    static EvalSampleExport empty(Instant from, Instant to, int limit) {
      return new EvalSampleExport(
          from,
          to,
          limit,
          0,
          false,
          hashSamples(List.of()),
          // 空集也走同一个聚合函数：null 语义只在一处实现，避免两处各自判空后逐渐走偏。
          summarize(List.of()),
          List.of());
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
        String aiParticipation,
        String triageReason,
        String finalStatus,
        BigDecimal approvedAmount,
        String rejectType,
        boolean humanInvolved,
        String agreement,
        String agreementBasis) {}
  }
}
