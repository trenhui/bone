package com.bone.metadata.sdk.query.dsl.condition;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;

import java.util.Collection;

/**
 * 条件构建器接口 - 提供类型安全的条件查询API
 */
public interface Condition<T, V> {
    // 基础比较操作
    QueryBuilder.FluentQuery<T> eq(V value);
    QueryBuilder.FluentQuery<T> neq(V value);
    QueryBuilder.FluentQuery<T> gt(V value);
    QueryBuilder.FluentQuery<T> gte(V value);
    QueryBuilder.FluentQuery<T> lt(V value);
    QueryBuilder.FluentQuery<T> lte(V value);

    // 字符串操作
    QueryBuilder.FluentQuery<T> like(String value);
    QueryBuilder.FluentQuery<T> notLike(String value);
    QueryBuilder.FluentQuery<T> startsWith(String value);
    QueryBuilder.FluentQuery<T> endsWith(String value);
    QueryBuilder.FluentQuery<T> contains(String value);

    // 集合操作
    QueryBuilder.FluentQuery<T> in(Collection<V> values);
    QueryBuilder.FluentQuery<T> notIn(Collection<V> values);

    // 空值操作
    QueryBuilder.FluentQuery<T> isNull();
    QueryBuilder.FluentQuery<T> isNotNull();

    // 范围操作
    QueryBuilder.FluentQuery<T> between(V start, V end);

    // 逻辑组合
    <NV> Condition<T, NV> and(Class<T> entityClass, String fieldName);
    <NV> Condition<T, NV> or(Class<T> entityClass, String fieldName);
}