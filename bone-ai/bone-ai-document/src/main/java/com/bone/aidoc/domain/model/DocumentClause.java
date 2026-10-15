package com.bone.aidoc.domain.model;

/**
 * 文档条款切片（含 span 定位，服务“引用定位原文”）。
 *
 * @param clauseNo 条款号（“第一条”/“1.1”/“甲方”等，未识别为 null）
 * @param text 条款正文
 * @param page 起始页码（1-based）
 * @param spanStart 全文起始 offset（0-based，跨页累计）
 * @param spanEnd 全文结束 offset（开区间）
 */
public record DocumentClause(String clauseNo, String text, int page, int spanStart, int spanEnd) {

  public static DocumentClause of(
      String clauseNo, String text, int page, int spanStart, int spanEnd) {
    return new DocumentClause(clauseNo, text, page, spanStart, spanEnd);
  }
}
