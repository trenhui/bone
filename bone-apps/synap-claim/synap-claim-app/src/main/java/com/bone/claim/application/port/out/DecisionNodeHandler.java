package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 决策节点处理器（防腐层端口，§3.2.1）。
 *
 * <p>每个节点一种实现（bone-ai 侧适配：文档解析 / 规则引擎 / AI 网关 / 仲裁 / 理算）， 本域只定义契约，不直接 import 任何第三方实现。
 *
 * <p><b>节点输出契约</b>（执行器按下列键读取，缺失即视为"未知"→ fail-safe 转人工）： {@code hasEvidence} / {@code
 * evidenceParsed} / {@code ruleConflict} / {@code amount} / {@code ocrConfidence} / {@code riskHit}
 * / {@code fraudGatePassed} / {@code rejectSuggested}； 置信度由 {@link
 * DecisionNodeOutcome#confidence()} 单独给出。
 *
 * <p><b>能力三态（{@code capabilityMissing} + {@code capabilityReason}）</b>：节点无法判定（依赖能力未接入）时置 {@code
 * capabilityMissing=true} 并给受控原因码；执行器据此只会把"本会 AUTO_APPROVE"的结论降级为 {@code
 * MANUAL_REQUIRED}。<b>严禁</b>把"无法判定"写进 {@code rejectSuggested}——那会把"没接线"落成对客户的不利决定。
 */
public interface DecisionNodeHandler {

  /** 处理的节点名（与 {@link DecisionGraph} 节点名一致）。 */
  String nodeName();

  /** 执行节点；抛异常表示节点不可用（LLM 节点 → 降级纯规则路径；确定性节点 → 中止转人工）。 */
  DecisionNodeOutcome execute(DecisionNodeContext context);

  /** 节点执行上下文。 */
  record DecisionNodeContext(
      long claimId, String traceId, DecisionGraph.Node node, Map<String, Object> outputs) {}

  /**
   * 节点输出。
   *
   * @param status 节点状态
   * @param output 节点输出键值（见端口注释的输出契约）
   * @param confidence 置信度（AI 节点为 AI 置信度；规则节点为规则路径置信度）
   * @param inputSummary 输入摘要（≤200 字，禁落原始 PII）
   * @param actionResult 节点结论（受控短码）
   */
  record DecisionNodeOutcome(
      DecisionNodeStatus status,
      Map<String, Object> output,
      BigDecimal confidence,
      String inputSummary,
      String actionResult) {

    public static DecisionNodeOutcome success(
        Map<String, Object> output,
        BigDecimal confidence,
        String inputSummary,
        String actionResult) {
      return new DecisionNodeOutcome(
          DecisionNodeStatus.SUCCEEDED,
          output == null ? Map.of() : output,
          confidence,
          inputSummary,
          actionResult);
    }

    public static DecisionNodeOutcome failure(String actionResult) {
      return new DecisionNodeOutcome(DecisionNodeStatus.FAILED, Map.of(), null, "", actionResult);
    }
  }
}
