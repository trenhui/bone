package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import com.bone.claim.domain.model.decision.AiModelProvenance;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.claim.domain.model.decision.KnowledgeBasis;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 运行期健康度：回答「AI 到底在不在线」。
 *
 * <p><b>它补的是哪个洞</b>：{@link AiCapabilityReadinessService} 给的是<b>部署态</b>（适配器有没有真实实现、开关开没开），
 * 评测导出（{@code EvalSampleExportApplicationService}）给的是<b>评测态</b>（AI 建议与案件终态的一致率）。
 * 两者之间缺一个<b>运行期</b>事实：这段时间里 AI 节点实际跑了多少次、参与成功几次、降级几次、为什么降。没有这个数， 「已接真实 provider」的环境照样可能 100%
 * 降级，而任何报表上都看不出来。
 *
 * <p><b>为什么从轨迹取数而不是从执行表</b>：降级原因码 {@code aiUnavailableReason} 随节点输出落在 {@code
 * claim_decision_trace}，执行表只有 FALLBACK 这一个粗粒度状态。也就是说「哪类故障最多」这个问题的答案只存在于节点层。
 *
 * <p><b>只读，不参与任何金额或状态判定</b>：本服务不写库、不改开关、不触发降级/恢复动作。它把空洞如实标出来， 是否视为故障由运维判断——阈值与告警需先有稳定流量，属后续工作。
 *
 * <p><b>「桩跑过」不算参与</b>：{@code aiAvailable=true} 只证明适配器返回了结论。本地确定性桩同样返回 {@code true} 并自报 {@code
 * aiModel=local-stub}，若照单计入参与，一个根本没接模型的环境会显示「AI 100% 在线」—— 这正是本端点存在的意义所在，被绕过就毫无价值。故按 {@link
 * AiModelProvenance} 把「自报可用」再切成三份：能证明是 生产模型的（{@link
 * AiRuntimeHealth#aiParticipated()}）、只能证明是桩的、以及证明了可用却说不清出自哪个模型的。
 */
@Service
@RequiredArgsConstructor
public class AiRuntimeHealthApplicationService {

  /** 时间窗上界（天）：与评测导出同口径，防止运维端点退化成全库扫描器。 */
  public static final int MAX_WINDOW_DAYS = 31;

  /** 单次扫描节点上界（与 SQL 模板里的 {@code LIMIT 2000} 保持一致）。 */
  public static final int MAX_NODES = 2000;

  /** 降级原因桶：节点没带原因码（v1.6 之前的历史数据）。 */
  public static final String UNATTRIBUTED = "UNATTRIBUTED";

  /** 降级原因桶：带了原因码但不在受控枚举内（版本错配 / 异构写入）。 */
  public static final String UNRECOGNIZED = "UNRECOGNIZED";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DecisionExecutionRepository decisionExecutionRepository;

  /**
   * 聚合时间窗内的 AI 节点健康度。
   *
   * @throws com.bone.core.exception.BusinessException 时间窗缺失 / 倒置 / 超过 {@link #MAX_WINDOW_DAYS}
   *     天（400，不静默裁剪）
   */
  public AiRuntimeHealth health(Instant from, Instant to) {
    validateWindow(from, to);
    List<DecisionTraceNode> nodes =
        decisionExecutionRepository.findAiAssessNodesInWindow(DecisionGraph.AI_ASSESS, from, to);
    return aggregate(nodes, from, to);
  }

  /**
   * 纯函数聚合（供单测与变异验证）。
   *
   * <p><b>四个互斥且完备的桶</b>：生产模型参与 / 桩参与 / 来源不明 / 降级，外加「无法判定」。后两个「说不清」的桶 是刻意留的——老 schema 的输出里没有 {@code
   * aiAvailable} 键，把这种行当成「参与成功」会让参与率凭空变好，当成 「降级」又会捏造一次不存在的故障。
   */
  static AiRuntimeHealth aggregate(List<DecisionTraceNode> nodes, Instant from, Instant to) {
    int scanned = nodes.size();
    int participated = 0;
    int stubbed = 0;
    int modelUnproven = 0;
    int degraded = 0;
    int undetermined = 0;
    int unparsable = 0;
    Map<String, Integer> degradedByReason = new TreeMap<>();
    int capabilityMissing = 0;
    Map<String, Integer> capabilityByReason = new TreeMap<>();
    Map<String, Integer> knowledgeBasisByReason = new TreeMap<>();
    int knowledgeBasisUnattributed = 0;
    int unverifiableLocators = 0;
    int locatorUnattributed = 0;

    for (DecisionTraceNode node : nodes) {
      JsonNode output = parse(node.getOutput());
      if (output == null) {
        // 输出不可解析：既证明不了参与、也证明不了降级，只能记「无法判定」并单独计数暴露出去。
        unparsable++;
        undetermined++;
        continue;
      }
      JsonNode aiAvailable = output.get("aiAvailable");
      if (aiAvailable == null || !aiAvailable.isBoolean()) {
        undetermined++;
        continue;
      }
      if (aiAvailable.booleanValue()) {
        // 「返回了结论」≠「模型跑过」：本地桩也自报 available=true，只在模型标识可归因到生产模型时才计参与。
        switch (AiModelProvenance.of(text(output, "aiModel"))) {
          case PROVIDER -> participated++;
          case STUB -> stubbed++;
          case UNKNOWN -> modelUnproven++;
        }
        // 幻觉计量：模型给出但在我送进去的材料里找不到出处的定位条数（适配器已丢弃那些条目）。
        // 只统计「模型确实跑过」的节点——降级节点没有模型输出，把它的 0 计入分母会把编造率稀释。
        JsonNode locators = output.get("aiUnverifiableLocators");
        if (locators == null || !locators.isIntegralNumber()) {
          locatorUnattributed++;
        } else {
          unverifiableLocators += locators.intValue();
        }
      } else {
        degraded++;
        degradedByReason.merge(reasonBucket(text(output, "aiUnavailableReason")), 1, Integer::sum);
      }
      // 能力缺失是第二类缺失（知识/规则/视觉/图计算），与「模型本身调不通」是不同处置动作，
      // 故独立成桶；它既可能伴随降级（知识库未接且模型也不可达），也可能单独出现在成功节点上。
      if (output.path("capabilityMissing").asBoolean(false)) {
        capabilityMissing++;
        String capabilityReason = text(output, "capabilityReason");
        capabilityByReason.merge(
            capabilityReason == null ? UNATTRIBUTED : capabilityReason, 1, Integer::sum);
        // 基据是「知识层为什么判不了」的二级归因：只有 KNOWLEDGE_UNAVAILABLE 一个码时，
        // 运维无法区分「解析没出事实」与「目录没收录」——两者处置动作互斥（改解析 vs 补目录）。
        // 缺失（尚未落基据的历史行）单独计数：并入任何具名基据都会把「老数据没这个字段」读成一种真实成因。
        String basis = text(output, "knowledgeBasis");
        if (basis == null) {
          knowledgeBasisUnattributed++;
        } else {
          knowledgeBasisByReason.merge(KnowledgeBasis.of(basis).code(), 1, Integer::sum);
        }
      }
    }

    // 分母口径不变：所有已明确表态的节点（含桩与来源不明）。收紧的只有分子——证明不了是生产模型就不算参与，
    // 否则「桩环境」与「模型全挂」在报表上无法区分。
    int determined = participated + stubbed + modelUnproven + degraded;
    return new AiRuntimeHealth(
        from,
        to,
        scanned,
        scanned >= MAX_NODES,
        participated,
        stubbed,
        modelUnproven,
        degraded,
        undetermined,
        unparsable,
        rate(participated, determined),
        Map.copyOf(degradedByReason),
        // 归因覆盖率与参与率必须分开：跑了但说不清为什么降级，和根本没跑，是两种运维状态。
        degraded == 0
            ? null
            : rate(degraded - degradedByReason.getOrDefault(UNATTRIBUTED, 0), degraded),
        capabilityMissing,
        Map.copyOf(capabilityByReason),
        Map.copyOf(knowledgeBasisByReason),
        knowledgeBasisUnattributed,
        unverifiableLocators,
        locatorUnattributed);
  }

  /**
   * 比率：分母为 0 时返回 {@code null} 而不是 0。
   *
   * <p>{@code 0%} 与 {@code null} 是两件不同的事实——前者是「全都坏了」，后者是「这批评据不成立」。 报表把 null 静默变 0，会让「没有任何 AI
   * 调用」显示成「AI 全部降级」，或反之让空窗期显示成达标。
   */
  private static BigDecimal rate(int numerator, int denominator) {
    if (denominator == 0) {
      return null;
    }
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
  }

  /** 原因码归一：缺失 → {@link #UNATTRIBUTED}；不在受控枚举内 → {@link #UNRECOGNIZED}（不把自由文本带进审计面）。 */
  private static String reasonBucket(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNATTRIBUTED;
    }
    try {
      return AiDegradationReason.valueOf(raw).name();
    } catch (IllegalArgumentException ex) {
      return UNRECOGNIZED;
    }
  }

  private static JsonNode parse(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(output);
    } catch (Exception ex) {
      // 单条坏数据不该让整个健康度端点 500：计数进 unparsableOutputs，由调用方决定是否排查。
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
  }

  private static void validateWindow(Instant from, Instant to) {
    if (from == null || to == null) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID, "时间窗必填：from 与 to 均为 ISO-8601 时刻");
    }
    if (!from.isBefore(to)) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID, "时间窗非法：from 必须早于 to");
    }
    if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_WINDOW_DAYS)) > 0) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID,
          "时间窗过大：最多 " + MAX_WINDOW_DAYS + " 天（当前 " + Duration.between(from, to).toDays() + " 天）");
    }
  }

  /**
   * 运行期健康度快照。
   *
   * @param nodesScanned 扫描的 AI 节点数
   * @param truncated 结果打满 SQL 上界时为 {@code true}（此时数字是下界，不是全量）
   * @param aiParticipated {@code aiAvailable=true} <b>且</b>模型标识可归因到生产模型的节点数（唯一计入参与率的分子）
   * @param aiStubbed {@code aiAvailable=true} 但模型标识是确定性桩 / 网关替身的节点数——方法被调用过，没有模型参与
   * @param aiModelUnproven {@code aiAvailable=true} 但没留模型标识的节点数（老 schema / 异构写入）：证明不了出自哪个模型
   * @param aiDegraded {@code aiAvailable=false} 的节点数
   * @param aiUndetermined 输出缺 {@code aiAvailable} 键或 JSON 不可解析的节点数（既不计参与也不计降级）
   * @param unparsableOutputs {@code aiUndetermined} 中因 JSON 不可解析造成的那部分（诊断用子集）
   * @param participationRate {@code aiParticipated / (aiParticipated + aiStubbed + aiModelUnproven
   *     + aiDegraded)}，4 位小数；分母为 0 时 {@code null}。分母是「所有已明确表态的节点」，分子只取能证明是生产模型的 ——桩环境因此显示 0 而不是
   *     100%
   * @param degradedByReason 降级原因码 → 条数。{@link #UNATTRIBUTED} 是真空洞（节点没给码）； {@link #UNRECOGNIZED}
   *     不是空洞而是「码不认识」——它证明生产侧确实写了码，只是与本构建的受控枚举 版本错配，故仍计入 {@code
   *     attributionCoverage}。覆盖率量的是「有没有码」，不是「码认不认识」； 「不认识的那部分有多大」看本 map 里 {@link #UNRECOGNIZED}
   *     这一项的条数。
   * @param attributionCoverage 降级样本里带了非空原因码的占比；降级为 0 时 {@code null}
   * @param capabilityMissing 自报「能力缺失」的节点数（知识/规则/视觉/图计算），与模型降级分开计
   * @param capabilityByReason 能力缺失原因码 → 条数
   * @param knowledgeBasisByReason 知识层基据码 → 条数（{@link KnowledgeBasis}）：区分「解析没出事实」「只有票面事实」
   *     「目录未收录」「能力仍是桩」——四者处置动作互斥，合并成一个 {@code KNOWLEDGE_UNAVAILABLE} 等于不可归因
   * @param knowledgeBasisUnattributed 能力缺失但没落基据码的节点数（尚未落基据的历史行）。单列而不并入具名基据：并入会把
   *     「老数据没这个字段」读成一种真实成因，从而把运维引向错误的处置方向
   * @param aiUnverifiableLocators 模型给出、但在送进去的材料里找不到出处的定位条数（事实 + 证据引用）。它们已被适配器丢弃，
   *     故不会出现在轨迹的事实列表里——本字段是这条丢弃动作的唯一可观测出口。大于 0 通常意味着提示词与材料格式错配 （模型在编 {@code claimContext}
   *     这类定位）或模型在复述输入
   * @param aiLocatorUnattributed 模型参与但没落幻觉计量键的节点数（v1.9 之前的历史行）。单列而不当作 0： 把「老数据缺字段」算成「零编造」会让幻觉率虚低
   */
  public record AiRuntimeHealth(
      Instant windowFrom,
      Instant windowTo,
      int nodesScanned,
      boolean truncated,
      int aiParticipated,
      int aiStubbed,
      int aiModelUnproven,
      int aiDegraded,
      int aiUndetermined,
      int unparsableOutputs,
      BigDecimal participationRate,
      Map<String, Integer> degradedByReason,
      BigDecimal attributionCoverage,
      int capabilityMissing,
      Map<String, Integer> capabilityByReason,
      Map<String, Integer> knowledgeBasisByReason,
      int knowledgeBasisUnattributed,
      int aiUnverifiableLocators,
      int aiLocatorUnattributed) {

    /** 派生字段：显式声明，避免下游把参与率当准确率对外报。 */
    @JsonProperty("note")
    public String note() {
      return "运行期口径：AiAssess 节点自报 aiAvailable + aiModel。participationRate 的分子只含可归因到生产模型的参与"
          + "（本地桩 local-stub 与未报模型的节点分别计入 aiStubbed / aiModelUnproven，都不算参与），"
          + "分母为所有已明确表态的节点；分母为 0 时为 null，不是 0%。"
          + "attributionCoverage 是降级中带受控原因码的占比。两者都不是准确率——准确率见 /claims/eval/samples 的 agreementRate。"
          + "knowledgeBasisByReason 是能力缺失的二级归因（知识层为什么判不了），"
          + "knowledgeBasisUnattributed 是没落基据的老数据；两者皆空表示这段时间没有能力缺失。"
          + "aiUnverifiableLocators 是模型给出但材料里无出处的定位条数（已被丢弃，不在事实列表里），"
          + "aiLocatorUnattributed 是没落该计量的老数据；它们衡量「模型是否在有材料可依的情况下编造」。";
    }
  }
}
