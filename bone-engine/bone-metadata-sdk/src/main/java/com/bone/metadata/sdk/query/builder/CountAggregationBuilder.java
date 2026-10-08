package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.context.AggregationContext;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.support.util.SqlInjectionPreventer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.util.CollectionUtils;

/**
 * 专用计数查询构建器，用于生成计算分组结果总数的SQL 不包含分页和字段选择，只返回总数。
 *
 * <p>与 {@link CountBuilder} 对齐：统一注入 {@code m.deleted = false}（软删表且未 opt-in）与租户过滤 （ADR-0029
 * fail-closed，复用 {@link TenantFilterInjector}）。子查询内聚合同样必须受租户/软删约束， 否则外层 COUNT(*) 会放大越权/含脏数据的结果。
 */
public class CountAggregationBuilder implements SqlQueryBuilder<AggregationContext> {

  @Override
  public CompiledQuery build(AggregationContext ctx) {
    ctx.validate();
    CompiledQuery sub = buildSubQuery(ctx);
    String sql = "SELECT COUNT(*) FROM (" + sub.getSql() + ") count_table";
    return new CompiledQuery(sql, sub.getParameters());
  }

  /** 构建子查询（不包含分页和字段选择） */
  private CompiledQuery buildSubQuery(AggregationContext ctx) {
    TableMetadata tbl = ctx.getTableMetadata();
    Criteria<?> c = ctx.getCriteria();

    // 与 AggregationBuilder 同构：聚合通道不 JOIN ext_data_reserved，扩展表字段条件显式失败关闭，
    // 避免静默丢弃过滤条件导致分组计数偏大。
    if (c.requiresExtJoin()) {
      throw new IllegalArgumentException(
          "聚合计数不支持扩展表字段条件（requiresExtJoin=true）：聚合通道不 JOIN ext_data_reserved，"
              + "无法把扩展字段解析为物理列。请仅用主表字段做聚合。表: "
              + tbl.getName());
    }

    Map<String, Object> params = new LinkedHashMap<>(c.getParameters());

    // WHERE 片段：主表条件 + 软删 + 租户（与 CountBuilder 同构）
    List<String> where = new ArrayList<>();
    where.addAll(c.getMainConditions().stream().map(Condition::toSql).toList());
    if (tbl.isSoftDeletable() && !ctx.isIncludeDeleted()) {
      where.add("m.deleted = false");
    }
    TenantFilterInjector.inject(where, params, tbl, c, true);

    String whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

    // 4. GROUP BY子句
    String groupByClause = "";
    if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
      StringJoiner groupByJoiner = new StringJoiner(", ");
      for (String groupBy : ctx.getGroupByFields()) {
        String safeGroupBy = SqlInjectionPreventer.sanitizeFieldName(groupBy);
        String bareColumnName =
            safeGroupBy.contains(".")
                ? safeGroupBy.substring(safeGroupBy.lastIndexOf('.') + 1)
                : safeGroupBy;
        groupByJoiner.add("m." + bareColumnName);
      }
      groupByClause = " GROUP BY " + groupByJoiner.toString();
    }

    // 5. HAVING子句
    String havingClause = "";
    if (!CollectionUtils.isEmpty(ctx.getHavingConditions())) {
      StringJoiner havingJoiner = new StringJoiner(" AND ");
      for (String having : ctx.getHavingConditions()) {
        String safeHaving = SqlInjectionPreventer.sanitizeHavingCondition(having);
        havingJoiner.add(safeHaving);
      }
      havingClause = " HAVING " + havingJoiner.toString();
    }

    // 6. 构建完整子查询
    String subSql =
        "SELECT "
            + "1"
            + " FROM "
            + tbl.getName()
            + " m"
            + whereClause
            + groupByClause
            + havingClause;
    return new CompiledQuery(subSql, params);
  }
}
