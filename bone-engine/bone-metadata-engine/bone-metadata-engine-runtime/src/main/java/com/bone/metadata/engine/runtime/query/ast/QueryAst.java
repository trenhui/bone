package com.bone.metadata.engine.runtime.query.ast;

import java.util.ArrayList;
import java.util.List;

/** 查询抽象语法树，用于表示解析后的SmartQL查询 */
public class QueryAst {

  private String objectName;
  private List<String> selectFields;
  private List<String> parameters;
  private String whereClause;
  private List<String> orderBy;
  private boolean containsDynamicFunctions;
  private boolean hasComplexConditions;

  public QueryAst() {
    this.selectFields = new ArrayList<>();
    this.parameters = new ArrayList<>();
    this.orderBy = new ArrayList<>();
  }

  /** 获取实体名称 */
  public String getObjectName() {
    return objectName;
  }

  public void setObjectName(String objectName) {
    this.objectName = objectName;
  }

  /** 获取选择的字段列表 */
  public List<String> getSelectFields() {
    return selectFields;
  }

  public void setSelectFields(List<String> selectFields) {
    this.selectFields = selectFields;
  }

  public void addSelectField(String field) {
    this.selectFields.add(field);
  }

  /** 获取参数列表 */
  public List<String> getParameters() {
    return parameters;
  }

  public void setParameters(List<String> parameters) {
    this.parameters = parameters;
  }

  public void addParameter(String parameter) {
    this.parameters.add(parameter);
  }

  /** 获取WHERE子句 */
  public String getWhereClause() {
    return whereClause;
  }

  public void setWhereClause(String whereClause) {
    this.whereClause = whereClause;
  }

  /** 获取排序字段列表 */
  public List<String> getOrderBy() {
    return orderBy;
  }

  public void setOrderBy(List<String> orderBy) {
    this.orderBy = orderBy;
  }

  public void addOrderBy(String orderBy) {
    this.orderBy.add(orderBy);
  }

  /** 检查是否包含动态函数 */
  public boolean containsDynamicFunctions() {
    return containsDynamicFunctions;
  }

  public void setContainsDynamicFunctions(boolean containsDynamicFunctions) {
    this.containsDynamicFunctions = containsDynamicFunctions;
  }

  /** 检查是否有复杂条件 */
  public boolean hasComplexConditions() {
    return hasComplexConditions;
  }

  public void setHasComplexConditions(boolean hasComplexConditions) {
    this.hasComplexConditions = hasComplexConditions;
  }
}
