package com.bone.metadata.sdk.query.dsl.join;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.query.dsl.condition.ConditionImpl;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.support.function.SFunction;
import com.bone.metadata.sdk.support.util.SqlUtil;


/**
 * 关联查询构建器实现类 - 纯Lambda版本
 * 基于业界最佳实践，提供完全类型安全的关联查询API
 */
public class JoinImpl<T, J> implements Join<T, J> {

    private final QueryContext<T> queryContext;
    private final FluentQuery<T> fluentQuery;
    private final String joinEntityAlias;
    private boolean isOrCondition = false;

    public JoinImpl(QueryContext<T> queryContext, FluentQuery<T> fluentQuery, String joinEntityAlias) {
        this.queryContext = queryContext;
        this.fluentQuery = fluentQuery;
        this.joinEntityAlias = joinEntityAlias;
    }

    // ===== 核心方法实现 =====

    @Override
    public <F, JF> FluentQuery<T> on(SFunction<T, F> entityFieldGetter, SFunction<J, JF> joinFieldGetter) {
        String entityField = SqlUtil.extractFieldName(entityFieldGetter);
        String joinField = SqlUtil.extractFieldName(joinFieldGetter);
        addJoinCondition(entityField, "=", joinField);
        return fluentQuery;
    }

    @Override
    public <F> Condition<T, F> where(SFunction<T, F> fieldGetter) {
        String fieldName = SqlUtil.extractFieldName(fieldGetter);
        return new ConditionImpl<>(queryContext, fieldName, false);
    }

    @Override
    public FluentQuery<T> orderBy(SFunction<T, ?> fieldGetter, boolean isAsc) {
        return fluentQuery.orderBy(fieldGetter, isAsc);
    }

    @Override
    public FluentQuery<T> limit(int limit) {
        return fluentQuery.limit(limit);
    }

    @Override
    public FluentQuery<T> offset(int offset) {
        return fluentQuery.offset(offset);
    }

    @Override
    public FluentQuery<T> groupBy(SFunction<T, ?>... fieldGetters) {
        return fluentQuery.groupBy(fieldGetters);
    }

    // ===== 新增方法实现 =====

    /**
     * AND 关联条件连接
     */
    public <F, JF> Join<T, J> andOn(SFunction<T, F> entityFieldGetter, SFunction<J, JF> joinFieldGetter) {
        String entityField = SqlUtil.extractFieldName(entityFieldGetter);
        String joinField = SqlUtil.extractFieldName(joinFieldGetter);
        addJoinCondition(entityField, "=", joinField);
        isOrCondition = false;
        return this;
    }

    /**
     * OR 关联条件连接
     */
    public <F, JF> Join<T, J> orOn(SFunction<T, F> entityFieldGetter, SFunction<J, JF> joinFieldGetter) {
        String entityField = SqlUtil.extractFieldName(entityFieldGetter);
        String joinField = SqlUtil.extractFieldName(joinFieldGetter);
        addJoinCondition(entityField, "=", joinField);
        isOrCondition = true;
        return this;
    }

    // ===== 私有辅助方法 =====

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