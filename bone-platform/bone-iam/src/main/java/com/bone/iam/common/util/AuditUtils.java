package com.bone.iam.common.util;

import com.bone.iam.application.service.AuditService;
import com.bone.iam.domain.audit.vo.OperationType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuditUtils {
  public static void log(
      AuditService auditService,
      Long userId,
      OperationType operation,
      String resourceId,
      String resourceType,
      String result,
      Integer duration) {
    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    String ip = request.getRemoteAddr();
    String userAgent = request.getHeader("User-Agent");
    String parameters = request.getQueryString();

    // 脱敏处理
    parameters = maskSensitiveInfo(parameters);

    auditService.log(
        1L,
        userId,
        operation,
        resourceId,
        resourceType,
        ip,
        userAgent,
        parameters,
        result,
        duration);
  }

  private static String maskSensitiveInfo(String parameters) {
    // 实现敏感信息脱敏逻辑
    if (parameters == null) {
      return null;
    }
    // 简单的脱敏处理，实际项目中可能需要更复杂的逻辑
    return parameters
        .replaceAll("password=([^&]+)", "password=***")
        .replaceAll("token=([^&]+)", "token=***");
  }
}
