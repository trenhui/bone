package com.bone.claim.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 评估网关端口（出站，对接 bone-ai C5 LLM 网关 + C6 受控 RAG）。
 *
 * <p>{@link #assess} 在网关不可用时抛 {@link AiGatewayUnavailableException}——决策执行器据此对 LLM 节点
 * 降级纯规则路径（FALLBACK），符合"AI 不可用→降级而非失败"纪律。
 */
public interface AiGatewayPort {

  /** AI 评估：建议拒赔 + 置信度（>=0.85 方可直出，<0.7 强制人工，见 {@code DecisionTriage}）。 */
  AiAssessment assess(ClaimContextPort.ClaimContextView ctx);

  /**
   * AI 评估结果。
   *
   * <p>{@code rejectReasons} 为结构化拒赔原因码（白名单枚举，非自由文本）—— 用于"不利决定须可解释"的合规要求 （保险/PIPL），经 {@code
   * RejectReasonCodes} 校验后落决策轨迹供审计与申诉。 自由文本原因视为不可信，绝不允许模型臆造。
   *
   * <p>{@code modelVersion} / {@code promptVersion} 由<b>适配器自报</b>（真实上游模型 + 提示词契约版本），不经调用方传入：
   * 模型版本是决策复算、算法备案与投诉举证的先决条件，若交给 HTTP 调用方填，缺省即静默留空，溯源形同虚设。
   */
  record AiAssessment(
      boolean available,
      boolean rejectSuggested,
      BigDecimal confidence,
      List<String> rejectReasons,
      String modelVersion,
      String promptVersion) {

    /** 归一化：空值不落 {@code null}，避免下游 NPE 与"null 字符串"入库。 */
    public AiAssessment {
      rejectReasons = rejectReasons == null ? List.of() : List.copyOf(rejectReasons);
      modelVersion = modelVersion == null ? "" : modelVersion;
      promptVersion = promptVersion == null ? "" : promptVersion;
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
  }
}
