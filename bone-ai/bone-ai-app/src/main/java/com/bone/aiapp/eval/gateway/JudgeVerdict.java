package com.bone.aiapp.eval.gateway;

/** LLM-as-Judge 判定结果（评分 0..1 + 是否通过 + 理由）。 */
public record JudgeVerdict(double score, boolean passed, String rationale) {}
