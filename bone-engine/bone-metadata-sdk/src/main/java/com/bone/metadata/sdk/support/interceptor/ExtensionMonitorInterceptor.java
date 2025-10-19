package com.bone.metadata.sdk.support.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.concurrent.TimeUnit;

/**
 * 扩展字段操作监控切面。
 */
@Aspect
public class ExtensionMonitorInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ExtensionMonitorInterceptor.class);

    @Around("execution(* com.bone.metadata.sdk.extension..*(..))")
    public Object monitorExtensionOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long start = System.nanoTime();
        boolean success = false;

        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            long duration = System.nanoTime() - start;
            if (duration > TimeUnit.MILLISECONDS.toNanos(500)) {
                log.warn("Slow extension operation: {} took {}ms", method, TimeUnit.NANOSECONDS.toMillis(duration));
            }
        }
    }
}