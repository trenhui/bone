package com.bone.metadata.sdk.query.dsl.builder;

import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Condition;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Join;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL构建器 - 根据查询上下文构建参数化SQL语句
 */
public class SqlBuilder<T> {

    private final QueryContext<T> queryContext;
    private final StringBuilder sql = new StringBuilder();
    private final List<Object> parameters = new ArrayList<>();
    private int paramIndex = 1;

    public SqlBuilder(QueryContext<T> queryContext) {
        this.queryContext = queryContext;
    }

    /**
     * 构建完整的SELECT查询SQL
     */
    public String buildSelectSql() {
        buildSelectClause();
        buildFromClause();
        buildJoinClauses();
        buildWhereClause();
        buildGroupByClause();
        buildOrderByClause();
        buildLimitOffsetClause();
        return sql.toString();
    }

    /**
     * 构建COUNT查询SQL
     */
    public String buildCountSql() {
        sql.append("SELECT COUNT(*)");
        buildFromClause();
        buildJoinClauses();
        buildWhereClause();
        return sql.toString();
    }

    /**
     * 获取SQL参数
     */
    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * 构建SELECT子句
     */
    private void buildSelectClause() {
        String entityAlias = queryContext.getEntityAlias();
        sql.append("SELECT ").append(entityAlias).append(".*");
    }

    /**
     * 构建FROM子句
     */
    private void buildFromClause() {
        String tableName = getTableName(queryContext.getEntityClass());
        String entityAlias = queryContext.getEntityAlias();
        sql.append(" FROM ").append(tableName).append(" ").append(entityAlias);
    }

    /**
     * 构建JOIN子句
     */
    private void buildJoinClauses() {
        for (Join join : queryContext.getJoins()) {
            String joinType = join.getJoinType().name();
            String joinTableName = getTableName(join.getJoinClass());
            String joinAlias = join.getJoinEntityAlias();
            
            sql.append(" ").append(joinType).append(" JOIN ")
               .append(joinTableName).append(" ").append(joinAlias).append(" ON ");
            
            // 构建关联条件
            for (int i = 0; i < join.getJoinConditions().size(); i++) {
                Join.JoinCondition joinCondition = join.getJoinConditions().get(i);
                
                if (i > 0) {
                    sql.append(joinCondition.isOr() ? " OR " : " AND ");
                }
                
                if (joinCondition.getJoinEntityField() != null) {
                    // 实体字段与关联表字段相等的条件
                    sql.append(queryContext.getEntityAlias()).append(".")
                       .append(joinCondition.getEntityField()).append(" ")
                       .append(joinCondition.getOperator()).append(" ")
                       .append(joinAlias).append(".")
                       .append(joinCondition.getJoinEntityField());
                } else if (joinCondition.getValue() != null) {
                    // 实体字段与值比较的条件
                    sql.append(queryContext.getEntityAlias()).append(".")
                       .append(joinCondition.getEntityField()).append(" ")
                       .append(joinCondition.getOperator()).append(" ?");
                    parameters.add(joinCondition.getValue());
                }
            }
        }
    }

    /**
     * 构建WHERE子句
     */
    private void buildWhereClause() {
        List<Condition> conditions = queryContext.getConditions();
        if (conditions.isEmpty()) {
            return;
        }

        sql.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            
            if (i > 0) {
                sql.append(condition.isOr() ? " OR " : " AND ");
            }
            
            buildCondition(condition);
        }
    }

    /**
     * 构建条件表达式
     */
    private void buildCondition(Condition condition) {
        String fieldName = condition.getFieldName();
        String operator = condition.getOperator();
        Object value1 = condition.getValue1();
        Object value2 = condition.getValue2();

        sql.append(queryContext.getEntityAlias()).append(".").append(fieldName).append(" ").append(operator);

        switch (operator) {
            case "IN":
            case "NOT IN":
                if (value1 instanceof List) {
                    List<?> values = (List<?>) value1;
                    if (values.isEmpty()) {
                        sql.append(" (NULL)"); // 处理空列表情况
                    } else {
                        sql.append(" (").append("?,".repeat(values.size() - 1)).append("?)");
                        parameters.addAll(values);
                    }
                }
                break;
            case "BETWEEN":
                sql.append(" ? AND ?");
                parameters.add(value1);
                parameters.add(value2);
                break;
            case "IS NULL":
            case "IS NOT NULL":
                // 不需要添加参数
                break;
            default:
                sql.append(" ?");
                parameters.add(value1);
                break;
        }
    }

    /**
     * 构建GROUP BY子句
     */
    private void buildGroupByClause() {
        List<String> groupByFields = queryContext.getGroupByFields();
        if (groupByFields.isEmpty()) {
            return;
        }

        sql.append(" GROUP BY ")
           .append(groupByFields.stream()
               .map(field -> queryContext.getEntityAlias() + "." + field)
               .collect(Collectors.joining(", ")));
    }

    /**
     * 构建ORDER BY子句
     */
    private void buildOrderByClause() {
        List<Order> orders = queryContext.getOrders();
        if (orders.isEmpty()) {
            return;
        }

        sql.append(" ORDER BY ")
           .append(orders.stream()
               .map(order -> queryContext.getEntityAlias() + "." + order.getFieldName() + 
                            (order.isAsc() ? " ASC" : " DESC"))
               .collect(Collectors.joining(", ")));
    }

    /**
     * 构建LIMIT和OFFSET子句
     */
    private void buildLimitOffsetClause() {
        if (queryContext.getLimit() != null) {
            sql.append(" LIMIT ?");
            parameters.add(queryContext.getLimit());
            
            if (queryContext.getOffset() != null) {
                sql.append(" OFFSET ?");
                parameters.add(queryContext.getOffset());
            }
        }
    }

    /**
     * 获取表名（简化实现，实际可能需要从实体注解或元数据中获取）
     */
    private String getTableName(Class<?> entityClass) {
        // 这里简化实现，实际应该从实体注解或元数据中获取表名
        return entityClass.getSimpleName();
    }
}