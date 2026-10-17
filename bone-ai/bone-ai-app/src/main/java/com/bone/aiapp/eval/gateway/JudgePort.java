package com.bone.aiapp.eval.gateway;

/** LLM-as-Judge 出口端口（默认实现走网关 {@code LlmClient}，由网关统一计量成本/审计）。 */
public interface JudgePort {
  JudgeVerdict judge(JudgeInput input);
}
