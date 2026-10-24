package com.bone.airag.domain.service;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.AnswerGenerationPort;
import com.bone.core.exception.BizException;

/**
 * 答案生成端口的默认实现：<b>显式不可用</b>（fail-closed），而不是「静默返回空串」。
 *
 * <p>为什么默认要抛：未接生成服务时，若默认实现返回空串或占位文案，调用方会把它当成「模型答了但没内容」， 故障被吞进业务结果里；而抛 {@link
 * AiRagErrorCodes#ANSWER_GENERATION_UNAVAILABLE} 能让「忘了接生成」 在上线前就暴露。检索侧同理：无上下文时显式走「无依据」路径，不伪造答案。
 *
 * <p>接入方在组合根以 {@code @Bean AnswerGenerationPort} 覆盖（见端口注释的网关适配示例）。
 */
public class UnavailableAnswerGeneration implements AnswerGenerationPort {

  @Override
  public String generate(String systemPrompt, String userPrompt) {
    throw new BizException(
        503,
        "答案生成服务未接入（请以 @Bean AnswerGenerationPort 提供网关适配）",
        AiRagErrorCodes.ANSWER_GENERATION_UNAVAILABLE);
  }
}
