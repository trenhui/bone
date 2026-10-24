package com.bone.aiapp.web;

/**
 * RAG 回答请求（API 层 DTO，与领域 RagQuery 解耦）。
 *
 * <p>字段口径与 {@link RagRetrieveRequest} 一致（护栏可请求级覆盖配置默认值，详设 §2.6）：回答链先执行同一受控检索， 再进入「接地提示词 → 生成 →
 * 守卫」闭环（R1 接线后端点可用，见 RagController#answer）。
 */
public record RagAnswerRequest(
    String query, String docScope, Integer topK, Double minScore, Integer maxScan) {}
