package com.bone.aiapp.web;

/** 受控检索请求（API 层 DTO，与领域 RagQuery 解耦）。 */
public record RagRetrieveRequest(String query, String docScope, Integer topK) {}
