package com.bone.metadata.sdk.query.dsl.join;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.condition.Condition;

/**
 * 关联查询构建器接口 - 提供类型安全的关联查询API
 */
public interface Join<T, J> {
    // 关联条件设置
    <F, JF> QueryBuilder.FluentQuery<T> on(String entityField, String joinEntityField);
    <F, JF> QueryBuilder.FluentQuery<T> on(Condition<T, F> entityCondition, Condition<J, JF> joinCondition);
    <F, JF> QueryBuilder.FluentQuery<T> onWhere(String entityField, String operator, Object value);
    
    // 继续链式调用FluentQuery的方法
    <F> Condition<T, F> where(String fieldName);
    QueryBuilder.FluentQuery<T> orderBy(String fieldName, boolean isAsc);
    QueryBuilder.FluentQuery<T> limit(int limit);
    QueryBuilder.FluentQuery<T> offset(int offset);
    QueryBuilder.FluentQuery<T> groupBy(String... fieldNames);
    <JF> Join<T, J> andOn(String entityField, String joinEntityField);
    <JF> Join<T, J> orOn(String entityField, String joinEntityField);
}