package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条款切片：按中文合同常见条款起始模式切分并定位 span（服务“引用定位原文 100%”出口判据）。
 *
 * <p>起始模式（行首）：
 *
 * <ul>
 *   <li>中文编号：「第一条」「第二十一条」「第三条第一款」
 *   <li>阿拉伯编号：「1.」「1.1」「1.1.1」（含节号）
 *   <li>当事人条款：「甲方：」「乙方：」
 * </ul>
 *
 * 非起始行并入当前条款；span 为全文（跨页累计）0-based 区间。
 */
public final class ClauseSplitter {

  private static final Pattern CLAUSE_START =
      Pattern.compile(
          "(?m)^\\s*(第[一二三四五六七八九十百千万零〇]+[条款项目节](?:[一二三四五六七八九十百千万零〇]+[款]?)?\\s*[:：]?|"
              + "\\d{1,2}\\.(?:\\.?\\d{1,2}){0,2}\\s+[^\\n。]{2,40}[:：]?|"
              + "(?:Article|Clause|Section)\\s+\\d{1,3}[:.]|"
              + "[甲乙丙丁]方[^\\n]{0,8}[:：])");

  private ClauseSplitter() {}

  /**
   * 按页文本切片（span 跨页累计）。
   *
   * @return 条款列表；至少返回一个条款（全文兜底，避免空切片）
   */
  public static List<DocumentClause> split(ParsedDocument parsed) {
    List<DocumentClause> clauses = new ArrayList<>();
    StringBuilder full = new StringBuilder();
    for (int i = 0; i < parsed.pageTexts().size(); i++) {
      full.append(parsed.pageTexts().get(i));
    }
    String text = full.toString();

    Matcher matcher = CLAUSE_START.matcher(text);
    int lastStart = -1;
    int lastPage = 1;
    while (matcher.find()) {
      if (lastStart >= 0) {
        clauses.add(
            DocumentClause.of(
                clauseNoOf(text, lastStart),
                text.substring(lastStart, matcher.start()),
                lastPage,
                lastStart,
                matcher.start()));
      }
      lastStart = matcher.start();
      lastPage = pageOf(parsed, lastStart);
    }
    if (lastStart >= 0) {
      clauses.add(
          DocumentClause.of(
              clauseNoOf(text, lastStart),
              text.substring(lastStart),
              lastPage,
              lastStart,
              text.length()));
    }
    if (clauses.isEmpty()) {
      clauses.add(DocumentClause.of(null, text, 1, 0, text.length()));
    }
    return clauses;
  }

  private static String clauseNoOf(String text, int start) {
    int end = start;
    while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '。') {
      end++;
    }
    String head = text.substring(start, Math.min(end, start + 40)).trim();
    return head.length() > 24 ? head.substring(0, 24) : head;
  }

  private static int pageOf(ParsedDocument parsed, int offset) {
    int cursor = 0;
    for (int i = 0; i < parsed.pageTexts().size(); i++) {
      cursor += parsed.pageTexts().get(i).length();
      if (offset < cursor) {
        return i + 1;
      }
    }
    return parsed.pageCount();
  }
}
