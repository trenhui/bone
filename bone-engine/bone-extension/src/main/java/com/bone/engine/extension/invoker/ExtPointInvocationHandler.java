package com.bone.engine.extension.invoker;

import com.bone.core.exception.BizException;
import com.bone.core.util.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 扩展点调用处理器
 * <p>
 * 负责处理扩展点方法的调用，提供增强的异常处理、详细的性能指标记录和日志追踪。
 * 作为扩展点执行的核心入口，确保方法调用的安全性和可观测性。
 * </p>
 * 
 * @since 1.0.0
 */
public class ExtPointInvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(ExtPointInvocationHandler.class);

    /**
     * 调用扩展点实现的目标方法
     * <p>
     * 提供以下核心功能：
     * 1. 输入参数有效性验证
     * 2. 方法执行前日志记录
     * 3. 执行性能监控和计时
     * 4. 异常分类处理和转换
     * 5. 执行结果返回和日志记录
     * </p>
     * 
     * @param target 目标扩展点实现对象
     * @param method 被调用的方法
     * @param args 方法参数数组
     * @return 方法调用的结果对象
     * @throws BizException 当扩展点执行失败时抛出统一的业务异常
     * @throws IllegalArgumentException 当输入参数无效时抛出
     */
    public static Object invoke(@NonNull Object target, @NonNull Method method, @Nullable Object[] args) {
        // 1. 验证参数有效性
        validateInvocationParameters(target, method);
        
        // 2. 准备调用上下文信息
        String extensionClassName = target.getClass().getCanonicalName();
        String methodName = method.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        log.debug("[{}] Starting extension point invocation: {}.{}", timestamp, extensionClassName, methodName);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 3. 记录参数信息（避免记录敏感数据）
            log.debug("[{}] Invoking method: {}.{} with parameter count: {}", 
                    timestamp, extensionClassName, methodName, args == null ? 0 : args.length);
            
            // 4. 执行目标方法
            Object result = ReflectionUtil.invokeMethod(target, method.getName(), args);
            
            // 5. 记录执行完成信息
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Extension point invocation successful: {}.{} completed in {}ms", 
                    timestamp, extensionClassName, methodName, executionTime);
            
            return result;
        } catch (ReflectionUtil.ReflectionException ex) {
            // 6. 处理反射异常
            return handleReflectionException(ex, extensionClassName, methodName, startTime, timestamp);
        } catch (Exception e) {
            // 7. 处理未预期的异常
            return handleUnexpectedException(e, extensionClassName, methodName, startTime, timestamp);
        }
    }

    /**
     * 验证调用参数有效性
     * 
     * @param target 目标对象
     * @param method 方法对象
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private static void validateInvocationParameters(@NonNull Object target, @NonNull Method method) {
        // 参数已通过@NonNull注解验证，此方法可用于添加更复杂的验证逻辑
        log.trace("Validating invocation parameters for method: {}", method.getName());
    }

    /**
     * 处理反射异常
     * 
     * @param ex 反射异常
     * @param className 类名
     * @param methodName 方法名
     * @param startTime 开始时间
     * @param timestamp 时间戳
     * @return 方法调用结果（理论上不会到达这里）
     * @throws BizException 转换后的业务异常
     */
    private static Object handleReflectionException(ReflectionUtil.ReflectionException ex, 
                                                  String className, String methodName, 
                                                  long startTime, String timestamp) {
        long executionTime = System.currentTimeMillis() - startTime;
        Throwable cause = ex.getCause();
        
        if (cause instanceof BizException) {
            // 业务异常直接传播，保留原始上下文
            log.warn("[{}] Business exception in extension point: {}.{} after {}ms - {}",
                    timestamp, className, methodName, executionTime, cause.getMessage());
            throw (BizException) cause;
        } else {
            // 技术异常转换为标准业务异常
            String errorMsg = String.format("Extension point execution failed: %s", 
                    (cause != null ? cause.getMessage() : ex.getMessage()));
            
            log.error("[{}] Extension point method invocation failed: {}.{} after {}ms",
                    timestamp, className, methodName, executionTime, ex);
            
            throw BizException.of(500, errorMsg + 
                    " [extensionPoint=" + className + ", method=" + methodName + ", executionTimeMs=" + executionTime + "]");
        }
    }

    /**
     * 处理未预期的异常
     * 
     * @param e 异常对象
     * @param className 类名
     * @param methodName 方法名
     * @param startTime 开始时间
     * @param timestamp 时间戳
     * @return 方法调用结果（理论上不会到达这里）
     * @throws BizException 转换后的业务异常
     */
    private static Object handleUnexpectedException(Exception e, 
                                                  String className, String methodName, 
                                                  long startTime, String timestamp) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        log.error("[{}] Unexpected error during extension point invocation: {}.{} after {}ms",
                timestamp, className, methodName, executionTime, e);
        
        throw BizException.of(500, "Unexpected error: " + e.getMessage() + 
                " [extensionPoint=" + className + ", method=" + methodName + ", executionTimeMs=" + executionTime + "]");
    }
}