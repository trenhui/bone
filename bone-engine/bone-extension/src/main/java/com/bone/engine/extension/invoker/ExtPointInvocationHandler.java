package com.bone.engine.extension.invoker;

import com.bone.core.exception.BizException;
import com.bone.core.util.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 扩展点调用处理器
 * <p>
 * 负责处理扩展点方法的调用，提供增强的异常处理和详细的日志记录
 * 
 * @author renhui.trh 2023-10-2
 */
@Slf4j
public class ExtPointInvocationHandler {

    /**
     * 调用目标对象的指定方法
     * 
     * @param target 目标对象
     * @param method 被调用的方法
     * @param args 方法参数
     * @return 方法调用结果
     * @throws BizException 当扩展点执行失败时抛出
     */
    public static Object invoke(Object target, Method method, Object[] args) {
        // 验证参数有效性
        if (target == null) {
            throw BizException.of(500, "Extension target cannot be null");
        }
        if (method == null) {
            throw BizException.of(500, "Method cannot be null");
        }
        
        String className = target.getClass().getCanonicalName();
        String methodName = method.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        log.debug("[{}] Starting extension point invocation: {}.{}", timestamp, className, methodName);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 参数日志记录，避免敏感信息泄露
            log.debug("[{}] Invoking method: {}.{} with parameter count: {}", 
                    timestamp, className, methodName, args == null ? 0 : args.length);
            
            Object result = ReflectionUtil.invokeMethod(target, method.getName(), args);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录成功执行的详细信息
            log.info("[{}] Extension point invocation successful: {}.{} completed in {}ms", 
                    timestamp, className, methodName, executionTime);
            
            return result;
        } catch (ReflectionUtil.ReflectionException ex) {
            // 处理反射工具类抛出的异常
            long executionTime = System.currentTimeMillis() - startTime;
            Throwable cause = ex.getCause();
            
            if (cause instanceof BizException) {
                // 业务异常直接抛出，保持原始异常信息
                log.warn("[{}] Business exception in extension point: {}.{} after {}ms - {}",
                        timestamp, className, methodName, executionTime, cause.getMessage());
                throw (BizException) cause;
            } else {
                // 反射异常处理
                String errorMsg = String.format("Extension point execution failed: %s", 
                        (cause != null ? cause.getMessage() : ex.getMessage()));
                
                log.error("[{}] Extension point method invocation failed: {}.{} after {}ms",
                        timestamp, className, methodName, executionTime, ex);
                
                // 包装成业务异常
                throw BizException.of(500, errorMsg + 
                        " [extensionPoint=" + className + ", method=" + methodName + ", executionTimeMs=" + executionTime + "]");
            }
        } catch (Exception e) {
            // 处理其他未预期的异常
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.error("[{}] Unexpected error during extension point invocation: {}.{} after {}ms",
                    timestamp, className, methodName, executionTime, e);
            
            throw BizException.of(500, "Unexpected error: " + e.getMessage() + 
                    " [extensionPoint=" + className + ", method=" + methodName + ", executionTimeMs=" + executionTime + "]");
        }
    }
}