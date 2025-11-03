package com.bone.metadata.sdk.query.dsl.condition;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;

import java.util.Collection;

/**
 * 条件构建器实现类 - 提供具体的条件查询功能实现
 */
public class ConditionImpl<T, V> implements Condition<T, V> {

    private final QueryContext<T> queryContext;
    private final String fieldName;
    private final boolean isOrCondition;

    public ConditionImpl(QueryContext<T> queryContext, String fieldName) {
        this(queryContext, fieldName, false);
    }

    public ConditionImpl(QueryContext<T> queryContext, String fieldName, boolean isOrCondition) {
        this.queryContext = queryContext;
        this.fieldName = fieldName;
        this.isOrCondition = isOrCondition;
    }

    @Override
    public QueryBuilder.FluentQuery<T> eq(V value) {
        return addCondition("=", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> neq(V value) {
        return addCondition("<>", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> gt(V value) {
        return addCondition(">", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> gte(V value) {
        return addCondition(">=", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> lt(V value) {
        return addCondition("<", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> lte(V value) {
        return addCondition("<=", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> like(String value) {
        return addCondition("LIKE", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> notLike(String value) {
        return addCondition("NOT LIKE", value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> startsWith(String value) {
        return addCondition("LIKE", value + "%");
    }

    @Override
    public QueryBuilder.FluentQuery<T> endsWith(String value) {
        return addCondition("LIKE", "%" + value);
    }

    @Override
    public QueryBuilder.FluentQuery<T> contains(String value) {
        return addCondition("LIKE", "%" + value + "%");
    }

    @Override
    public QueryBuilder.FluentQuery<T> in(Collection<V> values) {
        return addCondition("IN", values);
    }

    @Override
    public QueryBuilder.FluentQuery<T> notIn(Collection<V> values) {
        return addCondition("NOT IN", values);
    }

    @Override
    public QueryBuilder.FluentQuery<T> isNull() {
        return addCondition("IS NULL", null);
    }

    @Override
    public QueryBuilder.FluentQuery<T> isNotNull() {
        return addCondition("IS NOT NULL", null);
    }

    @Override
    public QueryBuilder.FluentQuery<T> between(V start, V end) {
        QueryContext.Condition condition = new QueryContext.Condition();
        condition.setFieldName(fieldName);
        condition.setOperator("BETWEEN");
        condition.setValue1(start);
        condition.setValue2(end);
        condition.setOr(isOrCondition);
        queryContext.addCondition(condition);
        return queryContext.getFluentQuery();
    }

    @Override
    public <NV> Condition<T, NV> and(Class<T> entityClass, String fieldName) {
        return new ConditionImpl<>(queryContext, fieldName, false);
    }

    @Override
    public <NV> Condition<T, NV> or(Class<T> entityClass, String fieldName) {
        return new ConditionImpl<>(queryContext, fieldName, true);
    }

    /**
     * 添加单个条件
     */
    private QueryBuilder.FluentQuery<T> addCondition(String operator, Object value) {
        QueryContext.Condition condition = new QueryContext.Condition();
        condition.setFieldName(fieldName);
        condition.setOperator(operator);
        condition.setValue1(value);
        condition.setOr(isOrCondition);
        queryContext.addCondition(condition);
        return queryContext.getFluentQuery();
    }
}