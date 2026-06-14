package com.bone.metadata.sdk.query.dsl.condition;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.support.function.SFunction;
import java.util.Collection;

/** 条件构建器接口 - 提供类型安全的条件查询API */
public interface Condition<T, V> {

  // ===== 比较操作 =====
  FluentQuery<T> eq(V value);

  FluentQuery<T> neq(V value);

  FluentQuery<T> gt(V value);

  FluentQuery<T> gte(V value);

  FluentQuery<T> lt(V value);

  FluentQuery<T> lte(V value);

  // ===== 字符串操作 =====
  FluentQuery<T> like(String value);

  FluentQuery<T> notLike(String value);

  FluentQuery<T> startsWith(String value);

  FluentQuery<T> endsWith(String value);

  FluentQuery<T> contains(String value);

  // ===== 集合操作 =====
  FluentQuery<T> in(Collection<V> values);

  FluentQuery<T> notIn(Collection<V> values);

  // ===== 空值操作 =====
  FluentQuery<T> isNull();

  FluentQuery<T> isNotNull();

  // ===== 范围操作 =====
  FluentQuery<T> between(V start, V end);

  // ===== 链式条件方法 =====
  <NV> FluentQuery<T> and(SFunction<T, NV> fieldGetter);

  <NV> FluentQuery<T> or(SFunction<T, NV> fieldGetter);
}
