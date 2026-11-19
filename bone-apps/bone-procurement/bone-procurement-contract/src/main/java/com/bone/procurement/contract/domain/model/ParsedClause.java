package com.bone.procurement.contract.domain.model;

/**
 * 合同解析后的条款切片（本上下文自有 DTO）。
 *
 * <p>字段对齐 bone-ai-document 的 DocumentClause，但故意不 import 其类型：跨上下文集成只能经 infrastructure 的防腐层转换，domain
 * 零跨上下文依赖（ArchUnit noCrossContextDomainDependency 守护）。
 *
 * @param clauseNo 条款号（未识别为 null）
 * @param text 条款正文
 * @param page 起始页码（1-based）
 * @param spanStart 全文起始 offset（0-based，跨页累计）
 * @param spanEnd 全文结束 offset（开区间）
 */
public record ParsedClause(String clauseNo, String text, int page, int spanStart, int spanEnd) {

  public static ParsedClause of(
      String clauseNo, String text, int page, int spanStart, int spanEnd) {
    return new ParsedClause(clauseNo, text, page, spanStart, spanEnd);
  }
}
