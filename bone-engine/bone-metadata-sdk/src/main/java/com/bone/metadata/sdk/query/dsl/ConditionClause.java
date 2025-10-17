package com.bone.metadata.sdk.query.dsl;

import java.util.List;

/**
 * 条件子句接口，提供条件连接和结果执行方法
 * @param <T> 实体类型
 */
public interface ConditionClause<T> {

    /**
     * 连接AND条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return WhereClause实例
     */
    <V> WhereClause<T> and(FieldFunction<T, V> fieldFunction);

    /**
     * 连接OR条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return WhereClause实例
     */
    <V> WhereClause<T> or(FieldFunction<T, V> fieldFunction);

    /**
     * 执行查询并返回结果列表
     * @return 查询结果列表
     */
    List<T> list();

    /**
     * 执行查询并返回单条结果
     * @return 查询结果
     * @throws com.bone.metadata.sdk.domain.exception.MultipleResultsException 当查询结果多于一条时抛出
     */
    T single();

    /**
     * 执行计数查询
     * @return 记录总数
     */
    long count();

    /**
     * 添加ORDER BY排序
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return OrderByClause实例
     */
    <V> OrderByClause<T> orderBy(FieldFunction<T, V> fieldFunction);

    /**
     * 设置查询条数限制
     * @param limit 限制条数
     * @return 当前条件子句实例
     */
    ConditionClause<T> limit(long limit);

    /**
     * 设置查询偏移量
     * @param offset 偏移量
     * @return 当前条件子句实例
     */
    ConditionClause<T> offset(long offset);
}