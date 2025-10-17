package com.bone.metadata.sdk.query.dsl;

import java.util.Collection;
import java.lang.Comparable;

/**
 * WHERE条件子句接口，提供条件操作方法
 * @param <T> 实体类型
 */
public interface WhereClause<T> {

    /**
     * 等于条件
     * @param value 比较值
     * @return ConditionClause实例
     */
    ConditionClause<T> eq(Object value);

    /**
     * LIKE条件
     * @param value 模糊匹配值
     * @return ConditionClause实例
     */
    ConditionClause<T> like(String value);

    /**
     * IN条件
     * @param values 集合值
     * @return ConditionClause实例
     */
    ConditionClause<T> in(Collection<?> values);

    /**
     * 大于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return ConditionClause实例
     */
    <V> ConditionClause<T> gt(Comparable<V> value);

    /**
     * 小于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return ConditionClause实例
     */
    <V> ConditionClause<T> lt(Comparable<V> value);

    /**
     * 大于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return ConditionClause实例
     */
    <V> ConditionClause<T> gte(Comparable<V> value);

    /**
     * 小于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return ConditionClause实例
     */
    <V> ConditionClause<T> lte(Comparable<V> value);

    /**
     * 不等于条件
     * @param value 比较值
     * @return ConditionClause实例
     */
    ConditionClause<T> ne(Object value);

    /**
     * IS NULL条件
     * @return ConditionClause实例
     */
    ConditionClause<T> isNull();

    /**
     * IS NOT NULL条件
     * @return ConditionClause实例
     */
    ConditionClause<T> isNotNull();
}