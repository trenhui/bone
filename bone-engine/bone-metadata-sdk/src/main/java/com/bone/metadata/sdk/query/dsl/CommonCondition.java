package com.bone.metadata.sdk.query.dsl;

import java.util.Collection;
import java.lang.Comparable;

/**
 * 通用条件接口，定义所有条件操作方法
 * 可用于WHERE和HAVING子句的共享功能
 * @param <T> 实体类型
 * @param <R> 返回类型，用于链式调用
 */
public interface CommonCondition<T, R> {

    /**
     * 等于条件
     * @param value 比较值
     * @return 链式调用结果
     */
    R eq(Object value);

    /**
     * LIKE条件
     * @param value 模糊匹配值
     * @return 链式调用结果
     */
    R like(String value);

    /**
     * IN条件
     * @param values 集合值
     * @return 链式调用结果
     */
    R in(Collection<?> values);

    /**
     * 大于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return 链式调用结果
     */
    <V> R gt(Comparable<V> value);

    /**
     * 小于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return 链式调用结果
     */
    <V> R lt(Comparable<V> value);

    /**
     * 大于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return 链式调用结果
     */
    <V> R gte(Comparable<V> value);

    /**
     * 小于等于条件
     * @param value 比较值
     * @param <V> 值类型
     * @return 链式调用结果
     */
    <V> R lte(Comparable<V> value);

    /**
     * 不等于条件
     * @param value 比较值
     * @return 链式调用结果
     */
    R ne(Object value);

    /**
     * IS NULL条件
     * @return 链式调用结果
     */
    R isNull();

    /**
     * IS NOT NULL条件
     * @return 链式调用结果
     */
    R isNotNull();
}