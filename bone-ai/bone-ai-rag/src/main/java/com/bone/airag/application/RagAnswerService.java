package com.bone.airag.application;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.AnswerGenerationException;
import com.bone.airag.domain.gateway.AnswerGenerationPort;
import com.bone.airag.domain.model.GroundedPrompt;
import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.service.GroundedPromptBuilder;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RAG 回答编排（R1 闭环：检索 → 接地提示词 → 生成 → 接地校验 → 拒答或返回）。
 *
 * <p><b>四个不可省的顺序语义</b>：
 *
 * <ol>
 *   <li><b>先检索、无据不调模型</b>：检索结果为空时直接拒答，<b>不发起 LLM 调用</b>——没有上下文的生成必然靠模型自身
 *       知识，正是「无出处不得生成」要禁止的情形；同时也省掉一次纯浪费的模型成本。
 *   <li><b>预算截断后只对「真进了提示词的那部分」校验</b>：{@link GroundedPrompt#included()} 而非全部检索结果。 否则模型引用 {@code
 *       [3]}、而 {@code [3]} 已被预算截掉时，守卫与提示词对不上账，校验结论失去意义。
 *   <li><b>生成后必过守卫</b>：{@link RagApplicationService#verifyGrounding} 校验引用完整性与逐句支撑（含数字一致性）， 不通过即拒答并把
 *       {@link GroundingVerdict} 一并返回（前端可展示原因、监控可聚合拒答率）。
 *   <li><b>拒答不重试、不降级为「自由发挥」</b>：拒答是合规结果；退化成「不带出处的回答」等于把红线作废。
 * </ol>
 *
 * <p><b>生成故障同样不冲出编排边界</b>（R1 接线后生效）：{@link AnswerGenerationPort} 的故障（网关治理拒绝——注入疑似 / 配额 / 红线 /
 * 内容安全，或 provider 超时）经 {@link AnswerGenerationException} 上抛，本类收敛为 {@link
 * RagAnswerOutcome#unavailable} 可控拒答—— 异常上抛会把「这次答不了」升级成「业务流程中断」；errorCode 保真透传，安全事件不伪装成服务故障。
 *
 * <p><b>职责边界</b>：本类只做编排（顺序、短路、痕迹聚合）。提示词规则在 {@link GroundedPromptBuilder}，判定规则在 {@code
 * GroundingGuard}，检索在 {@link RagApplicationService}——应用层不承载领域规则（ArchUnit 约束）。
 */
public class RagAnswerService {

  private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);

  /** 统一拒答文案（服务端兜底）：前端应优先按 {@code errorCode} 走 i18n，本文案只用于无前端场景。 */
  public static final String REFUSAL_ANSWER = "未能在授权文档中找到可支撑该结论的依据，已拒绝作答。";

  /**
   * 降级痕迹码（进入 {@code degradations}）：上下文因 token 预算被截断。
   *
   * <p>必须透出而不是静默截断：截断会让「为什么这个问题答得不全」变成无法归因的悬案——是没检索到，还是检索到了 但没进上下文？这条痕迹把两种情形区分开。
   */
  public static final String DEGRADE_CONTEXT_TRUNCATED = "CONTEXT_TRUNCATED";

  private final RagApplicationService ragService;
  private final AnswerGenerationPort answerPort;
  private final GroundedPromptBuilder promptBuilder;

  public RagAnswerService(
      RagApplicationService ragService,
      AnswerGenerationPort answerPort,
      GroundedPromptBuilder promptBuilder) {
    this.ragService = ragService;
    this.answerPort = answerPort;
    this.promptBuilder = promptBuilder;
  }

  /** 检索并回答一个问题（完整闭环）。 */
  public RagAnswerOutcome answer(RagQuery query) {
    RagApplicationService.RagRetrieveOutcome retrieved = ragService.retrieve(query);

    if (retrieved.results().isEmpty()) {
      // 无据不调模型：空答案 + 空上下文 ⇒ NO_CONTEXT 判定，并落下 GROUNDING_REJECTED 事件
      GroundingVerdict verdict = ragService.verifyGrounding("", List.of());
      return new RagAnswerOutcome(
          false,
          REFUSAL_ANSWER,
          AiRagErrorCodes.GROUNDING_FAILED,
          verdict,
          List.of(),
          retrieved.degradations(),
          0,
          false);
    }

    GroundedPrompt prompt = promptBuilder.build(query.query(), retrieved.results());
    String answer;
    try {
      answer = answerPort.generate(prompt.system(), prompt.user());
    } catch (AnswerGenerationException e) {
      // 治理拒绝（注入疑似/配额/红线/内容安全）或 provider 故障：errorCode 保真透传——
      // 把注入拒绝归一成「服务不可用」会让安全事件从监控里消失
      log.warn(
          "RAG 生成被治理链拒绝或 provider 故障，收敛为可控拒答: code={}, detail={}",
          e.getErrorCode(),
          e.getMessage());
      return RagAnswerOutcome.unavailable(e.getErrorCode(), e.getMessage());
    } catch (RuntimeException e) {
      // 端口实现的未知运行时故障（bug 等）：同样不冲出编排边界，errorCode 归一为「生成不可用」，
      // WARN 明细保留现场（拒绝以静默空答案混进业务结果）
      log.warn("RAG 生成端口未知故障，收敛为不可用: {}", e.toString());
      return RagAnswerOutcome.unavailable(
          AiRagErrorCodes.ANSWER_GENERATION_UNAVAILABLE, e.toString());
    }
    // 只对真正进入提示词的上下文校验：编号与 included 一一对应
    GroundingVerdict verdict = ragService.verifyGrounding(answer, prompt.included());
    List<String> degradations = merged(retrieved.degradations(), prompt.truncated());
    if (!verdict.grounded()) {
      return new RagAnswerOutcome(
          false,
          REFUSAL_ANSWER,
          AiRagErrorCodes.GROUNDING_FAILED,
          verdict,
          prompt.included(),
          degradations,
          prompt.estimatedTokens(),
          prompt.truncated());
    }
    return new RagAnswerOutcome(
        true,
        answer,
        null,
        verdict,
        prompt.included(),
        degradations,
        prompt.estimatedTokens(),
        prompt.truncated());
  }

  /** 合并检索降级与本地截断痕迹（保序去重）。 */
  private static List<String> merged(List<String> degradations, boolean truncated) {
    List<String> out = new ArrayList<>(degradations);
    if (truncated && !out.contains(DEGRADE_CONTEXT_TRUNCATED)) {
      out.add(DEGRADE_CONTEXT_TRUNCATED);
    }
    return List.copyOf(out);
  }
}
