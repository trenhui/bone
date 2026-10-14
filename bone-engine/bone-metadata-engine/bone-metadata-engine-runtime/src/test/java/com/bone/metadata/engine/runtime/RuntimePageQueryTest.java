package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimePageQueryTest {

  @Test
  void parseSortAndFields() {
    RuntimePageQuery q = RuntimePageQuery.parse("order_no,amount", "-amount,id", null);
    assertEquals(2, q.selectFields().size());
    assertEquals("amount", q.sortSpecs().get(0).field());
    assertEquals(true, q.sortSpecs().get(0).descending());
  }

  @Test
  void parseFilterRejectsInvalidQ() {
    assertThrows(RuntimeRecordException.class, () -> RuntimePageQuery.parse(null, null, "bad"));
  }

  @Test
  void parseSingleFilter_backwardCompatible() {
    // 旧契约 field:value 零变更：无分隔符时仍是单条件，值可含任意字符（含逗号）
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "note:hello, world");
    assertEquals(1, q.filters().size());
    assertEquals("note", q.filters().get(0).field());
    assertEquals("hello, world", q.filters().get(0).value());
  }

  @Test
  void parseMultipleFilters_andSemantics() {
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "status:APPROVED;amount:99.99");
    assertEquals(2, q.filters().size());
    assertEquals("status", q.filters().get(0).field());
    assertEquals("APPROVED", q.filters().get(0).value());
    assertEquals("amount", q.filters().get(1).field());
    assertEquals("99.99", q.filters().get(1).value());
  }

  @Test
  void parseFilters_skipsEmptySegments() {
    // 空段（前端删条件行后的残留）跳过而非报错：删一行筛选不该让列表加载失败
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "a:1;;b:2;");
    assertEquals(2, q.filters().size());
  }

  @Test
  void parseMultipleFilters_rejectsMalformedSegment() {
    // 任一分段格式错即整体拒绝（400）：静默丢条件会让筛选结果比用户预期宽，是数据正确性问题
    assertThrows(
        RuntimeRecordException.class,
        () -> RuntimePageQuery.parse(null, null, "status:APPROVED;malformed"));
  }

  // ── P1-3 运算符扩展（field:op:value） ──────────────────────────────────

  @Test
  void parseOperatorFilter_comparisonOps() {
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "amount:gte:99.99;amount2:lt:50");
    assertEquals(2, q.filters().size());
    assertEquals(RuntimePageQuery.FilterOp.GTE, q.filters().get(0).op());
    assertEquals("99.99", q.filters().get(0).value());
    assertEquals(RuntimePageQuery.FilterOp.LT, q.filters().get(1).op());
    assertEquals("50", q.filters().get(1).value());
  }

  @Test
  void parseOperatorFilter_containsAndIn() {
    RuntimePageQuery q =
        RuntimePageQuery.parse(null, null, "title:contains:差旅;status:in:DRAFT,APPROVED");
    assertEquals(RuntimePageQuery.FilterOp.CONTAINS, q.filters().get(0).op());
    assertEquals("差旅", q.filters().get(0).value());
    assertEquals(RuntimePageQuery.FilterOp.IN, q.filters().get(1).op());
    // in 的逗号拆分属 SQL 渲染细节，解析层保持原串（含空格由渲染层 trim）
    assertEquals("DRAFT,APPROVED", q.filters().get(1).value());
  }

  @Test
  void parseOperatorFilter_explicitEq() {
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "status:eq:DRAFT");
    assertEquals(RuntimePageQuery.FilterOp.EQ, q.filters().get(0).op());
    assertEquals("DRAFT", q.filters().get(0).value());
  }

  @Test
  void colonInValue_fallsBackToEquality() {
    // 零歧义规则：第二段不是已知运算符 ⇒ 整段等值（存量语义）
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "url:http://x/y;note:a:b");
    assertEquals(RuntimePageQuery.FilterOp.EQ, q.filters().get(0).op());
    assertEquals("http://x/y", q.filters().get(0).value(), "URL 含冒号必须等值不误判");
    assertEquals("a:b", q.filters().get(1).value());
  }

  @Test
  void unknownOperatorToken_fallsBackToEquality() {
    RuntimePageQuery q = RuntimePageQuery.parse(null, null, "amount:xx:100");
    assertEquals(RuntimePageQuery.FilterOp.EQ, q.filters().get(0).op());
    assertEquals("xx:100", q.filters().get(0).value(), "未知运算符 token 按等值处理，不报错");
  }

  @Test
  void operatorWithEmptyValue_rejected() {
    assertThrows(
        RuntimeRecordException.class,
        () -> RuntimePageQuery.parse(null, null, "amount:gte:"),
        "运算符后缺值必须显式失败（静默放行会把 gte 变成恒真条件）");
  }

  @Test
  void mixedOperators_multipleConditions() {
    RuntimePageQuery q =
        RuntimePageQuery.parse(
            null, null, "status:in:DRAFT,SUBMITTED;amount:lt:500;title:contains:差旅;applicant:bob");
    assertEquals(4, q.filters().size());
    assertEquals(RuntimePageQuery.FilterOp.IN, q.filters().get(0).op());
    assertEquals(RuntimePageQuery.FilterOp.LT, q.filters().get(1).op());
    assertEquals(RuntimePageQuery.FilterOp.CONTAINS, q.filters().get(2).op());
    assertEquals(RuntimePageQuery.FilterOp.EQ, q.filters().get(3).op());
  }

  @Test
  void filterOp_fromToken_unknownReturnsNull() {
    // 回退等值的判据入口：未知 token 返 null 而非抛异常
    assertEquals(RuntimePageQuery.FilterOp.GTE, RuntimePageQuery.FilterOp.fromToken("gte"));
    assertNull(RuntimePageQuery.FilterOp.fromToken("http"));
    assertNull(RuntimePageQuery.FilterOp.fromToken(""));
  }
}
