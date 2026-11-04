package com.bone.claim.domain.model.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 参与决策披露（金发〔2026〕8号 §21 生成内容显著标识 / §22 可解释性与人工最终决策 / PIPL 第 24 条透明度与拒绝权）。
 *
 * <p><b>补齐的是文档真源已承诺、代码零实现的义务</b>： {@code 02-requirements/产品需求文档.md} §业务规则要求「拒赔通知书与理赔须知须含 AI 参与决策告知
 * + 要求人工复核的权利」，{@code 08-go-live/01-生产准入合规包.md} 要求「显著标识 AI 结论」。 这两条此前只写在文档里，运行时没有任何结构化载体。
 *
 * <p><b>三层证据分离（与 P0-1 治理清单同一纪律）</b>：本披露只基于<b>已落库的运行期事实</b> （{@code claim_decision_execution} +
 * {@code claim_decision_trace} 的 LLM 节点输出）， 不读部署态能力声明、不读自动放款开关、不重新调用任何 AI 能力、不写库、不碰金额与资金状态。
 *
 * <p><b>为什么必须是三态</b>：把「LLM 节点缺失 / 输出解析失败 / {@code aiAvailable} 键缺失」压成 「AI
 * 未参与」，等于把「不知道」写成一个对客户有利的确定性结论——与 {@code AiAssessNodeHandler} 的「无法判定 ≠ 拒赔」同理，只是方向相反。 因此 {@link
 * Involvement#UNDETERMINED} 与 {@link Involvement#AI_UNAVAILABLE} 是独立受控码， 二者都<b>不等价于「无 AI
 * 风险」</b>，且不得被消费方翻译成「AI 未参与决策」。
 *
 * <p><b>为什么不用 {@code execution.modelVersion} 判参与</b>：该列是执行入口传入的版本快照 （{@code POST /decision/execute}
 * 的 {@code modelVersion} 可由调用方填写），不是「模型真的跑过」的证据。 用快照判参与会把一次纯规则降级标成 AI 辅助决定，也让填个值就能伪造参与留痕。参与事实只认 LLM
 * 节点在轨迹里自报的 {@code aiAvailable}。
 */
public final class AiDecisionDisclosure {

  /** 显著标识文案（§21）：措辞固定，禁止把 AI 表述为决定者。 */
  public static final String LABEL_ASSISTED = "本结论含人工智能辅助生成内容：AI 仅提供建议、置信度与解释材料，不构成最终理赔决定";

  /** 降级态标识：不得让「没接线」被读成「已排除风险」。 */
  public static final String LABEL_UNAVAILABLE = "本次决策的 AI 能力未可用，已降级为规则路径；不得据此认定 AI 未参与或风险已排除";

  /** 无法判定态标识：明确写「无法确认」，而不是给一个看似安心的否定结论。 */
  public static final String LABEL_UNDETERMINED = "无法确认本次决策是否有人工智能参与；不得据此认定 AI 未参与或风险已排除";

  /** §22 / 金发 8 号：高风险业务人工最终决策，AI 输出仅作辅助。 */
  public static final String HUMAN_FINAL_DECISION = "最终理赔决定由具备权限的人工核赔人员作出并复核；AI 输出不参与金额计算、不改变资金状态。";

  /** PIPL 第 24 条 + PRD §业务规则（48h 申诉、五要素告知）。受控码，前端字典翻译，禁自由文本外溢。 */
  public static final List<String> RIGHTS =
      List.of("REQUEST_HUMAN_REVIEW", "APPEAL_WITHIN_48H", "EXPLANATION_OF_DECISION");

  /** 留存策略：轨迹 append-only，代码不做自动清理；具体保留期由部署侧合规配置决定，不在此假造天数。 */
  public static final String RETENTION_NOTE =
      "决策执行与节点轨迹 append-only 留存，保留期不短于业务存续期；" + "本服务不做自动清理，清理策略由部署侧合规配置决定。";

  /** 口径声明：防止消费方把只读复现视图当成新判定或放款许可。 */
  public static final String SCOPE_NOTE =
      "本披露基于已落库的决策执行与轨迹事实生成，是只读复现视图；" + "不构成结论变更、不构成放款许可、不产生新的 AI 判定。";

  private AiDecisionDisclosure() {}

  /** AI 参与三态。 */
  public enum Involvement {
    AI_ASSISTED,
    AI_UNAVAILABLE,
    UNDETERMINED
  }

  /**
   * 从 LLM 节点轨迹输出抽取的运行期事实。
   *
   * @param present 是否找到 LLM 节点
   * @param aiAvailable 节点自报的可用性；{@code null} = 键缺失或类型不符，必须落 UNDETERMINED
   * @param parseFailure 输出缺失 / 非 JSON / 非对象
   * @param generatedFields 由 AI 生成且非空的输出字段名（显著标识的指向对象）
   */
  public record AiNodeFacts(
      boolean present,
      Boolean aiAvailable,
      boolean parseFailure,
      String nodeActionResult,
      String reportedModelVersion,
      String reportedPromptVersion,
      List<String> generatedFields,
      List<String> rejectReasons) {

    public AiNodeFacts {
      generatedFields = generatedFields == null ? List.of() : List.copyOf(generatedFields);
      rejectReasons = rejectReasons == null ? List.of() : List.copyOf(rejectReasons);
      nodeActionResult = nodeActionResult == null ? "" : nodeActionResult;
      reportedModelVersion = reportedModelVersion == null ? "" : reportedModelVersion;
      reportedPromptVersion = reportedPromptVersion == null ? "" : reportedPromptVersion;
    }

    /** 轨迹里没有 LLM 节点。 */
    public static AiNodeFacts absent() {
      return new AiNodeFacts(false, null, false, "", "", "", List.of(), List.of());
    }

    /** 找到了 LLM 节点，但输出不可解析（或缺失）。 */
    public static AiNodeFacts unparsable(String actionResult) {
      return new AiNodeFacts(true, null, true, actionResult, "", "", List.of(), List.of());
    }
  }

  /** 可复现证据：全部是受控码、版本串与标量，不回传轨迹 output 原文（禁 PII 外溢）。 */
  public record Evidence(
      String graphVersion,
      String ruleVersion,
      String declaredModelVersion,
      String declaredPromptVersion,
      String reportedModelVersion,
      String reportedPromptVersion,
      String decisionResult,
      String triageReason,
      String consumptionReason,
      String status,
      Instant finishedAt,
      int traceNodeCount,
      String llmNodeActionResult,
      List<String> generatedFields,
      List<String> rejectReasons,
      List<String> traceGaps) {

    public Evidence {
      generatedFields = generatedFields == null ? List.of() : List.copyOf(generatedFields);
      rejectReasons = rejectReasons == null ? List.of() : List.copyOf(rejectReasons);
      traceGaps = traceGaps == null ? List.of() : List.copyOf(traceGaps);
    }
  }

  /** 一次披露（不可变快照）。 */
  public record Disclosure(
      Long claimId,
      String traceId,
      Involvement involvement,
      String involvementReason,
      String label,
      String aiRole,
      List<String> rights,
      String humanFinalDecision,
      String retentionNote,
      Evidence evidence,
      String scopeNote) {

    public Disclosure {
      rights = rights == null ? List.of() : List.copyOf(rights);
    }
  }

  /** 无执行记录 / 显式 traceId 跨案时的 fail-closed 披露：绝不返回「AI 未参与」。 */
  public static Disclosure undetermined(Long claimId, String reason) {
    Evidence empty =
        new Evidence(
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            null,
            0,
            "",
            List.of(),
            List.of(),
            List.of(reason));
    return new Disclosure(
        claimId,
        "",
        Involvement.UNDETERMINED,
        reason,
        LABEL_UNDETERMINED,
        "UNKNOWN",
        RIGHTS,
        HUMAN_FINAL_DECISION,
        RETENTION_NOTE,
        empty,
        SCOPE_NOTE);
  }

  /** 由已落库事实聚合披露；纯函数，不产生 IO、不调用任何 AI 能力。 */
  public static Disclosure of(
      DecisionExecution execution, List<DecisionTraceNode> traces, AiNodeFacts facts) {
    List<DecisionTraceNode> safeTraces = traces == null ? List.of() : traces;
    AiNodeFacts f = facts == null ? AiNodeFacts.absent() : facts;

    Involvement involvement;
    String reason;
    if (!f.present()) {
      involvement = Involvement.UNDETERMINED;
      reason = "LLM_NODE_ABSENT";
    } else if (f.parseFailure()) {
      involvement = Involvement.UNDETERMINED;
      reason = "LLM_OUTPUT_UNPARSABLE";
    } else if (f.aiAvailable() == null) {
      involvement = Involvement.UNDETERMINED;
      reason = "AI_AVAILABLE_FLAG_MISSING";
    } else if (f.aiAvailable()) {
      involvement = Involvement.AI_ASSISTED;
      reason = "LLM_NODE_SUCCEEDED";
    } else {
      involvement = Involvement.AI_UNAVAILABLE;
      reason = "LLM_NODE_FALLBACK";
    }

    String label =
        switch (involvement) {
          case AI_ASSISTED -> LABEL_ASSISTED;
          case AI_UNAVAILABLE -> LABEL_UNAVAILABLE;
          case UNDETERMINED -> LABEL_UNDETERMINED;
        };
    String aiRole =
        switch (involvement) {
          case AI_ASSISTED -> "ASSISTIVE_ONLY";
          case AI_UNAVAILABLE -> "NOT_APPLICABLE";
          case UNDETERMINED -> "UNKNOWN";
        };

    // 证据完整性缺口：如实列出，不因缺一项就把整次参与判成 UNDETERMINED，也不假装可完整复算。
    List<String> gaps = new ArrayList<>();
    if (safeTraces.isEmpty()) {
      gaps.add("TRACE_EMPTY");
    }
    if (involvement == Involvement.AI_ASSISTED) {
      if (blank(f.reportedModelVersion())) {
        gaps.add("NODE_MODEL_VERSION_MISSING");
      }
      if (blank(f.reportedPromptVersion())) {
        gaps.add("NODE_PROMPT_VERSION_MISSING");
      }
      if (blank(execution.getModelVersion())) {
        gaps.add("EXECUTION_MODEL_SNAPSHOT_MISSING");
      }
    }

    Evidence evidence =
        new Evidence(
            nz(execution.getGraphVersion()),
            nz(execution.getRuleVersion()),
            nz(execution.getModelVersion()),
            nz(execution.getPromptVersion()),
            f.reportedModelVersion(),
            f.reportedPromptVersion(),
            nz(execution.getDecisionResult()),
            nz(execution.getTriageReason()),
            nz(execution.getConsumptionReason()),
            nz(execution.getStatus()),
            execution.getFinishedAt(),
            safeTraces.size(),
            f.nodeActionResult(),
            f.generatedFields(),
            f.rejectReasons(),
            List.copyOf(gaps));

    return new Disclosure(
        execution.getClaimId(),
        execution.getTraceId(),
        involvement,
        reason,
        label,
        aiRole,
        RIGHTS,
        HUMAN_FINAL_DECISION,
        RETENTION_NOTE,
        evidence,
        SCOPE_NOTE);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
