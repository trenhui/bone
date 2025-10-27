package com.bone.metadata.sdk.query.dsl;

/**
 * HAVING条件子句接口，提供分组后的条件操作方法
 * 通过继承CommonCondition消除方法重复定义
 * @param <T> 实体类型
 */
public interface HavingClause<T> extends CommonCondition<T, GroupByClause<T>> {

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