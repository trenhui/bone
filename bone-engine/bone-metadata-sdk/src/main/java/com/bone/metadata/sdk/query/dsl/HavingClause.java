package com.bone.metadata.sdk.query.dsl;

import java.util.Collection;
import java.lang.Comparable;
import java.util.List;

/**
 * HAVING条件子句接口，提供分组后的条件操作方法
 * @param <T> 实体类型
 */
public interface HavingClause<T> {

    /**
     * 等于条件
     * @param value 比较值
     * @return GroupByClause实例
     */
    GroupByClause<T> eq(Object value);

    /**
     * LIKE条件
     * @param value 模糊匹配值
     * @return GroupByClause实例
     */
    GroupByClause<T> like(String value);

    /**
     * IN条件
     * @param values 集合值
     * @return GroupByClause实例
     */
    GroupByClause<T> in(Collection<?> values);

    /**
     * 大于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> gt(Comparable<V> value);

    /**
     * 小于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> lt(Comparable<V> value);

    /**
     * 大于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> gte(Comparable<V> value);

    /**
     * 小于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> lte(Comparable<V> value);

    /**
     * 不等于条件
     * @param value 比较值
     * @return GroupByClause实例
     */
    GroupByClause<T> ne(Object value);

    /**
     * IS NULL条件
     * @return GroupByClause实例
     */
    GroupByClause<T> isNull();

    /**
     * IS NOT NULL条件
     * @return GroupByClause实例
     */
    GroupByClause<T> isNotNull();

    /**
     * 连接AND条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return HavingClause实例
     */
    <V> HavingClause<T> and(FieldFunction<T, V> fieldFunction);

    /**
     * 连接OR条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return HavingClause实例
     */
    <V> HavingClause<T> or(FieldFunction<T, V> fieldFunction);
}