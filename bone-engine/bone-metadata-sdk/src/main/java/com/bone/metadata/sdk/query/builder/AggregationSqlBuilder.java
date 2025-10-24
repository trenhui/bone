package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.query.context.AggregationContext;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.support.util.SqlInjectionPreventer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.StringJoiner;

public class AggregationSqlBuilder implements SqlQueryBuilder<AggregationContext> {
    @Override
    public CompiledQuery build(AggregationContext ctx) {
        ctx.validate();

        // 构建SELECT列表：包含分组字段和聚合表达式
        StringJoiner selectJoiner = new StringJoiner(", ");

        // 添加分组字段
        if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
            for (String groupBy : ctx.getGroupByFields()) {
                String safeGroupBy = SqlInjectionPreventer.sanitizeFieldName(groupBy);
                selectJoiner.add(safeGroupBy);
            }
        }

        // 添加聚合表达式
        for (String agg : ctx.getAggregations()) {
            String safeAgg = SqlInjectionPreventer.sanitizeAggregation(agg);
            selectJoiner.add(safeAgg);
        }

        String selectList = selectJoiner.toString();
        String table = ctx.getTableMetadata().getName();

        // 构建WHERE子句
        String whereSql = ctx.getCriteria().whereSql();
        String whereClause = "";
        if (StringUtils.hasText(whereSql)) {
            whereClause = whereSql.trim().toUpperCase().startsWith("WHERE") ?
                    " " + whereSql : " WHERE " + whereSql;
        }

        // 构建GROUP BY子句
        String groupByClause = "";
        if (!CollectionUtils.isEmpty(ctx.getGroupByFields())) {
            StringJoiner groupByJoiner = new StringJoiner(", ");
            for (String groupBy : ctx.getGroupByFields()) {
                String safeGroupBy = SqlInjectionPreventer.sanitizeFieldName(groupBy);
                groupByJoiner.add(safeGroupBy);
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
        StringBuilder sql = new StringBuilder("SELECT " + selectList
                + " FROM " + table + " m "
                + whereClause
                + groupByClause
                + havingClause);

        // 添加分页逻辑（如果需要）
        if (ctx.getCriteria() != null && ctx.getCriteria().getPageSize() > 0) {
            int pageSize = ctx.getCriteria().getPageSize();
            int page = ctx.getCriteria().getPageNo();
            int offset = (page - 1) * pageSize;

            sql.append(" LIMIT ").append(pageSize)
                    .append(" OFFSET ").append(offset);
        }

        return new CompiledQuery(sql.toString(), ctx.getCriteria().getParameters());
    }
}