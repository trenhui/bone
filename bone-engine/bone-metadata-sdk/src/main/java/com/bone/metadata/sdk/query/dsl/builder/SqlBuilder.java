package com.bone.metadata.sdk.query.dsl.builder;

import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Condition;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Join;
import com.bone.metadata.sdk.query.dsl.context.QueryContext.Order;
import com.bone.metadata.sdk.query.dsl.util.SqlSafeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        sql.setLength(0);
        parameters.clear();

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
        sql.setLength(0);
        parameters.clear();

        // 确保COUNT查询只返回单列
        sql.append("SELECT COUNT(*)");
        
        // 简化COUNT查询，避免JOIN操作影响结果列数
        String tableName = getTableName(queryContext.getEntityClass());
        String entityAlias = queryContext.getEntityAlias();
        sql.append(" FROM " + tableName + " " + entityAlias);
        
        // 只添加WHERE条件
        buildWhereClause();
        
        return sql.toString();
    }

    /**
     * 构建聚合查询SQL
     */
    public String buildAggregateSql(String function, String fieldName) {
        sql.setLength(0);
        parameters.clear();

        // 验证字段名
        if (!SqlSafeUtils.isValidFieldName(fieldName)) {
            throw new IllegalArgumentException("Invalid field name for aggregate: " + fieldName);
        }

        String columnName = camelToSnake(fieldName);
        sql.append("SELECT ").append(function).append("(")
                .append(queryContext.getEntityAlias()).append(".").append(columnName).append(")");

        buildFromClause();
        buildJoinClauses();
        buildWhereClause();
        buildGroupByClause();

        return sql.toString();
    }

    /**
     * 构建投影查询SQL（选择特定字段）
     */
    public String buildProjectionSql(String fieldName) {
        sql.setLength(0);
        parameters.clear();

        // 验证字段名
        if (!SqlSafeUtils.isValidFieldName(fieldName)) {
            throw new IllegalArgumentException("Invalid field name for projection: " + fieldName);
        }

        String columnName = camelToSnake(fieldName);
        sql.append("SELECT ").append(queryContext.getEntityAlias()).append(".").append(columnName);

        buildFromClause();
        buildJoinClauses();
        buildWhereClause();
        buildGroupByClause();
        buildOrderByClause();
        buildLimitOffsetClause();

        return sql.toString();
    }

    /**
     * 构建完整的查询 CompiledQuery
     */
    public CompiledQuery buildQuery() {
        String sql = buildSelectSql();
        return createCompiledQuery(sql);
    }

    /**
     * 构建计数查询 CompiledQuery
     */
    public CompiledQuery buildCountQuery() {
        String sql = buildCountSql();
        return createCompiledQuery(sql);
    }

    /**
     * 构建聚合查询 CompiledQuery
     */
    public CompiledQuery buildAggregateQuery(String function, String fieldName) {
        String sql = buildAggregateSql(function, fieldName);
        return createCompiledQuery(sql);
    }

    /**
     * 构建投影查询 CompiledQuery
     */
    public CompiledQuery buildProjectionQuery(String fieldName) {
        String sql = buildProjectionSql(fieldName);
        return createCompiledQuery(sql);
    }

    /**
     * 创建 CompiledQuery 对象
     */
    private CompiledQuery createCompiledQuery(String sql) {
        // 使用命名参数，避免参数位置不匹配问题
        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            paramMap.put("param" + i, parameters.get(i));
        }
        return new CompiledQuery(sql, paramMap);
    }

    // /**
    //  * 参数转换工具方法
    //  */
    // private Map<String, Object> convertToParamMap(List<Object> params) {
    //     Map<String, Object> paramMap = new HashMap<>();
    //     if (params != null) {
    //         for (int i = 0; i < params.size(); i++) {
    //             paramMap.put("p" + i, params.get(i));
    //         }
    //     }
    //     return paramMap;
    // }

    /**
     * 获取SQL参数
     */
    public List<Object> getParameters() {
        return parameters;
    }

    // ===== SQL 子句构建方法 =====

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
                            .append(camelToSnake(joinCondition.getEntityField())).append(" ")
                            .append(joinCondition.getOperator()).append(" ")
                            .append(joinAlias).append(".")
                            .append(camelToSnake(joinCondition.getJoinEntityField()));
                } else if (joinCondition.getValue() != null) {
                    // 实体字段与值比较的条件
                    sql.append(queryContext.getEntityAlias()).append(".")
                            .append(camelToSnake(joinCondition.getEntityField())).append(" ")
                            .append(joinCondition.getOperator()).append(" :param").append(parameters.size());
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
        String columnName = camelToSnake(fieldName);
        String operator = condition.getOperator();
        Object value1 = condition.getValue1();
        Object value2 = condition.getValue2();

        sql.append(queryContext.getEntityAlias()).append(".").append(columnName).append(" ").append(operator);

        switch (operator) {
            case "IN":
            case "NOT IN":
                if (value1 instanceof List) {
                    List<?> values = (List<?>) value1;
                    if (values.isEmpty()) {
                        sql.append(" (NULL)"); // 处理空列表情况
                    } else {
                        StringBuilder placeholders = new StringBuilder();
                        for (int i = 0; i < values.size(); i++) {
                            if (i > 0) {
                                placeholders.append(", ");
                            }
                            String paramName = ":param" + parameters.size();
                            placeholders.append(paramName);
                            parameters.add(values.get(i));
                        }
                        sql.append(" (").append(placeholders).append(")");
                    }
                }
                break;
            case "BETWEEN":
                sql.append(" :param").append(parameters.size());
                parameters.add(value1);
                sql.append(" AND :param").append(parameters.size());
                parameters.add(value2);
                break;
            case "IS NULL":
            case "IS NOT NULL":
                // 不需要添加参数
                break;
            default:
                sql.append(" :param").append(parameters.size());
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
                        .map(field -> queryContext.getEntityAlias() + "." + camelToSnake(field))
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
                        .map(order -> queryContext.getEntityAlias() + "." + camelToSnake(order.getFieldName()) +
                                (order.isAsc() ? " ASC" : " DESC"))
                        .collect(Collectors.joining(", ")));
    }

    /**
     * 构建LIMIT和OFFSET子句
     */
    private void buildLimitOffsetClause() {
        if (queryContext.getLimit() != null) {
            sql.append(" LIMIT :param").append(parameters.size());
            parameters.add(queryContext.getLimit());

            if (queryContext.getOffset() != null) {
                sql.append(" OFFSET :param").append(parameters.size());
                parameters.add(queryContext.getOffset());
            }
        }
    }

    // ===== 工具方法 =====

    /**
     * 驼峰命名转下划线命名
     */
    private String camelToSnake(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(input.charAt(0)));
        for (int i = 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 获取表名 - 支持自定义@Table注解
     */
    private String getTableName(Class<?> entityClass) {
        try {
            // 检查是否有自定义的@Table注解
            Class<?> tableAnnotationClass = null;
            try {
                // 尝试加载自定义Table注解
                tableAnnotationClass = Class.forName("com.bone.metadata.sdk.domain.annotation.Table");
                // 获取注解实例（使用反射避免直接引用）
                java.lang.reflect.Method getAnnotationMethod = Class.class.getMethod("getAnnotation", Class.class);
                Object tableAnnotation = getAnnotationMethod.invoke(entityClass, tableAnnotationClass);

                if (tableAnnotation != null) {
                    // 获取name属性
                    java.lang.reflect.Method nameMethod = tableAnnotationClass.getMethod("value");
                    Object tableNameObj = nameMethod.invoke(tableAnnotation);
                    if (tableNameObj instanceof String) {
                        String tableName = (String) tableNameObj;
                        if (!tableName.isEmpty()) {
                            return tableName;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // 自定义注解不存在，继续处理
            }
        } catch (Exception e) {
            // 解析注解失败，使用默认命名规则
        }

        // 默认使用实体类名作为表名（转为小写并添加s后缀）
        String className = entityClass.getSimpleName().toLowerCase();
        if (className.endsWith("s") || className.endsWith("x") ||
                className.endsWith("z") || (className.length() > 1 &&
                className.endsWith("h") && !className.endsWith("ch") &&
                !className.endsWith("sh"))) {
            return className;
        } else {
            return className + "s";
        }
    }
}