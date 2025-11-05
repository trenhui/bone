package com.bone.metadata.sdk.query.dsl;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.query.dsl.builder.SqlBuilder;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.query.dsl.condition.ConditionImpl;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.query.dsl.join.Join;
import com.bone.metadata.sdk.query.dsl.join.JoinImpl;
import com.bone.metadata.sdk.query.dsl.util.SqlSafeUtils;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.support.function.SFunction;
import com.bone.metadata.sdk.support.util.SqlUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认流畅查询实现 - 纯Lambda版本
 * 基于业界最佳实践，提供完全类型安全的API，复用现有组件
 */
public class DefaultFluentQuery<T> implements FluentQuery<T> {

    private final QueryContext<T> queryContext;
    private final SqlExecutor sqlExecutor;

    public DefaultFluentQuery(Class<T> entityClass, SqlExecutor sqlExecutor) {
        this(entityClass, sqlExecutor, "t");
    }

    public DefaultFluentQuery(Class<T> entityClass, SqlExecutor sqlExecutor, String alias) {
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SqlExecutor cannot be null");
        this.queryContext = new QueryContext<>(entityClass, alias, this);
    }


    // ===== 条件查询实现 =====

    @Override
    public <F> Condition<T, F> where(SFunction<T, F> fieldGetter) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "where");
        // 明确指定类型参数，解决类型推断问题
        return new ConditionImpl<T, F>(queryContext, fieldName);
    }

    @Override
    public FluentQuery<T> where(Consumer<WhereBuilder<T>> conditionBuilder) {
        DefaultWhereBuilder builder = new DefaultWhereBuilder();
        conditionBuilder.accept(builder);
        return this;
    }

    // ===== 排序实现 =====

    @Override
    public FluentQuery<T> orderBy(SFunction<T, ?> fieldGetter, boolean isAsc) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "orderBy");
        queryContext.addOrder(new QueryContext.Order(fieldName, isAsc));
        return this;
    }

    @Override
    public FluentQuery<T> orderByAsc(SFunction<T, ?> fieldGetter) {
        return orderBy(fieldGetter, true);
    }

    @Override
    public FluentQuery<T> orderByDesc(SFunction<T, ?> fieldGetter) {
        return orderBy(fieldGetter, false);
    }

    // ===== 分页控制实现 =====

    @Override
    public FluentQuery<T> limit(int limit) {
        validateNonNegative(limit, "Limit");
        queryContext.setLimit(limit);
        return this;
    }

    @Override
    public FluentQuery<T> offset(int offset) {
        validateNonNegative(offset, "Offset");
        queryContext.setOffset(offset);
        return this;
    }

    // ===== 分组实现 =====

    @Override
    public FluentQuery<T> groupBy(Function<T, ?>... fieldGetters) {
        for (Function<T, ?> fieldGetter : fieldGetters) {
            String fieldName = SqlUtil.extractFieldName(fieldGetter);
            validateFieldName(fieldName, "groupBy");
            queryContext.addGroupByFields(fieldName);
        }
        return this;
    }

    @Override
    public <F> Condition<T, F> having(SFunction<T, F> fieldGetter) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "having");
        // 明确指定类型参数，解决类型推断问题
        return new ConditionImpl<T, F>(queryContext, fieldName, false);
    }

    // ===== 关联查询实现 =====

    @Override
    public <J> Join<T, J> join(Class<J> joinClass, String joinAlias) {
        validateJoinAlias(joinAlias);
        return createJoin(joinClass, joinAlias, QueryContext.JoinType.INNER);
    }

    @Override
    public <J> Join<T, J> leftJoin(Class<J> joinClass, String joinAlias) {
        validateJoinAlias(joinAlias);
        return createJoin(joinClass, joinAlias, QueryContext.JoinType.LEFT);
    }

    @Override
    public <J> Join<T, J> rightJoin(Class<J> joinClass, String joinAlias) {
        validateJoinAlias(joinAlias);
        return createJoin(joinClass, joinAlias, QueryContext.JoinType.RIGHT);
    }

    @Override
    public <J> Join<T, J> fullJoin(Class<J> joinClass, String joinAlias) {
        validateJoinAlias(joinAlias);
        return createJoin(joinClass, joinAlias, QueryContext.JoinType.FULL);
    }

    @Override
    public <J> FluentQuery<T> joinOn(Class<J> joinClass, String joinAlias,
                                     Function<T, ?> entityFieldGetter, Function<J, ?> joinFieldGetter) {
        validateJoinAlias(joinAlias);

        String entityField = SqlUtil.extractFieldName(entityFieldGetter);
        String joinField = SqlUtil.extractFieldName(joinFieldGetter);

        validateFieldName(entityField, "join entity field");
        validateFieldName(joinField, "join entity field");

        // 创建关联并设置条件
        queryContext.addJoin(joinClass, joinAlias, QueryContext.JoinType.INNER);
        QueryContext.Join currentJoin = queryContext.getCurrentJoin();

        if (currentJoin != null) {
            QueryContext.Join.JoinCondition joinCondition = new QueryContext.Join.JoinCondition();
            joinCondition.setEntityField(entityField);
            joinCondition.setOperator("=");
            joinCondition.setJoinEntityField(joinField);
            currentJoin.addJoinCondition(joinCondition);
        }

        return this;
    }

    @Override
    public <J> FluentQuery<T> leftJoinOn(Class<J> joinClass, String joinAlias,
                                         Function<T, ?> entityFieldGetter, Function<J, ?> joinFieldGetter) {
        validateJoinAlias(joinAlias);

        String entityField = SqlUtil.extractFieldName(entityFieldGetter);
        String joinField = SqlUtil.extractFieldName(joinFieldGetter);

        validateFieldName(entityField, "join entity field");
        validateFieldName(joinField, "join entity field");

        // 创建左关联并设置条件
        queryContext.addJoin(joinClass, joinAlias, QueryContext.JoinType.LEFT);
        QueryContext.Join currentJoin = queryContext.getCurrentJoin();

        if (currentJoin != null) {
            QueryContext.Join.JoinCondition joinCondition = new QueryContext.Join.JoinCondition();
            joinCondition.setEntityField(entityField);
            joinCondition.setOperator("=");
            joinCondition.setJoinEntityField(joinField);
            currentJoin.addJoinCondition(joinCondition);
        }

        return this;
    }

    private <J> Join<T, J> createJoin(Class<J> joinClass, String joinAlias, QueryContext.JoinType joinType) {
        queryContext.addJoin(joinClass, joinAlias, joinType);
        return new JoinImpl<>(queryContext, this, joinAlias);
    }

    // ===== 新增方法实现 =====

    @Override
    public <F> Condition<T, F> and(SFunction<T, F> fieldGetter) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "and");
        // 设置逻辑操作符为AND
        queryContext.setCurrentLogicalOperator("AND");
        return new ConditionImpl<T, F>(queryContext, fieldName, false);
    }

    @Override
    public <F> Condition<T, F> or(SFunction<T, F> fieldGetter) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "or");
        // 设置逻辑操作符为OR
        queryContext.setCurrentLogicalOperator("OR");
        return new ConditionImpl<T, F>(queryContext, fieldName, true);
    }

    @Override
    public FluentQuery<T> and(Consumer<FluentQuery<T>> groupBuilder) {
        // 开始AND分组
        queryContext.openGroup("AND");
        groupBuilder.accept(this);
        queryContext.closeGroup();
        return this;
    }

    @Override
    public FluentQuery<T> or(Consumer<FluentQuery<T>> groupBuilder) {
        // 开始OR分组
        queryContext.openGroup("OR");
        groupBuilder.accept(this);
        queryContext.closeGroup();
        return this;
    }

    // ===== 查询执行实现 =====

    @Override
    public List<T> list() {
        CompiledQuery query = buildSelectQuery();
        return sqlExecutor.queryList(query, queryContext.getEntityClass());
    }

    @Override
    public T single() {
        List<T> results = list();
        return switch (results.size()) {
            case 0 -> null;
            case 1 -> results.get(0);
            default -> throw new NonUniqueResultException(
                    String.format("Query returned %d results but expected single result", results.size()));
        };
    }

    @Override
    public Optional<T> singleOpt() {
        CompiledQuery query = buildSelectQuery();
        T result = sqlExecutor.querySingle(query, queryContext.getEntityClass());
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<T> first() {
        return limit(1).singleOpt();
    }

    @Override
    public long count() {
        QueryContext<T> countContext = queryContext.cloneWithoutOrderLimit();
        SqlBuilder<T> sqlBuilder = new SqlBuilder<>(countContext);
        return sqlExecutor.count(sqlBuilder.buildCountQuery());
    }

    @Override
    public PageResult<T> page(int pageNum, int pageSize) {
        validatePositive(pageNum, "Page number");
        validatePositive(pageSize, "Page size");

        // 保存原始分页设置
        Integer originalLimit = queryContext.getLimit();
        Integer originalOffset = queryContext.getOffset();

        try {
            // 设置分页参数并查询数据
            int offset = (pageNum - 1) * pageSize;
            queryContext.setLimit(pageSize);
            queryContext.setOffset(offset);

            List<T> records = list();

            // 查询总数
            queryContext.setLimit(originalLimit);
            queryContext.setOffset(originalOffset);
            long total = count();

            return PageResult.of(records, total, pageNum, pageSize);
        } finally {
            // 恢复原始设置
            queryContext.setLimit(originalLimit);
            queryContext.setOffset(originalOffset);
        }
    }

    @Override
    public boolean exists() {
        return count() > 0;
    }

    // ===== 高级功能实现 =====

    @Override
    public <R> List<R> select(SFunction<T, R> fieldGetter, Class<R> resultType) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "select");

        QueryContext<T> projectionContext = queryContext.cloneForProjection(fieldName);
        SqlBuilder<T> sqlBuilder = new SqlBuilder<>(projectionContext);

        return sqlExecutor.query(sqlBuilder.buildQuery(), resultType);
    }

    @Override
    public <R> Optional<R> aggregate(String function, SFunction<T, ?> fieldGetter, Class<R> resultType) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        validateFieldName(fieldName, "aggregate");
        validateAggregateFunction(function);

        SqlBuilder<T> sqlBuilder = new SqlBuilder<>(queryContext);
        CompiledQuery query = sqlBuilder.buildAggregateQuery(function, fieldName);

        R result = sqlExecutor.queryForObject(query, resultType);
        return Optional.ofNullable(result);
    }

    @Override
    public <R> List<R> map(Function<T, R> mapper) {
        return list().stream().map(mapper).collect(Collectors.toList());
    }

    @Override
    public void forEach(Consumer<T> action) {
        list().forEach(action);
    }

    @Override
    public Stream<T> stream() {
        return list().stream();
    }

    // ===== 内部辅助方法 =====

    private CompiledQuery buildSelectQuery() {
        SqlBuilder<T> sqlBuilder = new SqlBuilder<>(queryContext);
        return sqlBuilder.buildQuery();
    }


    private String buildAggregateSql(String function, String fieldName, SqlBuilder<T> sqlBuilder) {
        // 简化实现，实际应该使用SqlBuilder构建完整的聚合查询
        return "SELECT " + function + "(" + fieldName + ") FROM " + getTableName(queryContext.getEntityClass());
    }

    private String getTableName(Class<?> entityClass) {
        // 简化实现，使用类名的小写形式
        return entityClass.getSimpleName().toLowerCase();
    }

    private void validateFieldName(String fieldName, String operation) {
        if (!SqlSafeUtils.isValidFieldName(fieldName)) {
            throw new IllegalArgumentException(
                    String.format("Invalid field name for %s: %s", operation, fieldName));
        }
    }

    private void validateJoinAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Join alias cannot be null or empty");
        }
        if (!SqlSafeUtils.isValidFieldName(alias)) {
            throw new IllegalArgumentException("Invalid join alias: " + alias);
        }
    }

    private void validateAggregateFunction(String function) {
        Set<String> validFunctions = Set.of("COUNT", "SUM", "AVG", "MAX", "MIN");
        if (!validFunctions.contains(function.toUpperCase())) {
            throw new IllegalArgumentException("Invalid aggregate function: " + function);
        }
    }

    private void validateNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private void validatePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    // ===== 内部构建器类 =====

    /**
     * 默认WHERE构建器实现
     */
    private class DefaultWhereBuilder implements WhereBuilder<T> {
        @Override
        public <F> Condition<T, F> and(SFunction<T, F> fieldGetter) {
            queryContext.setCurrentLogicalOperator("AND");
            String fieldName = SqlUtil.extractFieldName(fieldGetter);
            // 明确指定类型参数
            return new ConditionImpl<T, F>(queryContext, fieldName, false);
        }

        @Override
        public <F> Condition<T, F> or(SFunction<T, F> fieldGetter) {
            queryContext.setCurrentLogicalOperator("OR");
            String fieldName = SqlUtil.extractFieldName(fieldGetter);
            // 明确指定类型参数
            return new ConditionImpl<T, F>(queryContext, fieldName, true);
        }
    }

    // ===== 自定义异常类 =====

    /**
     * 非唯一结果异常
     */
    public static class NonUniqueResultException extends RuntimeException {
        public NonUniqueResultException(String message) {
            super(message);
        }
    }
}