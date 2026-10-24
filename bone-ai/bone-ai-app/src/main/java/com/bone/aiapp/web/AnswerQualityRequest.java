package com.bone.aiapp.web;

import java.util.List;

/**
 * 回答质量评判请求（RAG faithfulness 语义级评测入口，详设 §2.7）。
 *
 * @param query 用户查询
 * @param contexts 检索上下文（按相关度排序；空 = 无据，判官 fail-closed 记 0）
 * @param answer 待评判答案
 */
public record AnswerQualityRequest(String query, List<String> contexts, String answer) {}
