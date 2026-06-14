package com.bone.metadata.sdk.domain.exception;

import java.util.Map;

/** 查询执行异常类 当SQL查询执行过程中出现错误时抛出 */
public class QueryExecutionException extends SDKException {
  private static final long serialVersionUID = 1L;

  // SQL语句
  private String sql;
  // 查询参数
  private Map<String, Object> parameters;

  /**
   * 创建查询执行异常
   *
   * @param message 异常消息
   */
  public QueryExecutionException(String message) {
    super("QUERY_EXECUTION_ERROR", message);
  }

  /**
   * 创建查询执行异常
   *
   * @param message 异常消息
   * @param cause 根本原因
   */
  public QueryExecutionException(String message, Throwable cause) {
    super("QUERY_EXECUTION_ERROR", message, cause);
  }

  /**
   * 设置SQL语句
   *
   * @param sql SQL语句
   * @return 当前异常实例
   */
  public QueryExecutionException setSql(String sql) {
    this.sql = sql;
    addContext("sql", sql);
    return this;
  }

  /**
   * 获取SQL语句
   *
   * @return SQL语句
   */
  public String getSql() {
    return sql;
  }

  /**
   * 设置查询参数
   *
   * @param parameters 参数映射
   * @return 当前异常实例
   */
  public QueryExecutionException setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
    addContext("parameters", parameters);
    return this;
  }

  /**
   * 获取查询参数
   *
   * @return 参数映射
   */
  public Map<String, Object> getParameters() {
    return parameters;
  }
}
