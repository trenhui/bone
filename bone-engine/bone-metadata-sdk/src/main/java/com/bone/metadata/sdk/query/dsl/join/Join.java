package com.bone.metadata.sdk.query.dsl.join;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.support.function.SFunction;

/**
 * 关联查询构建器接口 - 提供类型安全的关联查询API
 */
public interface Join<T, J> {

    /**
     * 设置关联条件
     */
    <F, JF> FluentQuery<T> on(SFunction<T, F> entityFieldGetter, SFunction<J, JF> joinFieldGetter);

    // ===== 链式调用方法 =====
    <F> Condition<T, F> where(SFunction<T, F> fieldGetter);
    FluentQuery<T> orderBy(SFunction<T, ?> fieldGetter, boolean isAsc);
    FluentQuery<T> limit(int limit);
    FluentQuery<T> offset(int offset);
    FluentQuery<T> groupBy(SFunction<T, ?>... fieldGetters);
}