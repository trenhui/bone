package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
