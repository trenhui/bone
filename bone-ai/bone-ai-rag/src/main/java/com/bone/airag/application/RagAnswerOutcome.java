package com.bone.airag.application;

import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.List;

/**
 * RAG 回答结果（检索 → 生成 → 接地校验的完整产出）。
 *
 * <p><b>{@code answered=false} 是正常业务结果，不是异常</b>：它表示「检索不到可支撑内容」或「模型输出未过接地
 * 守卫」，调用方应把它当作可控的合规拒答返回（附 {@code errorCode} 与 {@code verdict} 供前端展示原因与监控聚合），
 * <b>不得重试</b>——重试只会再生成一段同样无支撑的内容。
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
    boolean truncated) {}
