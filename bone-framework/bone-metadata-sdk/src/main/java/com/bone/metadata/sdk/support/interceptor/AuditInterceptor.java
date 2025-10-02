package com.bone.metadata.sdk.support.interceptor;

import com.bone.metadata.sdk.domain.model.AuditLog;
import com.bone.metadata.sdk.support.audit.AuditService;
import com.bone.metadata.sdk.support.context.RequestContextHolder;
import com.bone.metadata.sdk.support.security.service.MetaPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

@Aspect
@Order(1)
@Slf4j
public class AuditInterceptor {

    private final AuditService auditService;
    private final MetaPermissionService permissionService;

    public AuditInterceptor(AuditService auditService, MetaPermissionService permissionService) {
        this.auditService = auditService;
        this.permissionService = permissionService;
    }

    @Around("@annotation(com.bone.metadata.sdk.domain.annotation.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 生成审计上下文
        String traceId = UUID.randomUUID().toString();
        RequestContextHolder.setTraceId(traceId);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String operation = signature.getMethod().getAnnotation(com.bone.metadata.sdk.domain.annotation.Auditable.class).value();

        // 2. 获取执行主体
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = (authentication != null) ? authentication.getName() : "anonymous";

        // 3. 记录审计开始
        Instant startTime = Instant.now();
        auditService.log(AuditLog.builder()
                .traceId(traceId)
                .principal(principal)
                .operation(operation)
                .status("STARTED")
                .timestamp(startTime)
                .build());

        // 4. 执行目标方法
        Object result;
        try {
            result = joinPoint.proceed();
            auditService.log(AuditLog.builder()
                    .traceId(traceId)
                    .principal(principal)
                    .operation(operation)
                    .status("SUCCESS")
                    .timestamp(Instant.now())
                    .build());
            return result;
        } catch (Exception e) {
            auditService.log(AuditLog.builder()
                    .traceId(traceId)
                    .principal(principal)
                    .operation(operation)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .timestamp(Instant.now())
                    .build());
            throw e;
        } finally {
            RequestContextHolder.clear();
        }
    }

    // 权限审计增强（可选）
    public void auditPermission(String resource, String action) {
        if (!permissionService.hasPermission(resource, action)) {
            auditService.log(AuditLog.builder()
                    .principal(SecurityContextHolder.getContext().getAuthentication().getName())
                    .operation("PERMISSION_DENIED")
                    .resource(resource)
                    .action(action)
                    .status("DENIED")
                    .timestamp(Instant.now())
                    .build());
        }
    }
}