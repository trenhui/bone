package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 抽象查询上下文基类，为各种查询上下文提供共同的基础结构和功能 减少重复代码并确保上下文对象设计的一致性 */
public abstract class AbstractQueryContext {
  protected final TableMetadata table;
  protected final Criteria<?> criteria;
  protected final AllocationContext extContext;

  /**
   * 创建一个带有表元数据和外部上下文的抽象查询上下文
   *
   * @param table 表元数据，不能为空
   * @param criteria 查询条件，可能为null
   * @param extContext 外部上下文，可能为null
   */
  protected AbstractQueryContext(
      TableMetadata table, Criteria<?> criteria, AllocationContext extContext) {
    this.table = Objects.requireNonNull(table, "Table metadata cannot be null");
    this.criteria = criteria;
    this.extContext = extContext;
  }

  /**
   * 获取表元数据
   *
   * @return 表元数据
   */
  public TableMetadata getTable() {
    return table;
  }

  /**
   * 获取查询条件
   *
   * @return 查询条件
   */
  public Criteria<?> getCriteria() {
    return criteria;
  }

  /**
   * 获取外部上下文
   *
   * @return 外部上下文
   */
  public AllocationContext getExtContext() {
    return extContext;
  }

  /**
   * 安全地获取列表，如果为null则返回空列表 用于避免空指针异常
   *
   * @param list 原始列表
   * @param <T> 列表元素类型
   * @return 非空列表
   */
  protected <T> List<T> safeList(List<T> list) {
    return list != null ? list : Collections.emptyList();
  }
}
