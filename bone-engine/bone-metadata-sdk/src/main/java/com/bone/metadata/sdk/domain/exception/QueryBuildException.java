package com.bone.metadata.sdk.domain.exception;

import java.lang.reflect.Type;
import java.util.Map;

/** 查询构建异常类 当SQL查询构建过程中出现错误时抛出 */
public class QueryBuildException extends SDKException {
  private static final long serialVersionUID = 1L;

  // 生成的SQL语句
  private String sql;
  // SQL参数
  private Map<String, Object> parameters;
  // 相关的实体类型
  private Type entityType;
  // 构建阶段信息
  private String buildPhase;

  /**
   * 创建查询构建异常
   *
   * @param message 异常消息
   */
  public QueryBuildException(String message) {
    super("QUERY_BUILD_ERROR", message);
  }

  /**
   * 创建查询构建异常
   *
   * @param message 异常消息
   * @param cause 根本原因
   */
  public QueryBuildException(String message, Throwable cause) {
    super("QUERY_BUILD_ERROR", message, cause);
  }

  /**
   * 设置生成的SQL语句
   *
   * @param sql SQL语句
   * @return 当前异常实例
   */
  public QueryBuildException setSql(String sql) {
    this.sql = sql;
    addContext("sql", sql);
    return this;
  }

  /**
   * 获取生成的SQL语句
   *
   * @return SQL语句
   */
  public String getSql() {
    return sql;
  }

  /**
   * 设置SQL参数
   *
   * @param parameters 参数映射
   * @return 当前异常实例
   */
  public QueryBuildException setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
    addContext("parameters", parameters);
    return this;
  }

  /**
   * 获取SQL参数
   *
   * @return 参数映射
   */
  public Map<String, Object> getParameters() {
    return parameters;
  }

  /**
   * 设置实体类型
   *
   * @param entityType 实体类型
   * @return 当前异常实例
   */
  public QueryBuildException setEntityType(Type entityType) {
    this.entityType = entityType;
    if (entityType != null) {
      addContext("entityType", entityType.getTypeName());
    }
    return this;
  }

  /**
   * 获取实体类型
   *
   * @return 实体类型
   */
  public Type getEntityType() {
    return entityType;
  }

  /**
   * 设置构建阶段
   *
   * @param phase 构建阶段（如：WHERE、JOIN、ORDER_BY等）
   * @return 当前异常实例
   */
  public QueryBuildException setBuildPhase(String phase) {
    this.buildPhase = phase;
    addContext("buildPhase", phase);
    return this;
  }

  /**
   * 获取构建阶段
   *
   * @return 构建阶段
   */
  public String getBuildPhase() {
    return buildPhase;
  }
}
