package com.bone.airag.domain.model;

/** 重排序候选：一个进入 rerank 的切片（引用锚点 ref + 待打分文本 + 入池时的先验分）。 */
public record RerankCandidate(String ref, String text, double priorScore) {}
