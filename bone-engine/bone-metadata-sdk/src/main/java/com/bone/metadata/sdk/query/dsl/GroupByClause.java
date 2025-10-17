package com.bone.metadata.sdk.query.dsl;

import java.util.Collection;
import java.lang.Comparable;
import java.util.List;

/**
 * GROUP BY子句接口，提供分组操作方法
 * @param <T> 实体类型
 */
public interface GroupByClause<T> {

    /**
     * 添加下一个分组字段
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> groupBy(FieldFunction<T, V> fieldFunction);

    /**
     * 添加HAVING条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return HavingClause实例
     */
    <V> HavingClause<T> having(FieldFunction<T, V> fieldFunction);

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
     * @return 当前分组子句实例
     */
    GroupByClause<T> limit(long limit);

    /**
     * 设置查询偏移量
     * @param offset 偏移量
     * @return 当前分组子句实例
     */
    GroupByClause<T> offset(long offset);

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
}