package com.bone.metadata.engine.security;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 默认审计服务实现 负责记录系统中的安全相关操作日志 */
@Service
public class DefaultAuditService implements AuditService {

  private static final Logger logger = LoggerFactory.getLogger(DefaultAuditService.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // 使用线程池异步处理审计日志
  private final ExecutorService auditExecutor = Executors.newFixedThreadPool(2);

  @Override
  public void logAccessDenied(
      CustomAuthentication authentication,
      String resourceType,
      String resourceId,
      String operation) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[访问拒绝] 时间: %s, 用户: %s, 资源类型: %s, 资源ID: %s, 操作: %s",
            getCurrentTime(), username, resourceType, resourceId, operation);
    logger.warn(message);

    // 异步保存到数据库
    auditExecutor.submit(
        () ->
            saveAuditLog(username, "ACCESS_DENIED", resourceType, resourceId, operation, message));
  }

  @Override
  public void logFieldAccess(
      CustomAuthentication authentication,
      String entityName,
      String fieldName,
      String operation,
      boolean isMasked) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[字段访问] 时间: %s, 用户: %s, 实体: %s, 字段: %s, 操作: %s, 脱敏: %s",
            getCurrentTime(), username, entityName, fieldName, operation, isMasked ? "是" : "否");
    logger.info(message);

    // 异步保存到数据库
    auditExecutor.submit(
        () -> saveAuditLog(username, "FIELD_ACCESS", entityName, fieldName, operation, message));
  }

  @Override
  public void logRecordAccess(
      CustomAuthentication authentication, String entityName, String recordId, String operation) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[记录访问] 时间: %s, 用户: %s, 实体: %s, 记录ID: %s, 操作: %s",
            getCurrentTime(), username, entityName, recordId, operation);
    logger.info(message);

    // 异步保存到数据库
    auditExecutor.submit(
        () -> saveAuditLog(username, "RECORD_ACCESS", entityName, recordId, operation, message));
  }

  @Override
  public void logSensitiveOperation(
      CustomAuthentication authentication, String operation, String details) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[敏感操作] 时间: %s, 用户: %s, 操作: %s, 详情: %s",
            getCurrentTime(), username, operation, details);
    logger.warn(message);

    // 异步保存到数据库
    auditExecutor.submit(
        () -> saveAuditLog(username, "SENSITIVE_OPERATION", "SYSTEM", "", operation, message));
  }

  @Override
  public void logBatchOperation(
      CustomAuthentication authentication,
      String entityName,
      String operation,
      int recordCount,
      int successCount) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[批量操作] 时间: %s, 用户: %s, 实体: %s, 操作: %s, 总数: %d, 成功: %d, 失败: %d",
            getCurrentTime(),
            username,
            entityName,
            operation,
            recordCount,
            successCount,
            recordCount - successCount);
    logger.info(message);

    // 异步保存到数据库
    String details =
        String.format(
            "总数: %d, 成功: %d, 失败: %d", recordCount, successCount, recordCount - successCount);
    auditExecutor.submit(
        () -> saveAuditLog(username, "BATCH_OPERATION", entityName, "", operation, message));
  }

  @Override
  public void logAuthentication(String username, String ipAddress, boolean success, String reason) {
    String message =
        String.format(
            "[认证] 时间: %s, 用户: %s, IP: %s, 结果: %s, 原因: %s",
            getCurrentTime(), username, ipAddress, success ? "成功" : "失败", reason);
    if (success) {
      logger.info(message);
    } else {
      logger.warn(message);
    }

    // 异步保存到数据库
    auditExecutor.submit(
        () ->
            saveAuditLog(
                username,
                "AUTHENTICATION",
                "SYSTEM",
                ipAddress,
                success ? "SUCCESS" : "FAILURE",
                message));
  }

  @Override
  public void logAuthorization(
      CustomAuthentication authentication, String resource, String operation, boolean success) {
    String username = authentication != null ? authentication.getName() : "anonymous";
    String message =
        String.format(
            "[授权] 时间: %s, 用户: %s, 资源: %s, 操作: %s, 结果: %s",
            getCurrentTime(), username, resource, operation, success ? "成功" : "失败");
    if (success) {
      logger.debug(message);
    } else {
      logger.warn(message);
    }

    // 异步保存到数据库
    auditExecutor.submit(
        () -> saveAuditLog(username, "AUTHORIZATION", resource, "", operation, message));
  }

  /** 获取当前时间字符串 */
  private String getCurrentTime() {
    return LocalDateTime.now().format(DATE_TIME_FORMATTER);
  }

  /** 保存审计日志到数据库 实际项目中应该保存到数据库中 */
  private void saveAuditLog(
      String username,
      String logType,
      String resourceType,
      String resourceId,
      String operation,
      String message) {
    try {
      // 这里应该是保存到数据库的逻辑
      // 暂时只是打印日志
      logger.debug(
          "保存审计日志: {}, {}, {}, {}, {}, {}",
          username,
          logType,
          resourceType,
          resourceId,
          operation,
          message);

      // 模拟数据库操作延迟
      Thread.sleep(10);
    } catch (Exception e) {
      // 审计日志保存失败不影响主流程
      logger.error("保存审计日志失败", e);
    }
  }

  /** 关闭线程池 在应用关闭时调用 */
  public void shutdown() {
    auditExecutor.shutdown();
  }
}
