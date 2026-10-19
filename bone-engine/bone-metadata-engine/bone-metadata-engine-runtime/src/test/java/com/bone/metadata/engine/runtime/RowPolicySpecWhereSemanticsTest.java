package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * {@link RowPolicySpec#buildWhere} 的 AND/OR 语义保真测试。
 *
 * <p><b>为什么这组测试优先级最高</b>：行级 ACL 是权限判定，而历史实现把 {@code when} 拆成原子后 <b>一律用 OR 拼接</b>——声明 {@code a AND
 * b}（意图收窄）实际生成 {@code a OR b}（可见行更宽）， 是<b>权限放大</b>方向；更严重的是含括号时拍平会整段丢弃括号内非首个比较 （实测 {@code (a OR b)
 * AND c} 生成 {@code a OR a OR c}，{@code b} 凭空消失）。
 *
 * <p>本组用例用「真实 SQL 字符串 + H2 端到端可见性」双重锁定，每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>把递归渲染改回拍平 OR → {@code and_staysAnd} / {@code parentheses_preserved} / {@code nestedParens}
 *       红；
 *   <li>删掉括号保留 → {@code parentheses_preserved} 红；
 *   <li>让 bindParams 自行估算个数 → {@code placeholderCount_matchesBindParams} 红。
 * </ul>
 */
class RowPolicySpecWhereSemanticsTest {

  private static final Predicate<String> COLS =
      c -> c.equals("created_by") || c.equals("approver_id") || c.equals("dept_id");

  private static RowPolicySpec spec(String when) {
    return RowPolicySpec.parse(
        "{\"owner_field\":\"created_by\",\"rules\":[{\"name\":\"r\",\"effect\":\"allow\",\"when\":\""
            + when
            + "\"}]}",
        COLS);
  }

  private static String whereOf(String when) {
    return spec(when).buildWhere("t", true, "42");
  }

  @Test
  @DisplayName("AND 语义保真：声明 AND ⇒ 生成 AND（历史实现拍平成 OR ⇒ 权限放大）")
  void and_staysAnd() {
    assertEquals(
        "(`created_by` = :rowPolicy_0 OR (`created_by` = :rowPolicy_1 AND `approver_id` = :rowPolicy_2))",
        whereOf("created_by = :operator AND approver_id = :operator"));
  }

  @Test
  @DisplayName("OR 语义保真：声明 OR ⇒ 生成 OR（向后兼容，文档示例口径）")
  void or_staysOr() {
    assertEquals(
        "(`created_by` = :rowPolicy_0 OR (`created_by` = :rowPolicy_1 OR `approver_id` = :rowPolicy_2))",
        whereOf("created_by = :operator OR approver_id = :operator"));
  }

  @Test
  @DisplayName("括号分组保留：AND/OR 混排时括号不被丢弃（否则 AND 退化为 OR）")
  void parentheses_preserved() {
    // (a OR b) AND c  —— 括号丢失后整个表达式变纯 OR，可见行集显著变宽
    assertEquals(
        "(`created_by` = :rowPolicy_0"
            + " OR ((`created_by` = :rowPolicy_1 OR `approver_id` = :rowPolicy_2)"
            + " AND `dept_id` = :rowPolicy_3))",
        whereOf("(created_by = :operator OR approver_id = :operator) AND dept_id = :operator"));
  }

  @Test
  @DisplayName("括号内每个比较都保留：历史实现会整段丢弃括号内非首个比较（b 消失）")
  void nestedParens_allComparisonsKept() {
    String sql =
        whereOf("(created_by = :operator OR approver_id = :operator) AND dept_id = :operator");
    assertTrue(sql.contains("`created_by` = :rowPolicy_1"), "owner 列应出现在规则内");
    assertTrue(sql.contains("`approver_id` = :rowPolicy_2"), "括号内第二个比较不得丢失");
    assertTrue(sql.contains("`dept_id` = :rowPolicy_3"), "AND 右侧比较不得丢失");
  }

  @Test
  @DisplayName("纯括号包裹单原子不产生多余嵌套（可读性，且语义等价）")
  void redundantParens_collapsed() {
    assertEquals(
        "(`created_by` = :rowPolicy_0 OR (`created_by` = :rowPolicy_1 AND `approver_id` = :rowPolicy_2))",
        whereOf("(created_by = :operator) AND approver_id = :operator"));
  }

  @Test
  @DisplayName("无 owner 列时只渲染规则部分（tableHasOwnerColumn=false）")
  void withoutOwnerColumn_onlyRules() {
    assertEquals(
        "((`created_by` = :rowPolicy_0 AND `approver_id` = :rowPolicy_1))",
        spec("created_by = :operator AND approver_id = :operator").buildWhere("t", false, "42"));
  }

  @Test
  @DisplayName("多规则之间是 OR（任一规则命中即可见）")
  void multipleRules_joinedByOr() {
    RowPolicySpec spec =
        RowPolicySpec.parse(
            "{\"owner_field\":\"created_by\",\"rules\":["
                + "{\"name\":\"a\",\"effect\":\"allow\",\"when\":\"created_by = :operator\"},"
                + "{\"name\":\"b\",\"effect\":\"allow\",\"when\":\"approver_id = :operator\"}]}",
            COLS);
    assertEquals(
        "(`created_by` = :rowPolicy_0"
            + " OR `created_by` = :rowPolicy_1"
            + " OR `approver_id` = :rowPolicy_2)",
        spec.buildWhere("t", true, "42"));
  }

  /**
   * 占位符与参数必须严格对齐——两者若各算一遍就会错位（历史 bindParams 用 {@code splitAtoms().size()+1}
   * 估算，在括号嵌套下与实际占位符数不符）。少绑一个参数 ⇒ SQL 执行时报「无名占位符」； 多绑一个不报错但掩盖真实数量，故此处断言**恰好相等**。
   */
  @Test
  @DisplayName("占位符数 == bindParams 绑定数（结构对齐，杜绝错位）")
  void placeholderCount_matchesBindParams() {
    String[] cases = {
      "created_by = :operator OR approver_id = :operator",
      "created_by = :operator AND approver_id = :operator",
      "(created_by = :operator OR approver_id = :operator) AND dept_id = :operator",
      "((created_by = :operator OR approver_id = :operator) AND dept_id = :operator)",
    };
    for (String when : cases) {
      RowPolicySpec spec = spec(when);
      String sql = spec.buildWhere("t", true, "42");
      MapSqlParameterSource params = new MapSqlParameterSource();
      spec.bindParams(params, "42");

      Matcher m = Pattern.compile(":rowPolicy_(\\d+)").matcher(sql);
      int maxIdx = -1;
      int occurrences = 0;
      while (m.find()) {
        occurrences++;
        maxIdx = Math.max(maxIdx, Integer.parseInt(m.group(1)));
      }
      assertEquals(
          occurrences, params.getValues().size(), "占位符出现次数与绑定参数数须一致: when=" + when + " sql=" + sql);
      assertEquals(
          maxIdx + 1, params.getValues().size(), "占位符序号须从0 连续且无缺口: when=" + when + " sql=" + sql);
    }
  }

  @Test
  @DisplayName("规则被全部校验丢弃时（未知列）不生成非法 `()`（历史实现会产生 SQL 语法错误）")
  void allRulesDropped_noEmptyParens() {
    // when 引用未声明列 ⇒ parse 期整条规则被丢弃 ⇒ 只剩 owner 条件
    RowPolicySpec spec =
        RowPolicySpec.parse(
            "{\"owner_field\":\"created_by\",\"rules\":[{\"name\":\"r\",\"effect\":\"allow\","
                + "\"when\":\"secret_col = :operator\"}]}",
            COLS);
    String sql = spec.buildWhere("t", true, "42");
    assertFalse(spec.hasRules(), "未知列规则应被丢弃");
    assertEquals("(`created_by` = :rowPolicy_0)", sql);
  }

  @Test
  @DisplayName("无 owner 列且无有效规则 ⇒ 生成恒假条件 1=0（fail-closed，绝不 `()` 或恒真）")
  void noOwnerNoRules_failClosed() {
    RowPolicySpec spec =
        RowPolicySpec.parse(
            "{\"owner_field\":\"created_by\",\"rules\":[{\"name\":\"r\",\"effect\":\"allow\","
                + "\"when\":\"secret_col = :operator\"}]}",
            COLS);
    String sql = spec.buildWhere("t", false, "42");
    assertEquals("(1 = 0)", sql, "无任何可生效条件时必须恒假（否则等于放开全部行）");
  }
}
