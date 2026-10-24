package com.bone.airag.domain.service;

import java.util.List;

/**
 * RAG 回答质量判据（2026-10-24 企业复杂场景诊断缺口③「faithfulness 语义级评测」落地）。
 *
 * <p>对标 2026 业界 RAG 评测最佳实践（RAGAS / TruLens 指标族）中与「回答可信度」直接相关的三项：
 *
 * <ul>
 *   <li><b>faithfulness（忠实度）</b>：答案断言被检索上下文支撑的比例——「答案是否只在据作答」的核心指标， 对抗幻觉；未支撑断言明细见 {@link
 *       #unsupportedClaims()}；
 *   <li><b>answerRelevance（答案相关性）</b>：答案对查询问题的覆盖度——对抗「答非所问」；
 *   <li><b>contextPrecision（上下文精确度）</b>：检索结果中真正被答案使用的上下文占比——对抗「检索噪音」， 同时是检索侧质量（topK / rerank /
 *       min-score 调优）的回喂信号。
 * </ul>
 *
 * <p><b>fail-closed 约定</b>：无上下文（检索为空 / 被 ACL 拒绝）时 faithfulness 记 0——「无据」不得计为「忠」。 拒答话术（{@code
 * RagAnswerService.REFUSAL_ANSWER}）同理低分，与 {@code GroundingGuard} 的姿态一致。
 *
 * @param faithfulness 忠实度 [0,1]
 * @param answerRelevance 答案相关性 [0,1]
 * @param contextPrecision 上下文精确度 [0,1]
 * @param unsupportedClaims 未被上下文支撑的答案断言明细（可观测：低 faithfulness 时的归因输入，非空即可读）
 */
public record AnswerQuality(
    double faithfulness,
    double answerRelevance,
    double contextPrecision,
    List<String> unsupportedClaims) {

  public AnswerQuality {
    if (faithfulness < 0.0 || faithfulness > 1.0) {
      throw new IllegalArgumentException("faithfulness 须在 [0,1]: " + faithfulness);
    }
    if (answerRelevance < 0.0 || answerRelevance > 1.0) {
      throw new IllegalArgumentException("answerRelevance 须在 [0,1]: " + answerRelevance);
    }
    if (contextPrecision < 0.0 || contextPrecision > 1.0) {
      throw new IllegalArgumentException("contextPrecision 须在 [0,1]: " + contextPrecision);
    }
    if (unsupportedClaims == null) {
      throw new IllegalArgumentException("unsupportedClaims 不可为 null（可为空列表）");
    }
  }

  /** 空上下文 / 空答案的 fail-closed 判据（全 0，无未支撑断言明细）。 */
  public static AnswerQuality failClosed() {
    return new AnswerQuality(0.0, 0.0, 0.0, List.of());
  }

  /** 是否达到忠实度门禁（评测门禁与灰度放行判据用）。 */
  public boolean meetsFaithfulnessGate(double gate) {
    return faithfulness >= gate;
  }
}
