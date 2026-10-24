package com.bone.airag.domain.model;

import java.util.List;

/**
 * 生成侧接地判定结果（把「无出处不得生成」从契约升级为可验证守卫）。
 *
 * <p>检索侧的引用强制是 fail-closed 的（无 {@code clauseRef}/span 的切片直接丢弃），但它只保证「喂给模型的上下文有出处」，
 * 不保证「模型说出的每句话都来自这些上下文」——LLM 仍可凭自身知识编造金额、期限、责任条款。本记录是生成后校验的输出，
 * {@code grounded=false} 时调用方必须拒答或降级为「仅返回原文片段」，不得把判定为无支撑的答案返回给用户。
 *
 * @param grounded 是否通过校验（false = 必须拒答/降级，fail-closed）
 * @param reason 判定原因码：{@code PASS} / {@code NO_CONTEXT} / {@code NO_CITATION} /
 *     {@code INVALID_CITATION} / {@code UNSUPPORTED_CLAIM}（见 {@code GroundingGuard} 常量）
 * @param sentenceCount 参与校验的句子数（空白/纯标点片段不计）
 * @param unsupportedCount 无上下文支撑的句子数
 * @param meanSupport 各句支撑比的均值（0~1，仅供观测/调阈值，不单独决定判定）
 * @param invalidCitations 越界或不存在的引用序号（如上下文只有 2 条却引用 {@code [9]}，即幻觉引用）
 * @param unsupportedSentences 被判无支撑的句子（已截断，供审计与回归定位）
 */
public record GroundingVerdict(
    boolean grounded,
    String reason,
    int sentenceCount,
    int unsupportedCount,
    double meanSupport,
    List<String> invalidCitations,
    List<String> unsupportedSentences) {}
