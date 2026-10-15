package com.bone.aidoc.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import com.bone.aidoc.domain.service.ClauseSplitter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ClauseSplitter：中文/阿拉伯/英文条款起始模式 + span 跨页定位。 */
class ClauseSplitterTest {

  @Test
  void splits_chinese_clauses_with_span() {
    ParsedDocument parsed =
        ParsedDocument.of(2, List.of("第一条 甲方：采购方\n甲方按约定付款。\n", "第二条 乙方：供应商\n乙方按期交付。\n"), "pdfbox");
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    assertThat(clauses).hasSize(2);
    assertThat(clauses.get(0).clauseNo()).contains("第一条");
    assertThat(clauses.get(0).page()).isEqualTo(1);
    assertThat(clauses.get(0).spanStart()).isEqualTo(0);
    assertThat(clauses.get(1).clauseNo()).contains("第二条");
    assertThat(clauses.get(1).page()).isEqualTo(2);
    assertThat(clauses.get(1).spanStart()).isGreaterThanOrEqualTo(clauses.get(0).spanEnd());
    assertThat(clauses.get(1).spanEnd())
        .isEqualTo(parsed.pageTexts().get(0).length() + parsed.pageTexts().get(1).length());
  }

  @Test
  void splits_numeric_clauses() {
    ParsedDocument parsed =
        ParsedDocument.of(1, List.of("1.1 交付期限\n30 日内交付。\n2.1 付款方式\n验收后 10 日付款。\n"), "pdfbox");
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    assertThat(clauses).hasSize(2);
    assertThat(clauses.get(0).clauseNo()).startsWith("1.1");
    assertThat(clauses.get(1).clauseNo()).startsWith("2.1");
  }

  @Test
  void splits_english_clauses() {
    ParsedDocument parsed =
        ParsedDocument.of(
            1,
            List.of(
                "Article 1: Scope\nThis agreement covers services.\nArticle 2: Payment\nNet 30 days.\n"),
            "pdfbox");
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    assertThat(clauses).hasSize(2);
    assertThat(clauses.get(0).clauseNo()).startsWith("Article 1");
    assertThat(clauses.get(1).clauseNo()).startsWith("Article 2");
  }

  @Test
  void falls_back_to_single_clause_when_no_marker() {
    ParsedDocument parsed = ParsedDocument.of(1, List.of("无条款标记的纯文本段落。\n"), "pdfbox");
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    assertThat(clauses).hasSize(1);
    assertThat(clauses.get(0).clauseNo()).isNull();
  }
}
