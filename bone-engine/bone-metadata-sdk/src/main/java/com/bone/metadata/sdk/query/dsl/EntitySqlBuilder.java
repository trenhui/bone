package com.bone.metadata.sdk.query.dsl;

import java.util.List;

/**
 * 实体SQL构建器接口，提供流式API进行类型安全的SQL查询构建
 * @param <T> 实体类型
 */
public interface EntitySqlBuilder<T> {

    /**
     * 添加WHERE条件
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return WhereClause实例
     */
    <V> WhereClause<T> where(FieldFunction<T, V> fieldFunction);
    
    /**
     * 添加WHERE条件（支持关联表字段引用，如"role.code"）
     * @param fieldName 字段名
     * @return WhereClause实例
     */
    WhereClause<T> where(String fieldName);

    /**
     * 添加GROUP BY分组
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return GroupByClause实例
     */
    <V> GroupByClause<T> groupBy(FieldFunction<T, V> fieldFunction);

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
     * @return 当前构建器实例
     */
    EntitySqlBuilder<T> limit(long limit);

    /**
     * 设置查询偏移量
     * @param offset 偏移量
     * @return 当前构建器实例
     */
    EntitySqlBuilder<T> offset(long offset);

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
     * 添加INNER JOIN关联
     * @param joinEntityClass 关联实体类
     * @param <J> 关联实体类型
     * @return JoinClause实例
     */
    <J> JoinClause<T, J> join(Class<J> joinEntityClass);
    
    /**
     * 添加LEFT JOIN关联
     * @param joinEntityClass 关联实体类
     * @param <J> 关联实体类型
     * @return JoinClause实例
     */
    <J> JoinClause<T, J> leftJoin(Class<J> joinEntityClass);
    
    /**
     * 添加RIGHT JOIN关联
     * @param joinEntityClass 关联实体类
     * @param <J> 关联实体类型
     * @return JoinClause实例
     */
    <J> JoinClause<T, J> rightJoin(Class<J> joinEntityClass);
    
    /**
     * 添加FULL JOIN关联
     * @param joinEntityClass 关联实体类
     * @param <J> 关联实体类型
     * @return JoinClause实例
     */
    <J> JoinClause<T, J> fullJoin(Class<J> joinEntityClass);
}