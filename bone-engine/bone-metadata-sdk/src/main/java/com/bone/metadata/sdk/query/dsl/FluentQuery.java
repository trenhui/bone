package com.bone.metadata.sdk.query.dsl;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.query.dsl.join.Join;
import com.bone.metadata.sdk.support.function.SFunction;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 流畅查询接口 - 提供类型安全的链式调用查询API
 * 基于业界最佳实践，支持连续 and/or 链式调用
 */
public interface FluentQuery<T> {

    // ===== 条件查询 =====
    <F> Condition<T, F> where(SFunction<T, F> fieldGetter);

    FluentQuery<T> where(Consumer<WhereBuilder<T>> conditionBuilder);

    /**
     * AND 条件连接 - 返回Condition支持连续链式调用
     */
    <F> Condition<T, F> and(SFunction<T, F> fieldGetter);

    /**
     * OR 条件连接 - 返回Condition支持连续链式调用
     */
    <F> Condition<T, F> or(SFunction<T, F> fieldGetter);

    /**
     * AND 分组条件 - 支持括号嵌套
     */
    FluentQuery<T> and(Consumer<FluentQuery<T>> groupBuilder);

    /**
     * OR 分组条件 - 支持括号嵌套
     */
    FluentQuery<T> or(Consumer<FluentQuery<T>> groupBuilder);

    // ===== 排序 =====
    FluentQuery<T> orderBy(SFunction<T, ?> fieldGetter, boolean isAsc);

    FluentQuery<T> orderByAsc(SFunction<T, ?> fieldGetter);

    FluentQuery<T> orderByDesc(SFunction<T, ?> fieldGetter);

    // ===== 分页控制 =====
    FluentQuery<T> limit(int limit);

    FluentQuery<T> offset(int offset);

    // ===== 分组 =====
    FluentQuery<T> groupBy(Function<T, ?>... fieldGetters);

    <F> Condition<T, F> having(SFunction<T, F> fieldGetter);

    // ===== 关联查询 =====
    <J> Join<T, J> join(Class<J> joinClass, String joinAlias);

    <J> Join<T, J> leftJoin(Class<J> joinClass, String joinAlias);

    <J> Join<T, J> rightJoin(Class<J> joinClass, String joinAlias);

    <J> Join<T, J> fullJoin(Class<J> joinClass, String joinAlias);

    <J> FluentQuery<T> joinOn(Class<J> joinClass, String joinAlias,
                              Function<T, ?> entityFieldGetter, Function<J, ?> joinFieldGetter);

    <J> FluentQuery<T> leftJoinOn(Class<J> joinClass, String joinAlias,
                                  Function<T, ?> entityFieldGetter, Function<J, ?> joinFieldGetter);

    // ===== 查询执行 =====
    List<T> list();

    T single();

    Optional<T> singleOpt();

    Optional<T> first();

    long count();

    PageResult<T> page(int pageNum, int pageSize);

    boolean exists();

    // ===== 高级功能 =====
    <R> List<R> select(SFunction<T, R> fieldGetter, Class<R> resultType);

    <R> Optional<R> aggregate(String function, SFunction<T, ?> fieldGetter, Class<R> resultType);

    <R> List<R> map(Function<T, R> mapper);

    void forEach(Consumer<T> action);

    Stream<T> stream();

    // ===== 内部构建器接口 =====
    interface WhereBuilder<T> {
        <F> Condition<T, F> and(SFunction<T, F> fieldGetter);

        <F> Condition<T, F> or(SFunction<T, F> fieldGetter);
    }
}