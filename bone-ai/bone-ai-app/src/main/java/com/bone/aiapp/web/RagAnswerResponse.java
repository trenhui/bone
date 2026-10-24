package com.bone.aiapp.web;

import com.bone.airag.application.RagAnswerOutcome;
import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.List;

/**
 * RAG 回答响应（API 层 DTO）：检索 → 接地提示词 → 生成 → 守卫的完整产出。
 *
 * <p><b>{@code answered=false} 是正常业务结果，不是异常</b>：表示「检索不到可支撑内容」或「模型输出未过接地守卫」或 「生成被网关治理链拒绝」—— 调用方按
 * {@code errorCode} 走 i18n 展示，<b>不得重试</b>（重试只会再生成一段同样无支撑的内容）。{@code verdict} 含拒答原因明细（无支撑句 /
 * 幻觉引用），供前端展示与监控聚合拒答率。
 *
 * @param answered 是否给出了可返回的答案（false = 拒答，{@code answer} 为统一拒答文案）
 * @param answer 模型答案（{@code answered=true} 时）；拒答时为固定文案（前端应按 {@code errorCode} 走 i18n）
 * @param errorCode 拒答原因码（成功时为 null）：{@code AI_RAG_GROUNDING_FAILED} / {@code
 *     AI_RAG_ANSWER_GENERATION_UNAVAILABLE} / 网关治理码（如 {@code AI_GATEWAY_INJECTION_SUSPECTED}）
 * @param verdict 接地判定明细（含 reason / 无支撑句 / 幻觉引用；生成故障时为审计轨迹位）
 * @param contexts 实际进入提示词的上下文（编号即下标 + 1），供前端展示出处
 * @param degradations 降级痕迹（检索降级 + {@code CONTEXT_TRUNCATED}）
 * @param estimatedTokens 上下文 token 估算（近似口径）
 * @param truncated 是否因上下文预算发生截断（true 时「答不全」可归因）
 */
public record RagAnswerResponse(
    boolean answered,
    String answer,
    String errorCode,
    GroundingVerdict verdict,
    List<RagResult> contexts,
    List<String> degradations,
    int estimatedTokens,
    boolean truncated) {

  public static RagAnswerResponse of(RagAnswerOutcome outcome) {
    return new RagAnswerResponse(
        outcome.answered(),
        outcome.answer(),
        outcome.errorCode(),
        outcome.verdict(),
        outcome.contexts(),
        outcome.degradations(),
        outcome.estimatedTokens(),
        outcome.truncated());
  }
}
