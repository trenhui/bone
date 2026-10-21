package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * {@link RuntimeQuerySupport} 过滤子句生成测试（P1-3 运算符扩展）。
 *
 * <p>每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>删掉 LIKE 的 escapeLike 包装 → 通配符转义用例红（用户搜 {@code 50%} 静默扩大结果集）；
 *   <li>删掉 IN 的索引参数名（复用同一个 key）→ IN 用例红（同名 addValue 覆盖，只剩最后一个值）；
 *   <li>删掉列白名单校验 → 未建模字段用例红（注入面）；
 *   <li>把 appendComparison 的 sqlOp 写死 {@code =} → 比较运算符用例红。
 * </ul>
 */
class RuntimeQuerySupportTest {

  private static final PublishedRuntimeEntity ENTITY =
      new PublishedRuntimeEntity(
          "expense_reimbursement",
          "t_expense_reimbursement",
          "id",
          0L,
          List.of(
              new RuntimeFieldColumn("id", "LONG", true, true, true),
              new RuntimeFieldColumn("amount", "DECIMAL", true, false, false),
              new RuntimeFieldColumn("title", "STRING", false, false, false),
              new RuntimeFieldColumn("status", "STRING", false, false, false)),
          "status",
          null,
          null);

  private String clauseOf(String q) {
    RuntimePageQuery query = RuntimePageQuery.parse(null, null, q);
    MapSqlParameterSource params = new MapSqlParameterSource();
    return RuntimeQuerySupport.buildFilterClause(ENTITY, query, params);
  }

  @Test
  void eq_isBackwardCompatible() {
    assertEquals(" AND `amount` = :qFilterValue0", clauseOf("amount:99.99"));
  }

  @Test
  void comparisonOps_renderCorrectSqlOperator() {
    assertEquals(" AND `amount` > :qFilterValue0", clauseOf("amount:gt:1"));
    assertEquals(" AND `amount` >= :qFilterValue0", clauseOf("amount:gte:1"));
    assertEquals(" AND `amount` < :qFilterValue0", clauseOf("amount:lt:1"));
    assertEquals(" AND `amount` <= :qFilterValue0", clauseOf("amount:lte:1"));
    assertEquals(" AND `status` <> :qFilterValue0", clauseOf("status:ne:DRAFT"));
  }

  @Test
  void contains_rendersLike_withWrappedValue() {
    RuntimePageQuery query = RuntimePageQuery.parse(null, null, "title:contains:差旅");
    MapSqlParameterSource params = new MapSqlParameterSource();
    String clause = RuntimeQuerySupport.buildFilterClause(ENTITY, query, params);
    assertEquals(" AND `title` LIKE :qFilterValue0", clause);
    assertEquals("%差旅%", params.getValue("qFilterValue0"));
  }

  @Test
  void contains_escapesLikeWildcards() {
    // 核心负向：不转义时用户搜 50% 会匹配「含 50 的任意串」（静默扩大结果集）
    RuntimePageQuery query = RuntimePageQuery.parse(null, null, "title:contains:50%_x\\y");
    MapSqlParameterSource params = new MapSqlParameterSource();
    RuntimeQuerySupport.buildFilterClause(ENTITY, query, params);
    assertEquals("%50\\%\\_x\\\\y%", params.getValue("qFilterValue0"));
  }

  @Test
  void in_rendersPlaceholdersPerValue_trimmed() {
    RuntimePageQuery query = RuntimePageQuery.parse(null, null, "status:in:DRAFT, APPROVED ,PAID");
    MapSqlParameterSource params = new MapSqlParameterSource();
    String clause = RuntimeQuerySupport.buildFilterClause(ENTITY, query, params);
    assertEquals(" AND `status` IN (:qFilterValue0_0, :qFilterValue0_1, :qFilterValue0_2)", clause);
    assertEquals("DRAFT", params.getValue("qFilterValue0_0"));
    assertEquals("APPROVED", params.getValue("qFilterValue0_1"), "in 的元素必须 trim（用户习惯带空格）");
    assertEquals("PAID", params.getValue("qFilterValue0_2"));
  }

  @Test
  void multipleConditions_useIndexedKeys_noOverwrite() {
    // 同名 addValue 是覆盖语义：索引参数名是多条件正确性的前提
    RuntimePageQuery query =
        RuntimePageQuery.parse(null, null, "amount:gte:10;title:contains:x;status:in:A,B");
    MapSqlParameterSource params = new MapSqlParameterSource();
    RuntimeQuerySupport.buildFilterClause(ENTITY, query, params);
    assertEquals("10", params.getValue("qFilterValue0"));
    assertEquals("%x%", params.getValue("qFilterValue1"));
    assertEquals("A", params.getValue("qFilterValue2_0"));
    assertEquals("B", params.getValue("qFilterValue2_1"));
  }

  @Test
  void unmappedField_rejected() {
    // 列白名单：q 是注入面，字段必须已建模
    RuntimePageQuery query = RuntimePageQuery.parse(null, null, "hacker_col:eq:1");
    assertThrows(
        RuntimeRecordException.class,
        () -> RuntimeQuerySupport.buildFilterClause(ENTITY, query, new MapSqlParameterSource()));
  }

  @Test
  void illegalIdentifier_rejected() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    // parse 层先炸（a;drop 分两段，第一段 "a" 无冒号 → 格式错）
    assertThrows(
        RuntimeRecordException.class, () -> RuntimePageQuery.parse(null, null, "a;drop:eq:1"));
    // sanitize 层再兜底（直接构造 FilterSpec 绕过 parse 的场景）
    RuntimePageQuery direct =
        new RuntimePageQuery(
            null,
            null,
            List.of(new RuntimePageQuery.FilterSpec("bad col", RuntimePageQuery.FilterOp.EQ, "x")));
    assertThrows(
        RuntimeException.class,
        () -> RuntimeQuerySupport.buildFilterClause(ENTITY, direct, params));
  }

  @Test
  void emptyFilters_emptyClause() {
    assertEquals("", clauseOf(""));
    assertEquals(
        "",
        RuntimeQuerySupport.buildFilterClause(
            ENTITY, new RuntimePageQuery(null, null, List.of()), new MapSqlParameterSource()));
    assertEquals(
        "",
        RuntimeQuerySupport.buildFilterClause(
            ENTITY, RuntimePageQuery.EMPTY, new MapSqlParameterSource()));
  }

  @Test
  void escapeLike_backslashReplacedFirst() {
    // 顺序断言：反斜杠必须第一个替换，否则后两步生成的 \ 会被二次转义
    assertEquals("\\\\", RuntimeQuerySupport.escapeLike("\\"));
    assertEquals("\\%", RuntimeQuerySupport.escapeLike("%"));
    assertEquals("\\_", RuntimeQuerySupport.escapeLike("_"));
    assertEquals("a\\\\b\\%c", RuntimeQuerySupport.escapeLike("a\\b%c"));
    assertTrue(RuntimeQuerySupport.escapeLike("plain").equals("plain"));
  }
}
