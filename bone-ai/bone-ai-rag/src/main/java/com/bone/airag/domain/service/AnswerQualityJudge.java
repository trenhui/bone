package com.bone.airag.domain.service;

import java.util.List;

/**
 * RAG 回答质量裁判端口（faithfulness / answerRelevance / contextPrecision，判据见 {@link AnswerQuality}）。
 *
 * <p><b>两个实现、两种消费场景</b>（与 {@code SensitiveDataMasker} 同构的「端口 + 确定性默认 + 语义增强」分层）：
 *
 * <ul>
 *   <li>{@link HeuristicAnswerQualityJudge}——确定性、零外部依赖：CI 构建内门禁（{@code mvn clean verify} 强制），
 *       锁死「据作答」底线不被回归破坏；
 *   <li>LLM 判官（app 组合根提供，经网关 {@code JudgePort} 计量）——语义级裁判：离线校准与大规模回归， 捕捉确定性判官的结构性盲区（同义改写 / 跨句归纳 /
 *       数值推导）。
 * </ul>
 *
 * <p><b>为何不把 LLM 判官接入运行时回答链</b>：判分是评测期动作而非请求期动作——每次回答都判分会把评测成本 计入生产时延与账单。运行时质量信号由 {@code
 * GroundingGuard}（引用接地，零成本确定性校验）承担； 本端口服务于评测管线（golden set 门禁 / 影子评测 / 上线前达标举证）。
 */
public interface AnswerQualityJudge {

  /**
   * 评判一次「查询 → 检索上下文 → 答案」三元组。
   *
   * @param query 用户查询（不可为空）
   * @param contexts 检索上下文文本（按相关度排序；空列表 = 无据，faithfulness fail-closed 记 0）
   * @param answer 待评判答案（空答案 = 无产出，fail-closed 记 0）
   */
  AnswerQuality judge(String query, List<String> contexts, String answer);
}
