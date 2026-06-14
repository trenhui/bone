package com.bone.metadata.sdk.domain.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 异常处理工具类 提供统一的异常处理、日志记录功能 */
public final class ExceptionUtils {
  private static final Logger logger = LoggerFactory.getLogger(ExceptionUtils.class);
  private static final ExceptionHandler EXCEPTION_HANDLER = ExceptionHandler.getInstance();

  private ExceptionUtils() {
    // 私有构造函数，避免实例化
  }

  /**
   * 处理异常，使用统一的异常处理器
   *
   * @param e 原始异常
   * @return 处理后的SDK异常
   */
  public static SDKException handleException(Throwable e) {
    return EXCEPTION_HANDLER.handleException(e);
  }

  /**
   * 处理异常并包装为运行时异常
   *
   * @param e 原始异常
   * @param message 错误消息
   * @return 运行时异常
   */
  public static RuntimeException handleAndWrapException(Throwable e, String message) {
    RuntimeException exception = new RuntimeException(message, e);
    EXCEPTION_HANDLER.handleException(exception);
    return exception;
  }

  /**
   * 创建并处理查询构建异常
   *
   * @param message 错误消息
   * @param entityType 实体类型
   * @param phase 构建阶段
   * @return 查询构建异常
   */
  public static QueryBuildException createQueryBuildException(
      String message, Class<?> entityType, String phase) {
    return EXCEPTION_HANDLER.createQueryBuildException(message, entityType, phase);
  }

  /**
   * 创建并处理查询执行异常
   *
   * @param message 错误消息
   * @param sql SQL语句
   * @param cause 根本原因
   * @return 查询执行异常
   */
  public static QueryExecutionException createQueryExecutionException(
      String message, String sql, Throwable cause) {
    return EXCEPTION_HANDLER.createQueryExecutionException(message, sql, cause);
  }
}
