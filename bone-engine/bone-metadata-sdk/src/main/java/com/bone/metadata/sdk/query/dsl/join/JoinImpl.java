package com.bone.metadata.sdk.query.dsl.join;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.query.dsl.condition.ConditionImpl;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;

/**
 * 关联查询构建器实现类 - 提供具体的关联查询功能实现
 */
public class JoinImpl<T, J> implements Join<T, J> {

    private final QueryContext<T> queryContext;
    private final QueryBuilder.FluentQuery<T> fluentQuery;
    private final String joinEntityAlias;
    private boolean isOrCondition = false;

    public JoinImpl(QueryContext<T> queryContext, QueryBuilder.FluentQuery<T> fluentQuery, String joinEntityAlias) {
        this.queryContext = queryContext;
        this.fluentQuery = fluentQuery;
        this.joinEntityAlias = joinEntityAlias;
    }

    @Override
    public <F, JF> QueryBuilder.FluentQuery<T> on(String entityField, String joinEntityField) {
        addJoinCondition(entityField, "=", joinEntityField);
        return fluentQuery;
    }

    @Override
    public <F, JF> QueryBuilder.FluentQuery<T> on(Condition<T, F> entityCondition, Condition<J, JF> joinCondition) {
        // 这里简化实现，实际应该更复杂地处理两个条件对象
        throw new UnsupportedOperationException("Complex join conditions not supported yet");
    }

    @Override
    public <F, JF> QueryBuilder.FluentQuery<T> onWhere(String entityField, String operator, Object value) {
        QueryContext.Join.JoinCondition joinCondition = new QueryContext.Join.JoinCondition();
        joinCondition.setEntityField(entityField);
        joinCondition.setOperator(operator);
        joinCondition.setValue(value);
        joinCondition.setOr(isOrCondition);
        
        queryContext.getCurrentJoin().addJoinCondition(joinCondition);
        isOrCondition = false;
        return fluentQuery;
    }

    @Override
    public <F> Condition<T, F> where(String fieldName) {
        return new ConditionImpl<>(queryContext, fieldName, false);
    }

    @Override
    public QueryBuilder.FluentQuery<T> orderBy(String fieldName, boolean isAsc) {
        return fluentQuery.orderBy(fieldName, isAsc);
    }

    @Override
    public QueryBuilder.FluentQuery<T> limit(int limit) {
        return fluentQuery.limit(limit);
    }

    @Override
    public QueryBuilder.FluentQuery<T> offset(int offset) {
        return fluentQuery.offset(offset);
    }

    @Override
    public QueryBuilder.FluentQuery<T> groupBy(String... fieldNames) {
        return fluentQuery.groupBy(fieldNames);
    }

    @Override
    public <JF> Join<T, J> andOn(String entityField, String joinEntityField) {
        addJoinCondition(entityField, "=", joinEntityField);
        isOrCondition = false;
        return this;
    }

    @Override
    public <JF> Join<T, J> orOn(String entityField, String joinEntityField) {
        addJoinCondition(entityField, "=", joinEntityField);
        isOrCondition = true;
        return this;
    }

    /**
     * 添加关联条件
     */
    private void addJoinCondition(String entityField, String operator, String joinEntityField) {
        QueryContext.Join.JoinCondition joinCondition = new QueryContext.Join.JoinCondition();
        joinCondition.setEntityField(entityField);
        joinCondition.setOperator(operator);
        joinCondition.setJoinEntityField(joinEntityField);
        joinCondition.setOr(isOrCondition);
        
        queryContext.getCurrentJoin().addJoinCondition(joinCondition);
    }
}