package com.bone.claim.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 评估网关端口（出站，对接 bone-ai C5 LLM 网关 + C6 受控 RAG）。
 *
 * <p>{@link #assess} 在网关不可用时抛 {@link AiGatewayUnavailableException}——决策执行器据此对 LLM 节点
 * 降级纯规则路径（FALLBACK），符合"AI 不可用→降级而非失败"纪律。
 *
 * <p><b>v1.2 AI 原生目标态</b>：AI 不再只产一个布尔建议，还需产出可解释的<b>结构化认知</b>——
 * 抽取事实、证据定位、条款引用、差异提示。这些字段只进入决策轨迹供核赔员、审计与后续评测样本消费， <b>不参与金额计算、不参与拒赔结论、不改变分诊</b>（P1「AI 不碰钱」不变）。
 * 模型缺失这些字段时按空列表 fail-soft 处理，绝不因"认知字段没给全"把一次可用评估判成不可用。
 */
public interface AiGatewayPort {

  /** AI 评估：建议拒赔 + 置信度（>=0.85 方可直出，<0.7 强制人工，见 {@code DecisionTriage}）。 */
  AiAssessment assess(ClaimContextPort.ClaimContextView ctx);

  /** AI 从材料抽取的单条结构化事实（受控码 + 值 + 证据定位；值仅用于展示与留痕，不参与判定）。 */
  record Fact(String code, String value, String evidenceRef) {}

  /**
   * AI 评估结果。
   *
   * <p>{@code rejectReasons} 为结构化拒赔原因码（白名单枚举，非自由文本）—— 用于"不利决定须可解释"的合规要求 （保险/PIPL），经 {@code
   * RejectReasonCodes} 校验后落决策轨迹供审计与申诉。 自由文本原因视为不可信，绝不允许模型臆造。
   *
   * <p>{@code modelVersion} / {@code promptVersion} 由<b>适配器自报</b>（真实上游模型 + 提示词契约版本），不经调用方传入：
   * 模型版本是决策复算、算法备案与投诉举证的先决条件，若交给 HTTP 调用方填，缺省即静默留空，溯源形同虚设。
   *
   * <p>{@code facts} / {@code evidenceRefs} / {@code clauseRefs} / {@code discrepancies} 是 AI
   * 原生的认知层产物： 随节点输出进入 {@code claim_decision_trace}，既是核赔员的可解释材料，也是独立盲标与评测集的候选样本源。
   * 由适配器做白名单、长度与数量收敛，禁止模型自由文本外溢。
   */
  record AiAssessment(
      boolean available,
      boolean rejectSuggested,
      BigDecimal confidence,
      List<String> rejectReasons,
      String modelVersion,
      String promptVersion,
      List<Fact> facts,
      List<String> evidenceRefs,
      List<String> clauseRefs,
      List<String> discrepancies) {

    /** 归一化：空值不落 {@code null}，避免下游 NPE 与"null 字符串"入库。 */
    public AiAssessment {
      rejectReasons = rejectReasons == null ? List.of() : List.copyOf(rejectReasons);
      modelVersion = modelVersion == null ? "" : modelVersion;
      promptVersion = promptVersion == null ? "" : promptVersion;
      facts = facts == null ? List.of() : List.copyOf(facts);
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
      clauseRefs = clauseRefs == null ? List.of() : List.copyOf(clauseRefs);
      discrepancies = discrepancies == null ? List.of() : List.copyOf(discrepancies);
    }

    /** 向后兼容：v1.1 的 6 参构造（无结构化认知输出）。 */
    public AiAssessment(
        boolean available,
        boolean rejectSuggested,
        BigDecimal confidence,
        List<String> rejectReasons,
        String modelVersion,
        String promptVersion) {
      this(
          available,
          rejectSuggested,
          confidence,
          rejectReasons,
          modelVersion,
          promptVersion,
          List.of(),
          List.of(),
          List.of(),
          List.of());
    }

    /** 向后兼容：仅关心拒赔结论时复用 4 参构造（无版本溯源）。 */
    public AiAssessment(
        boolean available,
        boolean rejectSuggested,
        BigDecimal confidence,
        List<String> rejectReasons) {
      this(available, rejectSuggested, confidence, rejectReasons, "", "");
    }

    /** 向后兼容：无原因码时复用 3 参构造。 */
    public AiAssessment(boolean available, boolean rejectSuggested, BigDecimal confidence) {
      this(available, rejectSuggested, confidence, List.of(), "", "");
    }

    /** 是否携带真实模型溯源（空 = 适配器未自报，消费方不得假装"已可追溯"）。 */
    public boolean hasModelTrace() {
      return !modelVersion.isBlank();
    }

    /** 是否产出了可解释的认知材料（供工作台决定是否展示 AI 事实卡片）。 */
    public boolean hasCognitiveOutput() {
      return !facts.isEmpty()
          || !evidenceRefs.isEmpty()
          || !clauseRefs.isEmpty()
          || !discrepancies.isEmpty();
    }
  }
}
