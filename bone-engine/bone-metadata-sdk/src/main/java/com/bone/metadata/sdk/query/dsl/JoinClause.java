package com.bone.metadata.sdk.query.dsl;

/**
 * 表连接条件构建接口，用于指定连接条件
 * @param <T> 主实体类型
 * @param <J> 关联实体类型
 */
public interface JoinClause<T, J> {
    
    /**
     * 指定连接条件（主表字段 = 关联表字段）
     * @param mainFieldFunction 主表字段方法引用
     * @param joinFieldFunction 关联表字段方法引用
     * @param <V> 字段值类型
     * @return 主实体的EntitySqlBuilder实例，用于继续构建查询
     */
    <V> EntitySqlBuilder<T> on(FieldFunction<T, V> mainFieldFunction, FieldFunction<J, V> joinFieldFunction);
    
    /**
     * 添加AND连接条件
     * @param joinFieldFunction 关联表字段方法引用
     * @param <V> 字段值类型
     * @return WhereClause实例，用于指定条件值
     */
    <V> WhereClause<T> and(FieldFunction<J, V> joinFieldFunction);
    
    /**
     * 添加OR连接条件
     * @param joinFieldFunction 关联表字段方法引用
     * @param <V> 字段值类型
     * @return WhereClause实例，用于指定条件值
     */
    <V> WhereClause<T> or(FieldFunction<J, V> joinFieldFunction);
}