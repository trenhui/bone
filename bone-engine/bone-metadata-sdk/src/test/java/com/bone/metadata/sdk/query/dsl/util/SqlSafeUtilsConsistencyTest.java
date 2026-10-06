package com.bone.metadata.sdk.query.dsl.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.sdk.support.util.SqlInjectionPreventer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 把「两份 SQL 防护实现的字段名规则不一致」锁成可执行事实（2026-10-05）。
 *
 * <p>设计诊断 P2-2 指出 {@code SqlSafeUtils} 与 {@code SqlInjectionPreventer} 是双份实现且正则不一致， 实测确认：
 *
 * <ul>
 *   <li>{@code SqlSafeUtils.FIELD_NAME_PATTERN} = {@code ^[a-zA-Z_][a-zA-Z0-9_]*$}（不含点号）
 *   <li>{@code SqlInjectionPreventer.SQL_FIELD_PATTERN} = {@code ^[a-zA-Z_][a-zA-Z0-9_.]*$}（含点号）
 * </ul>
 *
 * <p>因此带表前缀的 {@code a.b}：{@code SqlBuilder} / {@code DefaultFluentQuery} 走前者会<b>抛异常</b>， {@code
 * AggregationBuilder.groupBy} 走后者却<b>能通过</b>。同一平台对「合法字段名」给出两种答案。
 *
 * <p><b>本类不要求它们一致，只要求差异是已知且可见的</b>：统一任一方向都是对外 SDK 的行为变更， 需架构决策。若将来统一了，本类会红——那时请把断言改成「两者一致」并删除本段说明，
 * 而不是直接把测试删掉（否则差异又会变成无人知晓的暗坑）。
 */
class SqlSafeUtilsConsistencyTest {

  @Test
  @DisplayName("现状：带点号的字段名在两份实现里结论相反（SqlSafeUtils 拒绝 / Preventer 接受）")
  void dottedFieldNameIsJudgedDifferentlyByTheTwoImplementations() {
    assertFalse(
        SqlSafeUtils.isValidFieldName("a.b"), "SqlSafeUtils 的字段名规则不含点号——若这条变红，说明它已被放开，差异可能已被统一");
    assertDoesNotThrow(
        () -> SqlInjectionPreventer.sanitizeFieldName("a.b"),
        "SqlInjectionPreventer 的字段名规则含点号——若这条变红，说明它已被收紧");
  }

  @Test
  @DisplayName("共同底线：不含点号的合法字段名两边都接受（差异只体现在点号上，不是整体失效）")
  void plainFieldNameIsAcceptedByBoth() {
    assertTrue(SqlSafeUtils.isValidFieldName("user_name"));
    assertDoesNotThrow(() -> SqlInjectionPreventer.sanitizeFieldName("user_name"));
  }

  @Test
  @DisplayName("共同底线：真正危险的输入两边都拒绝（不一致不等于整体失守）")
  void dangerousInputIsRejectedByBoth() {
    String hostile = "id; DROP TABLE t";
    assertFalse(SqlSafeUtils.isValidFieldName(hostile));
    assertFalse(
        java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$").matcher(hostile).matches(),
        "含分号/空格的注入串不应匹配 Preventer 的字段名规则");
  }
}
