package com.bone.airag.domain.model;

/** 重排序结果：候选引用 + 相关性分（rerank 后的最终排序依据）。 */
public record RerankScored(String ref, double score) {}
