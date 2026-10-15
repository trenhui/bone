package com.bone.aidoc.domain.model;

import java.util.List;

/**
 * 文档解析 IR（统一领域模型，解析器解耦——总案 §4.4 / 详设 §2.5）。
 *
 * <p>第一批最小 IR：PDFBox 条款切片 + span 定位。{@code provider_meta} 放解析器私有信息 （如 Tika 元数据 / OCR
 * 引擎标识），不与统一字段混用。
 *
 * @param docId 文档标识（调用方生成；租户内唯一）
 * @param tenantId 租户
 * @param sourceRef 来源引用（文件 key / URL）
 * @param pageCount 页数
 * @param parseMode 解析模式（PDFBOX_TEXT / TIKA_TEXT / DOCLING / OCR…）
 * @param provider 解析器标识
 * @param providerMeta 解析器私有元信息
 * @param clauses 条款切片（含 span 定位）
 * @param partial 是否部分 IR（sidecar 超时降级时为 true，编排转 HITL）
 */
public record DocumentIR(
    String docId,
    String tenantId,
    String sourceRef,
    int pageCount,
    String parseMode,
    String provider,
    java.util.Map<String, Object> providerMeta,
    List<DocumentClause> clauses,
    boolean partial) {

  public static DocumentIR of(
      String docId,
      String tenantId,
      String sourceRef,
      int pageCount,
      String parseMode,
      String provider,
      List<DocumentClause> clauses) {
    return new DocumentIR(
        docId,
        tenantId,
        sourceRef,
        pageCount,
        parseMode,
        provider,
        java.util.Map.of(),
        clauses,
        false);
  }

  public DocumentIR withPartial() {
    return new DocumentIR(
        docId, tenantId, sourceRef, pageCount, parseMode, provider, providerMeta, clauses, true);
  }
}
