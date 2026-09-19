package com.bone.metadata.sdk.sql.tenant;

import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;

/**
 * 可信租户条件注入器（{@code @TenantScope(AUTO)/ALL/BYPASS}）。
 *
 * <p>作用在 {@code @Sql} 通道的最终 SQL 上——经 {@code MyBatisSqlProcessor} 处理后，{@code #{...}} 已转换为 {@code
 * :命名参数}。 与 ADR-0029 的 Criteria 通道注入保持同一不变量：<b>租户表查询必须被限定到单一租户，缺可信上下文即失败关闭</b>， 绝不退化为 {@code
 * tenant_id = NULL} 静默空结果或跨租户越权。
 *
 * <h3>注入策略</h3>
 *
 * <ul>
 *   <li>{@link TenantScopeMode#MANUAL} / {@link TenantScopeMode#ALL} / {@link
 *       TenantScopeMode#BYPASS}：不做任何注入（保持历史行为）。
 *   <li>{@link TenantScopeMode#AUTO}：{@code tenantId} 必须非 null（来自可信 {@code TenantContext}），否则抛
 *       {@link MissingTenantContextException}。
 *       <ul>
 *         <li>优先替换作者显式锚点 {@code /*bone:tenant*\/}（联表查询须自带别名，如 {@code "o.tenant_id"}）；
 *         <li>若无锚点，对"单表 + 可选 WHERE、无 JOIN/子查询"的简单查询做启发式注入；
 *         <li>复杂查询（JOIN 无锚点、子查询无法判定）一律抛异常，要求作者补锚点——<b>失败关闭，绝不静默漏注</b>。
 *       </ul>
 * </ul>
 */
public final class TenantSqlRewriter {

  /** 注入后承载租户值的命名参数名（加入 {@code ProcessedSql} 的参数集合）。 */
  public static final String TENANT_PARAM = "__boneTenantId__";

  /** 作者在 SQL 中放置的锚点：SDK 替换为 {@code <column> = :__boneTenantId__}。 */
  static final String MARKER = "/*bone:tenant*/";

  private static final String[] BOUNDARY_KEYWORDS = {
    " group by ",
    " order by ",
    " having ",
    " limit ",
    " offset ",
    " union ",
    " for update ",
    " fetch "
  };

  private TenantSqlRewriter() {}

  /**
   * 重写 SQL，注入租户条件。
   *
   * @param sql 经处理器后的最终 SQL（含 {@code :命名参数}）
   * @param mode 作用模式
   * @param column 租户列表达式（可带别名），如 {@code "o.tenant_id"} 或 {@code "tenant_id"}
   * @param tenantId 从可信 {@code TenantContext} 解析出的租户值（AUTO 模式下必须非 null）
   * @param softDelete 是否在租户条件后追加 {@code AND deleted = 0}
   * @return 注入后的 SQL（非 AUTO 模式原样返回）
   */
  public static String rewrite(
      String sql, TenantScopeMode mode, String column, Long tenantId, boolean softDelete) {
    if (mode == null
        || mode == TenantScopeMode.MANUAL
        || mode == TenantScopeMode.ALL
        || mode == TenantScopeMode.BYPASS) {
      return sql;
    }
    if (tenantId == null) {
      throw new MissingTenantContextException(column);
    }
    String clause = buildClause(column, softDelete);
    if (sql.contains(MARKER)) {
      return sql.replace(MARKER, clause);
    }
    // 无锚点：这些结构下无法唯一定位「单表租户列」，启发式注入只会命中其中一段 SELECT
    // （如 UNION 的第一臂），其余段完全不过滤 = fail-open 的部分注入，比不注入更危险（跨租户泄漏）。
    // 故一律要求作者补锚点——失败关闭，绝不静默漏注。
    String reason = unsupportedStructureReason(sql.toLowerCase());
    if (reason != null) {
      throw new IllegalStateException(
          "AUTO tenant injection cannot safely locate the tenant column for a "
              + reason
              + " query without the "
              + MARKER
              + " anchor (alias/structure ambiguity). Place the anchor where the tenant condition "
              + "belongs, e.g. \"... WHERE o.id = :orderId "
              + MARKER
              + " AND o.deleted = 0\".");
    }
    return injectSimple(sql, clause);
  }

  /**
   * 无锚点注入前必须拒绝的复杂结构（返回拒绝原因，简单单表查询返回 {@code null}）。
   *
   * <p>判定只针对<b>顶层</b>关键字（括号内的子查询不作为独立判定主体，但其派生表结构本身由 {@code " from ("} 拒绝）。
   */
  private static String unsupportedStructureReason(String lowerSql) {
    if (containsTopLevelKeyword(lowerSql, " join ")) {
      return "JOIN";
    }
    if (containsTopLevelKeyword(lowerSql, " union ")) {
      return "UNION";
    }
    if (containsTopLevelKeyword(lowerSql, " intersect ")) {
      return "INTERSECT";
    }
    if (containsTopLevelKeyword(lowerSql, " except ")) {
      return "EXCEPT";
    }
    if (containsTopLevelKeyword(lowerSql, " from (")) {
      return "derived-table/sub-query";
    }
    return null;
  }

  private static String buildClause(String column, boolean softDelete) {
    String clause = column + " = :" + TENANT_PARAM;
    if (softDelete) {
      clause += " AND deleted = 0";
    }
    return clause;
  }

  /** 无锚点、无 JOIN 时的启发式注入：仅对"单表 + 可选 WHERE、无子查询"的简单查询安全； 复杂查询已由上层拦截，这里不再抛异常。 */
  private static String injectSimple(String sql, String clause) {
    String lower = sql.toLowerCase();
    int whereIdx = indexOfTopLevelKeyword(lower, " where ");
    if (whereIdx >= 0) {
      // 已有 WHERE：在其结束位置前追加 " AND <clause>"。
      int end = findTopLevelBoundary(lower, whereIdx + " where ".length());
      return sql.substring(0, end) + " AND " + clause + sql.substring(end);
    }
    // 单表、无 WHERE：在首个顶层边界（GROUP BY / ORDER BY / ...）前注入 WHERE；无边界则追加到末尾。
    int end = findTopLevelBoundary(lower, 0);
    return sql.substring(0, end) + " WHERE " + clause + sql.substring(end);
  }

  /** 在 {@code from} 之后找首个"顶层（括号深度 0、不在字符串字面量内）"的边界关键字位置；找不到返回字符串长度（末尾）。 */
  private static int findTopLevelBoundary(String lowerSql, int from) {
    int depth = 0;
    boolean inString = false;
    int i = from;
    int n = lowerSql.length();
    while (i < n) {
      char c = lowerSql.charAt(i);
      if (inString) {
        if (c == '\'') {
          inString = false;
        }
        i++;
        continue;
      }
      if (c == '\'') {
        inString = true;
        i++;
        continue;
      }
      if (c == '(') {
        depth++;
        i++;
        continue;
      }
      if (c == ')') {
        if (depth > 0) {
          depth--;
        }
        i++;
        continue;
      }
      if (depth == 0) {
        for (String kw : BOUNDARY_KEYWORDS) {
          if (lowerSql.startsWith(kw, i)) {
            return i;
          }
        }
      }
      i++;
    }
    return n;
  }

  private static int indexOfTopLevelKeyword(String lowerSql, String keyword) {
    int depth = 0;
    boolean inString = false;
    int i = 0;
    int n = lowerSql.length();
    while (i < n) {
      char c = lowerSql.charAt(i);
      if (inString) {
        if (c == '\'') {
          inString = false;
        }
        i++;
        continue;
      }
      if (c == '\'') {
        inString = true;
        i++;
        continue;
      }
      if (c == '(') {
        depth++;
        i++;
        continue;
      }
      if (c == ')') {
        if (depth > 0) {
          depth--;
        }
        i++;
        continue;
      }
      if (depth == 0 && lowerSql.startsWith(keyword, i)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static boolean containsTopLevelKeyword(String lowerSql, String keyword) {
    return indexOfTopLevelKeyword(lowerSql, keyword) >= 0;
  }
}
