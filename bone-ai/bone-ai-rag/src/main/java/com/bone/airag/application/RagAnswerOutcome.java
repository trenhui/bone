package com.bone.airag.application;

import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.service.GroundingGuard;
import java.util.List;

/**
 * RAG 回答结果（检索 → 生成 → 接地校验的完整产出）。
 *
 * <p><b>{@code answered=false} 是正常业务结果，不是异常</b>：它表示「检索不到可支撑内容」或「模型输出未过接地 守卫」，调用方应把它当作可控的合规拒答返回（附
 * {@code errorCode} 与 {@code verdict} 供前端展示原因与监控聚合）， <b>不得重试</b>——重试只会再生成一段同样无支撑的内容。
 *
 * @param answered 是否给出了可返回的答案（false = 拒答，{@code answer} 为统一拒答文案）
 * @param answer 模型答案（{@code answered=true} 时）；拒答时为固定文案（前端应按 {@code errorCode} 走 i18n，而非直接展示）
 * @param errorCode 拒答原因码（成功时为 null）：{@code AI_RAG_GROUNDING_FAILED}
 * @param verdict 接地判定明细（含 reason / 无支撑句 / 幻觉引用）
 * @param contexts 实际进入提示词的上下文（编号即下标 + 1），供前端展示出处
 * @param degradations 降级痕迹（检索降级 + {@code CONTEXT_TRUNCATED}）
 * @param estimatedTokens 上下文 token 估算（近似口径）
 * @param truncated 是否因上下文预算发生截断（true 时「答不全」可归因）
 */
public record RagAnswerOutcome(
    boolean answered,
    String answer,
    String errorCode,
    GroundingVerdict verdict,
    List<RagResult> contexts,
    List<String> degradations,
    int estimatedTokens,
    boolean truncated) {

  /**
   * 生成链路故障时的拒答结果（编排层「AI 故障不阻断业务」的落地形态）。
   *
   * <p>检索 / 提示词 / 守卫任一环节的上游故障（生成端口不可用、provider 超时、配额 / 注入治理拒绝等）都不应以异常
   * 冲出编排边界——异常上抛会把「这次答不了」升级成「业务流程中断」。本工厂把故障收敛为与检索拒答同构的可控结果： 前端按 {@code errorCode} 展示，监控按拒答聚合。
   *
   * <p>故障明细放在 {@code verdict.unsupportedSentences}（审计轨迹位）：接地判定本身未执行，故支撑比等字段为空值。
   *
   * @param errorCode 对外错误码（如 {@code AI_RAG_ANSWER_GENERATION_UNAVAILABLE} / {@code
   *     AI_RAG_GROUNDING_FAILED}）
   * @param failureDetail 故障明细（异常摘要，供审计与定位）
   */
  public static RagAnswerOutcome unavailable(String errorCode, String failureDetail) {
    GroundingVerdict verdict =
        new GroundingVerdict(
            false,
            GroundingGuard.REASON_GENERATION_UNAVAILABLE,
            0,
            0,
            0.0,
            List.of(),
            List.of(failureDetail == null ? "" : failureDetail));
    return new RagAnswerOutcome(
        false, RagAnswerService.REFUSAL_ANSWER, errorCode, verdict, List.of(), List.of(), 0, false);
  }
}
