package com.bone.claim.application;

import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import com.bone.claim.domain.model.decision.DecisionNodeType;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTier;
import com.bone.claim.domain.model.decision.DecisionTriage;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 决策图执行器（自建轻量 DAG，§7.3）。
 *
 * <p>业界实践要点：
 *
 * <ul>
 *   <li><b>执行不在事务内</b>：节点可能含 LLM 调用（秒级），先在内存聚合轨迹，收敛后单次短事务落库， 避免长事务持锁（AGENTS §7 禁悲观锁）；
 *   <li><b>LLM 不可用 → 降级纯规则路径</b>（不是直接失败），执行状态记 FALLBACK；
 *   <li><b>确定性节点失败 → 中止并转人工</b>（fail-safe）；
 *   <li><b>结论由确定性分诊计算</b>，AI 只产建议与置信度（"AI 不碰钱"）。
 * </ul>
 */
@Service
public class DecisionGraphExecutor {

  private final DecisionExecutionApplicationService decisionExecutionApplicationService;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;
  private final Map<String, DecisionNodeHandler> handlers;

  public DecisionGraphExecutor(
      DecisionExecutionApplicationService decisionExecutionApplicationService,
      ClaimCaseApplicationService claimCaseApplicationService,
      TenantPort tenantPort,
      List<DecisionNodeHandler> handlers) {
    this.decisionExecutionApplicationService = decisionExecutionApplicationService;
    this.claimCaseApplicationService = claimCaseApplicationService;
    this.tenantPort = tenantPort;
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toMap(
                    DecisionNodeHandler::nodeName, h -> h, (a, b) -> a, LinkedHashMap::new));
  }

  /**
   * 诊断用访问器：返回已装配的决策节点处理器（键=节点名）。
   *
   * <p>供装配冒烟测试断言「Spring 真的把 N 个 handler 收进执行器」，以及运行时健康自检 （历史上曾因零实现导致 map 为空、决策图永远 {@code
   * MANUAL_REQUIRED} 的假绿）。
   */
  public Map<String, DecisionNodeHandler> handlers() {
    return handlers;
  }

  /** 版本快照（复算与回放的前提）。 */
  public record DecisionVersions(
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {}

  /** 执行结果。 */
  public record DecisionExecutionResult(
      String traceId,
      Long claimId,
      String decisionResult,
      String decisionTier,
      String status,
      int traceCount) {}

  /** 执行一次决策图（落库执行记录 + 全链轨迹）。 */
  public DecisionExecutionResult execute(long claimId, DecisionVersions versions) {
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    requireDecisionSegment(claim);

    DecisionExecution active = decisionExecutionApplicationService.findActive(claimId);
    if (active != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_DECISION_ACTIVE_CONFLICT, claimId);
    }

    DecisionGraph graph = DecisionGraph.canonical();
    DecisionExecution execution =
        DecisionExecution.start(
            DistributedIdGenerator.generateLongId(),
            tenantPort.currentTenantId(),
            UUID.randomUUID().toString(),
            claimId,
            versions.graphVersion(),
            versions.ruleVersion(),
            versions.modelVersion(),
            versions.promptVersion(),
            versions.catalogVersion());

    Map<String, Object> outputs = new HashMap<>();
    BigDecimal confidence = null;
    boolean fallback = false;
    boolean capabilityMissing = false;
    boolean evidenceMissing = false;
    int seq = 0;

    for (DecisionGraph.Node node : graph.nodes()) {
      seq++;
      DecisionNodeHandler handler = handlers.get(node.name());
      if (handler == null) {
        if (node.type() == DecisionNodeType.LLM) {
          // LLM 能力不可用 → 降级纯规则路径（非中止）
          fallback = true;
          execution.recordTrace(
              seq,
              node.type(),
              node.name(),
              "",
              null,
              DecisionNodeStatus.FALLBACK,
              "LLM_UNAVAILABLE",
              0);
          confidence = asDecimal(outputs.get("ruleConfidence"));
          continue;
        }
        // 确定性节点能力缺失：链路不完整 → fail-safe 转人工
        capabilityMissing = true;
        execution.recordTrace(
            seq, node.type(), node.name(), "", null, DecisionNodeStatus.SKIPPED, "NO_HANDLER", 0);
        continue;
      }
      long begin = System.nanoTime();
      try {
        DecisionNodeOutcome outcome =
            handler.execute(
                new DecisionNodeContext(claimId, execution.getTraceId(), node, outputs));
        int cost = (int) ((System.nanoTime() - begin) / 1_000_000);
        execution.recordTrace(
            seq,
            node.type(),
            node.name(),
            outcome.inputSummary(),
            toJson(outcome.output()),
            outcome.status(),
            outcome.actionResult(),
            cost);
        outputs.putAll(outcome.output());
        if (outcome.confidence() != null) {
          confidence = outcome.confidence();
        }
        if (node.type() == DecisionNodeType.LLM
            && outcome.status() == DecisionNodeStatus.FALLBACK) {
          fallback = true;
        }
        if (Boolean.FALSE.equals(outcome.output().get("hasEvidence"))) {
          // 证据未齐 → 短路：结论已定（补件），不再执行后续节点
          evidenceMissing = true;
          break;
        }
      } catch (RuntimeException ex) {
        int cost = (int) ((System.nanoTime() - begin) / 1_000_000);
        if (node.type() == DecisionNodeType.LLM) {
          // LLM 不可用 → 降级纯规则路径（置信度取规则路径结论）
          fallback = true;
          execution.recordTrace(
              seq,
              node.type(),
              node.name(),
              "",
              null,
              DecisionNodeStatus.FALLBACK,
              "LLM_UNAVAILABLE",
              cost);
          confidence = asDecimal(outputs.get("ruleConfidence"));
        } else {
          // 确定性节点失败 → 中止并转人工
          execution.recordTrace(
              seq,
              node.type(),
              node.name(),
              "",
              null,
              DecisionNodeStatus.FAILED,
              ex.getClass().getSimpleName(),
              cost);
          execution.fail();
          decisionExecutionApplicationService.persist(execution);
          return toResult(execution);
        }
      }
    }

    DecisionTriage.Outcome outcome =
        evidenceMissing
            ? new DecisionTriage.Outcome(
                DecisionResult.NEED_MORE_EVIDENCE, DecisionTier.MANUAL, "EVIDENCE_MISSING")
            : capabilityMissing
                ? new DecisionTriage.Outcome(
                    DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, "CAPABILITY_MISSING")
                : DecisionTriage.evaluate(
                    new DecisionTriage.Input(
                        asBoolean(outputs.get("hasEvidence")),
                        !Boolean.FALSE.equals(outputs.get("evidenceParsed")),
                        asBoolean(outputs.get("ruleConflict")),
                        asDecimal(outputs.get("amount")),
                        confidence,
                        asDecimal(outputs.get("ocrConfidence")),
                        asBoolean(outputs.get("riskHit")),
                        asBoolean(outputs.get("fraudGatePassed")),
                        asBoolean(outputs.get("rejectSuggested"))));
    execution.complete(outcome.result(), outcome.tier(), confidence, fallback);
    decisionExecutionApplicationService.persist(execution);
    return toResult(execution);
  }

  // ===================== 内部 =====================

  private void requireDecisionSegment(ClaimCase claim) {
    ClaimStatus status = claim.status();
    boolean allowed =
        status == ClaimStatus.PENDING_PRE_EXAM
            || status == ClaimStatus.AUTO_PRE_EXAMINING
            || status == ClaimStatus.PRE_EXAMINING;
    if (!allowed) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, claim.getId());
    }
  }

  private DecisionExecutionResult toResult(DecisionExecution execution) {
    return new DecisionExecutionResult(
        execution.getTraceId(),
        execution.getClaimId(),
        execution.getDecisionResult(),
        execution.getDecisionTier(),
        execution.getStatus(),
        execution.traces().size());
  }

  private static boolean asBoolean(Object v) {
    return Boolean.TRUE.equals(v);
  }

  private static BigDecimal asDecimal(Object v) {
    if (v instanceof BigDecimal d) {
      return d;
    }
    if (v instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    if (v instanceof String s && !s.isBlank()) {
      try {
        return new BigDecimal(s.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  /** 节点输出 → JSON 文本（trace 完整保留输出；无第三方依赖，确定性输出便于回放比对）。 */
  private static String toJson(Map<String, Object> output) {
    if (output == null || output.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> e : output.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"').append(escape(e.getKey())).append("\":");
      Object v = e.getValue();
      if (v == null) {
        sb.append("null");
      } else if (v instanceof Number || v instanceof Boolean) {
        sb.append(v);
      } else {
        sb.append('"').append(escape(String.valueOf(v))).append('"');
      }
    }
    return sb.append('}').toString();
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
