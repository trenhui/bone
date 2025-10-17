package com.bone.metadata.sdk.query.dsl;

/**
 * 排序子句接口，提供排序操作方法
 * @param <T> 实体类型
 */
public interface OrderByClause<T> {

    /**
     * 设置升序排序
     * @return EntitySqlBuilder实例
     */
    EntitySqlBuilder<T> asc();

    /**
     * 设置降序排序
     * @return EntitySqlBuilder实例
     */
    EntitySqlBuilder<T> desc();

    /**
     * 添加下一个排序字段
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return OrderByClause实例
     */
    <V> OrderByClause<T> thenBy(FieldFunction<T, V> fieldFunction);

    /**
     * 添加下一个升序排序字段
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return OrderByClause实例
     */
    <V> OrderByClause<T> thenAsc(FieldFunction<T, V> fieldFunction);

    /**
     * 添加下一个降序排序字段
     * @param fieldFunction 字段方法引用
     * @param <V> 字段值类型
     * @return OrderByClause实例
     */
    <V> OrderByClause<T> thenDesc(FieldFunction<T, V> fieldFunction);
}