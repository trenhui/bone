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
 * 聚合查询构建器（GROUP BY + 聚合表达式 + HAVING + 分页）。
 *
 * <p>与 {@link SelectBuilder}/{@link CountBuilder} 对齐：统一注入 {@code m.deleted = false}（软删表且未 opt-in
 * 包含已删）与租户过滤（ADR-0029 fail-closed，复用 {@link TenantFilterInjector}）。此前聚合通道漏注这两类谓词， 会导致租户表聚合越权（违反
 * ADR-0029）与软删表聚合含脏数据——这是 HC-006 中两个指标网关被迫回退裸 JDBC 的根因。
 */
public class AggregationBuilder implements SqlQueryBuilder<AggregationContext> {
  @Override
  public CompiledQuery build(AggregationContext ctx) {
    ctx.validate();

    TableMetadata tbl = ctx.getTableMetadata();
    Criteria<?> c = ctx.getCriteria();

    // 聚合通道不 JOIN ext_data_reserved：扩展表字段条件无法解析为物理列，
    // 历史实现会拼出 `ext.<col>`（未知别名 → SQL 报错）。这里显式失败关闭，
    // 避免「静默丢弃过滤条件」导致聚合结果偏大（指标失真）。
    if (c.requiresExtJoin()) {
      throw new IllegalArgumentException(
          "聚合查询不支持扩展表字段条件（requiresExtJoin=true）：聚合通道不 JOIN ext_data_reserved，"
              + "无法把扩展字段解析为物理列。请仅用主表字段做聚合。表: "
              + tbl.getName());
    }

    // 拷贝用户传入的参数，并承载租户注入追加的命名参数
    Map<String, Object> params = new LinkedHashMap<>(c.getParameters());

    // WHERE 片段：主表条件 + 软删 + 租户（与 SelectBuilder 同构，保证 fail-closed 不变量）
    List<String> where = new ArrayList<>();
    where.addAll(c.getMainConditions().stream().map(Condition::toSql).toList());
    if (tbl.isSoftDeletable() && !ctx.isIncludeDeleted()) {
      where.add("m.deleted = false");
    }
    TenantFilterInjector.inject(where, params, tbl, c, true);

    String whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

    // 构建SELECT列表：包含分组字段和聚合表达式
    StringJoiner selectJoiner = new StringJoiner(", ");

    // 添加分组字段（统一 m. 限定，与 GROUP BY / CountAggregationBuilder 同构）
    if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
      for (String groupBy : ctx.getGroupByFields()) {
        selectJoiner.add(SqlInjectionPreventer.qualifyWithAlias(groupBy, "m"));
      }
    }

    // 添加聚合表达式
    for (String agg : ctx.getAggregations()) {
      String safeAgg = SqlInjectionPreventer.sanitizeAggregation(agg);
      selectJoiner.add(safeAgg);
    }

    String selectList = selectJoiner.toString();
    String table = tbl.getName();

    // 构建GROUP BY子句（与 SELECT 列表同源，保证引用同一表达式）
    String groupByClause = "";
    if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
      StringJoiner groupByJoiner = new StringJoiner(", ");
      for (String groupBy : ctx.getGroupByFields()) {
        groupByJoiner.add(SqlInjectionPreventer.qualifyWithAlias(groupBy, "m"));
      }
      groupByClause = " GROUP BY " + groupByJoiner.toString();
    }

    // 构建HAVING子句
    String havingClause = "";
    if (!CollectionUtils.isEmpty(ctx.getHavingConditions())) {
      StringJoiner havingJoiner = new StringJoiner(" AND ");
      for (String having : ctx.getHavingConditions()) {
        String safeHaving = SqlInjectionPreventer.sanitizeHavingCondition(having);
        havingJoiner.add(safeHaving);
      }
      havingClause = " HAVING " + havingJoiner.toString();
    }

    // 构建完整SQL
    StringBuilder sql =
        new StringBuilder(
            "SELECT "
                + selectList
                + " FROM "
                + table
                + " m"
                + whereClause
                + groupByClause
                + havingClause);

    // 添加分页逻辑（显式分页优先，否则回退 Criteria 上的分页状态）
    int effSize = ctx.getSize() != null ? ctx.getSize() : (c != null ? c.getSize() : 0);
    if (effSize > 0) {
      int effPage = ctx.getPage() != null ? ctx.getPage() : (c != null ? c.getPage() : 1);
      int offset = (effPage - 1) * effSize;
      sql.append(" LIMIT ").append(effSize).append(" OFFSET ").append(offset);
    }

    return new CompiledQuery(sql.toString(), params);
  }
}
