package com.bone.claim.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 评估网关端口（出站，对接 bone-ai C5 LLM 网关 + C6 受控 RAG）。
 *
 * <p>{@link #assess} 在网关不可用时抛 {@link AiGatewayUnavailableException}——决策执行器据此对 LLM 节点
 * 降级纯规则路径（FALLBACK），符合"AI 不可用→降级而非失败"纪律。
 *
 * <p><b>v1.4 AI 原生契约</b>：AI 不再只产一个布尔建议，还需产出可解释的<b>结构化认知</b>——
 * 抽取事实、证据定位、条款引用、差异提示。这些字段进入决策轨迹供核赔员、审计与评测样本消费， 其中<b>白名单内的差异码</b>经 {@code AiDiscrepancyRouting}
 * 驱动非资金流程状态（自动通过 → 补件/复核）。 红线不变（P1「AI 不碰钱」）：不参与金额计算、不参与理算、不产拒赔结论、不改变分诊阈值与输入。 模型缺失这些字段时按空列表
 * fail-soft 处理，绝不因"认知字段没给全"把一次可用评估判成不可用。
 */
public interface AiGatewayPort {

  /** AI 评估：建议拒赔 + 置信度（>=0.85 方可直出，<0.7 强制人工，见 {@code DecisionTriage}）。 */
  AiAssessment assess(ClaimContextPort.ClaimContextView ctx);

  /** 部署态声明兜底：适配器未覆盖时一律 UNKNOWN（fail-closed），不得推断为 READY。 */
  default AiCapabilityDeclaration declaration() {
    return AiCapabilityDeclaration.unknown("C5_LLM");
  }

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
   *
   * <p><b>{@code unverifiableLocators} 是幻觉计量</b>：模型给出的事实 / 证据定位若在我送进去的材料里找不到对应定位，
   * 就不是"抽取"而是编造。适配器把它们丢弃并在此计数——丢弃是安全动作，计数是可观测动作；只丢不计数， 会让"模型什么都没抽到"与"模型抽了一堆但全不可核"在报表上长得一样。
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
      List<String> discrepancies,
      int unverifiableLocators) {

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

    /** 向后兼容：v1.2 的 10 参构造（尚未做定位可核校验时）。 */
    public AiAssessment(
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
      this(
          available,
          rejectSuggested,
          confidence,
          rejectReasons,
          modelVersion,
          promptVersion,
          facts,
          evidenceRefs,
          clauseRefs,
          discrepancies,
          0);
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

  /**
   * 自然语言问答查询（NL 入口，WS-B P1）。
   *
   * <p>与 {@link #assess} 的区别：assess 是「评估专用、结构化拒赔」；answer 是「开放问答」，用于理赔助手以自然语言回答政策 / 材料 / 流程问题。
   * 二者共用同一 C5 网关治理门面（脱敏 / 注入检测 / 成本红线），但 answer 走自由文本（非 JSON）通道。
   *
   * <p><b>诚实边界</b>：网关未配置真实 provider 时，实现须返回 {@link Completion#unavailable}， 不得编造答复（与 assess 的
   * fail-closed 同源）。
   */
  record AssistantQuery(String question, String claimId) {
    public AssistantQuery {
      question = question == null ? "" : question.trim();
      claimId = claimId == null ? "" : claimId.trim();
    }
  }

  /**
   * 自然语言问答答复（网关可降级，不抛异常）。
   *
   * <p>{@code available=false} 时 {@code text} 为空，消费方须如实展示「AI 未就绪」而非假装已回答。 {@code sources}
   * 为引用定位（须落在提供的知识片段内，防幻觉）。
   */
  record Completion(
      boolean available,
      String text,
      String modelVersion,
      List<String> sources,
      int unverifiableLocators) {
    public Completion {
      text = text == null ? "" : text;
      modelVersion = modelVersion == null ? "" : modelVersion;
      sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** 网关未配置 / 调用失败：不可用，消费方如实透传未就绪。 */
    public static Completion unavailable() {
      return new Completion(false, "", "", List.of(), 0);
    }

    /** 网关成功返回：携带正文与模型溯源，来源留痕（如需引用定位由上游附）。 */
    public static Completion available(String text, String modelVersion, List<String> sources) {
      return new Completion(true, text, modelVersion, sources, 0);
    }
  }

  /** 默认兜底：未覆盖声明的实现一律返回 unavailable（fail-closed），不得推断为可答。 */
  default Completion answer(AssistantQuery query) {
    return Completion.unavailable();
  }
}
