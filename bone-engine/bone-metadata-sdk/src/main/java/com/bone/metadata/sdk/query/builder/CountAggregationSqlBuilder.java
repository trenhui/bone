package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.query.context.AggregationContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.support.util.SqlInjectionPreventer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.StringJoiner;

/**
 * 专用计数查询构建器，用于生成计算分组结果总数的SQL
 * 不包含分页和字段选择，只返回总数
 */
public class CountAggregationSqlBuilder implements SqlQueryBuilder<AggregationContext> {

    @Override
    public CompiledQuery build(AggregationContext ctx) {
        ctx.validate();

        // 构建基础计数查询
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM (");

        // 构建子查询（不包含分页和字段选择）
        String subQuery = buildSubQuery(ctx);
        sql.append(subQuery).append(") count_table");

        return new CompiledQuery(sql.toString(), ctx.getCriteria().getParameters());
    }

    /**
     * 构建子查询（不包含分页和字段选择）
     */
    private String buildSubQuery(AggregationContext ctx) {
        // 1. 构建SELECT子句：只选择1作为占位符
        String selectSql = "1";

        // 2. 表名
        String table = ctx.getTableMetadata().getName();

        // 3. WHERE子句
        String whereSql = ctx.getCriteria().whereSql();
        String whereClause = "";
        if (StringUtils.hasText(whereSql)) {
            whereClause = whereSql.trim().toUpperCase().startsWith("WHERE")
                    ? " " + whereSql
                    : " WHERE " + whereSql;
        }

        // 4. GROUP BY子句
        String groupByClause = "";
        if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
            StringJoiner groupByJoiner = new StringJoiner(", ");
            for (String groupBy : ctx.getGroupByFields()) {
                String safeGroupBy = SqlInjectionPreventer.sanitizeFieldName(groupBy);
                String bareColumnName = safeGroupBy.contains(".")
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
        return "SELECT " + selectSql +
                " FROM " + table + " m" +
                whereClause +
                groupByClause +
                havingClause;
    }
}