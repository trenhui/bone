package com.bone.airag.domain.model;

/** RAG 检索请求。 */
public record RagQuery(String query, String docScope, int topK) {}
