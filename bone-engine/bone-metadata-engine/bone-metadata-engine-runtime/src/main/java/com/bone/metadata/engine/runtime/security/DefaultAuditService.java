package com.bone.metadata.engine.runtime.security;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 默认审计服务实现 负责记录系统中的安全相关操作日志。
 *
 * <p><b>当前能力边界（务必知悉）</b>：安全事件（越权访问 / 字段脱敏 / 敏感操作 / 认证授权结果） 以 WARN / INFO
 * 级结构化日志同步输出，即「日志态审计」——应用日志的留存与轮转策略就是它的留存策略。
 *
 * <p><b>尚未接入持久化审计</b>：本类不落库。等保 / SOX 类场景需要的「不可篡改、可追溯」审计表 需要独立的表设计（DDL）与宿主注入，
 * 属于架构决策，不在引擎层单方面引入；接入前不要把本类当作数据库审计。
 */
@Service
public class DefaultAuditService implements AuditService {

  private static final Logger logger = LoggerFactory.getLogger(DefaultAuditService.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
  }

  /** 获取当前时间字符串 */
  private String getCurrentTime() {
    return LocalDateTime.now().format(DATE_TIME_FORMATTER);
  }
}
