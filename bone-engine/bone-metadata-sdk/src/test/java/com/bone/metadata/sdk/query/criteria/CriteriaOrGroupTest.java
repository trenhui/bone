package com.bone.metadata.sdk.query.criteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * OR 条件组的 SQL 生成回归测试。
 *
 * <p>历史缺陷：{@code Criteria.or(Consumer)} 生成的括号片段被当作普通 EQ 条件二次拼装， 输出 {@code m.(a LIKE :x OR b LIKE
 * :y) = :null} 的非法 SQL，且被 BaseRepository 的字段名校验以 fieldName=null 拦截——能力从未真正可用。
 * 本组用例锁定修复后的契约：原生片段原样输出、参数完整拷贝。
 */
class CriteriaOrGroupTest {

  @Test
  void orGroupShouldRenderParenthesizedFragmentWithAllColumns() {
    Criteria<Object> criteria = Criteria.create();
    criteria.or(
        sub -> sub.like("recordCode", "%KW%").like("displayName", "%KW%").like("data", "%KW%"));

    assertEquals(1, criteria.getMainConditions().size(), "OR 组应收敛为一条原生片段条件");
    Condition fragment = criteria.getMainConditions().get(0);
    assertTrue(fragment.isNativeFragment(), "OR 组片段必须标记为 nativeFragment");

    String sql = fragment.toSql();
    assertTrue(sql.startsWith("(") && sql.endsWith(")"), "片段必须被括号包裹: " + sql);
    assertTrue(sql.contains(" OR "), "组内条件必须以 OR 连接: " + sql);
    assertEquals(3, sql.split("LIKE").length - 1, "三个 LIKE 子条件都应出现在片段里: " + sql);
    assertFalse(sql.contains(" = :null"), "不得再按 EQ 语义二次拼装: " + sql);
    assertEquals(3, criteria.getParameters().size(), "子条件参数必须完整拷贝到主参数表");
  }

  @Test
  void orGroupSupportsIsNullAndComparison() {
    Criteria<Object> criteria = Criteria.create();
    criteria.or(sub -> sub.isNull("effectiveFrom").lte("effectiveFrom", "2026-01-01"));

    String sql = criteria.getMainConditions().get(0).toSql();
    assertTrue(sql.contains("IS NULL"), "IS NULL 子条件应原样输出: " + sql);
    assertTrue(sql.contains("<="), "比较子条件应保留操作符: " + sql);
    assertEquals(1, criteria.getParameters().size(), "IS NULL 不占参数，仅比较条件占一个");
  }

  @Test
  void emptyOrGroupShouldBeIgnored() {
    Criteria<Object> criteria = Criteria.create();
    criteria.eq("status", "PUBLISHED");
    criteria.or(sub -> {});

    assertEquals(1, criteria.getMainConditions().size(), "空 OR 组不应产生任何条件");
  }
}
