package com.bone.claim.application;

import com.bone.claim.domain.model.decision.AiDecisionDisclosure;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.AiNodeFacts;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionNodeType;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 参与决策披露（只读复现视图，监管 §21 / §22 / PIPL 24）。
 *
 * <p><b>刻意不做的事</b>：不写库、不调用任何 AI 能力、不读部署态能力声明、不读自动放款开关、 不重新执行决策图、不改金额与资金状态、不把轨迹 output 原文回传（禁 PII
 * 外溢）。 它只回答「这一案这一次决策，AI 到底扮演了什么角色、依据什么、能否复现」。
 *
 * <p><b>为什么参与判定只认轨迹节点自报</b>：执行行的 {@code model_version} 是入口可填的版本快照， 不是「模型跑过」的证据；部署态 {@code
 * declaration()} 说的是「装没装」，不是「这一案跑没跑」。 两者都不能替代逐案运行期事实（与 P0-1 三层证据分离一致）。
 *
 * <p><b>解析失败一律 UNDETERMINED</b>：轨迹 JSON 损坏、键缺失、类型不符都不得降级成 「AI 未参与」，否则「查不清」会被写成对客户有利的确定性结论。
 */
@Service
@RequiredArgsConstructor
public class AiDecisionDisclosureService {

  /** 版本串上限：节点自报值属外部输入，截断后再进披露面，防自由文本外溢。 */
  private static final int MAX_TEXT = 128;

  /** 由 AI 生成、需要在显著标识里点名的输出字段（受控键，不取自由文本值）。 */
  private static final List<String> GENERATED_KEYS =
      List.of("aiFacts", "aiEvidenceRefs", "aiClauseRefs", "aiDiscrepancies", "rejectReasons");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DecisionExecutionApplicationService decisionExecutionApplicationService;

  /**
   * @param traceId 可选；显式指定历史执行以复现。为空取该案最近一次执行。
   */
  public AiDecisionDisclosure.Disclosure disclose(long claimId, String traceId) {
    DecisionExecution execution =
        traceId == null || traceId.isBlank()
            ? decisionExecutionApplicationService.findLatest(claimId)
            : decisionExecutionApplicationService.findByTraceId(traceId);

    if (execution == null) {
      return AiDecisionDisclosure.undetermined(claimId, "NO_EXECUTION_RECORD");
    }
    // 显式 traceId 是租户内唯一键，但它可能属于另一个案件：不校验就把他人决策路径与版本快照
    // 挂到本案件的告知书下，属越权泄漏。此处 fail-closed 且不回传该 trace 的任何证据。
    if (execution.getClaimId() == null || execution.getClaimId() != claimId) {
      return AiDecisionDisclosure.undetermined(claimId, "TRACE_CLAIM_MISMATCH");
    }

    List<DecisionTraceNode> traces =
        decisionExecutionApplicationService.replay(execution.getTraceId());
    return AiDecisionDisclosure.of(execution, traces, readAiNode(traces));
  }

  /** 取该次执行最后一个 LLM 节点的结构化事实；无节点/不可解析均返回可区分的 UNDETERMINED 事实。 */
  private static AiNodeFacts readAiNode(List<DecisionTraceNode> traces) {
    if (traces == null || traces.isEmpty()) {
      return AiNodeFacts.absent();
    }
    DecisionTraceNode llm =
        traces.stream()
            .filter(n -> DecisionNodeType.LLM.name().equals(n.getNodeType()))
            .max(
                Comparator.comparingInt(
                    n -> n.getNodeSeq() == null ? Integer.MIN_VALUE : n.getNodeSeq()))
            .orElse(null);
    if (llm == null) {
      return AiNodeFacts.absent();
    }
    String output = llm.getOutput();
    if (output == null || output.isBlank()) {
      return AiNodeFacts.unparsable(llm.getActionResult());
    }
    try {
      JsonNode root = MAPPER.readTree(output);
      if (root == null || !root.isObject()) {
        return AiNodeFacts.unparsable(llm.getActionResult());
      }
      // null = 键缺失或类型不符，绝不当 false（false 是「确认降级」，null 是「不知道」）
      Boolean available = null;
      JsonNode flag = root.get("aiAvailable");
      if (flag != null && flag.isBoolean()) {
        available = flag.booleanValue();
      }
      List<String> generated = new ArrayList<>();
      for (String key : GENERATED_KEYS) {
        JsonNode value = root.get(key);
        if (value != null && value.isArray() && !value.isEmpty()) {
          generated.add(key);
        }
      }
      List<String> rejectReasons = new ArrayList<>();
      JsonNode reasons = root.get("rejectReasons");
      if (reasons != null && reasons.isArray()) {
        for (JsonNode reason : reasons) {
          if (reason.isTextual()) {
            rejectReasons.add(clip(reason.asText()));
          }
        }
      }
      return new AiNodeFacts(
          true,
          available,
          false,
          llm.getActionResult(),
          text(root, "aiModel"),
          text(root, "aiPromptVersion"),
          List.copyOf(generated),
          List.copyOf(rejectReasons));
    } catch (JsonProcessingException ex) {
      return AiNodeFacts.unparsable(llm.getActionResult());
    }
  }

  private static String text(JsonNode root, String key) {
    JsonNode value = root.get(key);
    return value != null && value.isTextual() ? clip(value.asText()) : "";
  }

  private static String clip(String s) {
    return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
  }
}
