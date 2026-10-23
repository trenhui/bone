package com.bone.aiapp.web;

/**
 * 受控检索请求（API 层 DTO，与领域 RagQuery 解耦）。
 *
 * <p>护栏可请求级覆盖配置默认值（详设 §2.6）：{@code minScore} 分数阈值、{@code maxScan} 容量护栏。
 * MMR 多样性权重为模块级旋钮（{@code bone.ai.rag.mmr-lambda}，同 candidatePool），不走请求级——
 * 质量参数按评测集标定后全局生效，避免每次请求拿猜测值试错。
 */
public record RagRetrieveRequest(
    String query, String docScope, Integer topK, Double minScore, Integer maxScan) {}
