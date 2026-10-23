package com.bone.airag.domain.model;

/**
 * RAG 检索结果（带出处锚点，供生成阶段引用定位原文）。
 *
 * @param chunkRef 切片 id
 * @param clauseRef 条款 id（引用强制锚点）
 * @param spanStart 条款内 span 起点
 * @param spanEnd 条款内 span 终点
 * @param score 最终排序分：接入 cross-encoder 时为 rerank 分；未接入 / 降级时为 RRF 融合分（量纲双轨，仅可同次结果内比较）
 * @param path 章节面包屑
 * @param text 切片原文
 */
public record RagResult(
    String chunkRef,
    String clauseRef,
    int spanStart,
    int spanEnd,
    double score,
    String path,
    String text) {}
