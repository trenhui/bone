package com.bone.metadata.sdk.query.dsl;

/**
 * WHERE条件子句接口，提供条件操作方法
 * 通过继承CommonCondition消除方法重复定义
 * @param <T> 实体类型
 */
public interface WhereClause<T> extends CommonCondition<T, ConditionClause<T>> {
    // 所有条件方法已在父接口中定义
}