package com.bone.metadata.engine.domain.exception;

/** 查询执行异常，表示在执行SmartQL查询过程中发生的错误 */
public class QueryExecutionException extends RuntimeException {

  /**
   * 构造函数
   *
   * @param message 异常消息
   */
  public QueryExecutionException(String message) {
    super(message);
  }

  /**
   * 构造函数
   *
   * @param message 异常消息
   * @param cause 异常原因
   */
  public QueryExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
