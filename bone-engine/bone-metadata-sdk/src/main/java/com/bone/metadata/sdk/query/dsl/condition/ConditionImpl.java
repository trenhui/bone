package com.bone.metadata.sdk.query.dsl.condition;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.context.QueryContext;
import com.bone.metadata.sdk.support.function.SFunction;
import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.Collection;

/** 条件构建器实现类 - 纯Lambda版本 基于业界最佳实践，提供完全类型安全的API */
public class ConditionImpl<T, V> implements Condition<T, V> {

  private final QueryContext<T> queryContext;
  private final String fieldName;
  private final boolean isOrCondition;

  public ConditionImpl(QueryContext<T> queryContext, String fieldName) {
    this(queryContext, fieldName, false);
  }

  public ConditionImpl(QueryContext<T> queryContext, String fieldName, boolean isOrCondition) {
    this.queryContext = queryContext;
    this.fieldName = fieldName;
    this.isOrCondition = isOrCondition;
  }

  // ===== 比较操作实现 =====
  @Override
  public FluentQuery<T> eq(V value) {
    return addCondition("=", value);
  }

  @Override
  public FluentQuery<T> neq(V value) {
    return addCondition("<>", value);
  }

  @Override
  public FluentQuery<T> gt(V value) {
    return addCondition(">", value);
  }

  @Override
  public FluentQuery<T> gte(V value) {
    return addCondition(">=", value);
  }

  @Override
  public FluentQuery<T> lt(V value) {
    return addCondition("<", value);
  }

  @Override
  public FluentQuery<T> lte(V value) {
    return addCondition("<=", value);
  }

  // ===== 字符串操作实现 =====
  @Override
  public FluentQuery<T> like(String value) {
    return addCondition("LIKE", value);
  }

  @Override
  public FluentQuery<T> notLike(String value) {
    return addCondition("NOT LIKE", value);
  }

  @Override
  public FluentQuery<T> startsWith(String value) {
    return addCondition("LIKE", value + "%");
  }

  @Override
  public FluentQuery<T> endsWith(String value) {
    return addCondition("LIKE", "%" + value);
  }

  @Override
  public FluentQuery<T> contains(String value) {
    return addCondition("LIKE", "%" + value + "%");
  }

  // ===== 集合操作实现 =====
  @Override
  public FluentQuery<T> in(Collection<V> values) {
    return addCondition("IN", values);
  }

  @Override
  public FluentQuery<T> notIn(Collection<V> values) {
    return addCondition("NOT IN", values);
  }

  // ===== 空值操作实现 =====
  @Override
  public FluentQuery<T> isNull() {
    return addCondition("IS NULL", null);
  }

  @Override
  public FluentQuery<T> isNotNull() {
    return addCondition("IS NOT NULL", null);
  }

  // ===== 范围操作实现 =====
  @Override
  public FluentQuery<T> between(V start, V end) {
    QueryContext.Condition condition = createCondition("BETWEEN");
    condition.setValue1(start);
    condition.setValue2(end);
    queryContext.addCondition(condition);
    return queryContext.getFluentQuery();
  }

  // ===== 链式条件方法实现 =====

  /** AND 条件连接 - Lambda版本 */
  @Override
  public <NV> FluentQuery<T> and(SFunction<T, NV> fieldGetter) {
    String extractedFieldName = SqlUtil.extractFieldName(fieldGetter);
    // 设置逻辑操作符为AND，返回FluentQuery继续链式
    queryContext.setCurrentLogicalOperator("AND");
    return queryContext.getFluentQuery();
  }

  /** OR 条件连接 - Lambda版本 */
  @Override
  public <NV> FluentQuery<T> or(SFunction<T, NV> fieldGetter) {
    String extractedFieldName = SqlUtil.extractFieldName(fieldGetter);
    // 设置逻辑操作符为OR，返回FluentQuery继续链式
    queryContext.setCurrentLogicalOperator("OR");
    return queryContext.getFluentQuery();
  }

  // ===== 私有辅助方法 =====

  /** 添加条件到查询上下文 */
  private FluentQuery<T> addCondition(String operator, Object value) {
    QueryContext.Condition condition = createCondition(operator);
    condition.setValue1(value);
    queryContext.addCondition(condition);
    return queryContext.getFluentQuery();
  }

  /** 创建条件对象 */
  private QueryContext.Condition createCondition(String operator) {
    QueryContext.Condition condition = new QueryContext.Condition();
    condition.setFieldName(fieldName);
    condition.setOperator(operator);
    condition.setOr(isOrCondition);
    return condition;
  }

  /** 创建新的条件构建器 - 解决类型推断问题 */
  private <NV> Condition<T, NV> createCondition(String fieldName, boolean isOr) {
    return new ConditionImpl<>(queryContext, fieldName, isOr);
  }
}
