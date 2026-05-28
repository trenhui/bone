package com.bone.engine.extension.core.executor;

import com.bone.engine.extension.api.spi.ExtensionPointExecutor;
import com.bone.engine.extension.core.metrics.ExtensionMetricsCollector;
import com.bone.engine.extension.core.security.ExtensionPermissionManager;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.studio.StudioExecutionLogReporter;
import com.bone.core.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 默认扩展点执行器
 * <p>
 * 实现扩展点的安全执行，提供以下核心功能：
 * 1. 安全的方法执行环境
 * 2. 异常捕获和转换
 * 3. 执行性能监控
 * </p>
 * 
 * @since 1.0.0
 */
public class DefaultExtensionPointExecutor implements ExtensionPointExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultExtensionPointExecutor.class);

    @Autowired
    private ExtensionPermissionManager extensionPermissionManager;

    @Nullable
    private StudioExecutionLogReporter studioReporter;

    @Nullable
    private ExtensionExecutionGuard executionGuard;

    @Nullable
    private ExtensionMetricsCollector metricsCollector;

    public void setStudioReporter(@Nullable StudioExecutionLogReporter studioReporter) {
        this.studioReporter = studioReporter;
    }

    public void setExecutionGuard(@Nullable ExtensionExecutionGuard executionGuard) {
        this.executionGuard = executionGuard;
    }

    public void setMetricsCollector(@Nullable ExtensionMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * 执行扩展点实现的方法
     * 
     * @param implementation 扩展实现对象
     * @param context 业务上下文
     * @param method 方法对象
     * @param args 方法参数
     * @param <T> 返回类型泛型
     * @return 方法执行结果
     * @throws Throwable 执行过程中的异常
     */
    @Override
    public <T> T execute(Object implementation, BizContext<?> context, Method method, Object[] args) throws Throwable {
        // 1. 验证参数有效性
        validateExecutionParameters(implementation, context, method);

        // 2. 准备执行上下文信息
        String extensionClassName = implementation.getClass().getCanonicalName();
        String methodName = method.getName();

        log.debug("Starting extension point execution: {}.{}", extensionClassName, methodName);

        long startTime = System.currentTimeMillis();

        try {
            // 3. 检查权限
            if (extensionPermissionManager != null && !extensionPermissionManager.checkPermission(implementation, method)) {
                String errorMsg = String.format("Extension point %s does not have permission to call method %s",
                        implementation.getClass().getCanonicalName(), method.getName());
                log.error(errorMsg);
                throw new SecurityException(errorMsg);
            }

            String bulkheadKey = implementation.getClass().getSimpleName();
            String extPoint = resolveExtPointName(implementation);

            // 4. 执行扩展点方法（可选舱壁 + 超时）
            T result =
                    executionGuard != null
                            ? executionGuard.execute(
                                    () -> invokeForGuard(implementation, method, args), bulkheadKey)
                            : executeMethod(implementation, method, args);

            // 5. 记录执行完成信息
            long executionTime = System.currentTimeMillis() - startTime;
            recordMetrics(extPoint, bulkheadKey, "success", executionTime);
            log.info("Extension point execution successful: {}.{} completed in {}ms", 
                    extensionClassName, methodName, executionTime);
            reportToStudio(extensionClassName, methodName, executionTime, true, null);

            return result;
        } catch (SecurityException ex) {
            // 5. 处理权限异常
            long executionTime = System.currentTimeMillis() - startTime;
            recordMetrics(
                    resolveExtPointName(implementation),
                    implementation.getClass().getSimpleName(),
                    "error",
                    executionTime);
            handleSecurityException(ex, extensionClassName, methodName, startTime);
            reportToStudio(extensionClassName, methodName, executionTime, false, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            // 6. 处理其他异常
            long executionTime = System.currentTimeMillis() - startTime;
            recordMetrics(
                    resolveExtPointName(implementation),
                    implementation.getClass().getSimpleName(),
                    "error",
                    executionTime);
            handleExecutionException(e, extensionClassName, methodName, startTime);
            reportToStudio(extensionClassName, methodName, executionTime, false, e.getMessage());
            throw e;
        }
    }

    /**
     * 验证执行参数有效性
     * 
     * @param implementation 扩展实现对象
     * @param context 业务上下文
     * @param method 方法对象
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void validateExecutionParameters(Object implementation, BizContext<?> context, Method method) {
        if (implementation == null) {
            throw new IllegalArgumentException("Extension implementation cannot be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }
    }

    /**
     * 执行扩展点方法
     * 
     * @param implementation 扩展实现对象
     * @param method 方法对象
     * @param args 方法参数
     * @param <T> 返回类型泛型
     * @return 方法执行结果
     * @throws Throwable 执行过程中的异常
     */
    private <T> T invokeForGuard(Object implementation, Method method, Object[] args) throws Exception {
        try {
            return executeMethod(implementation, method, args);
        } catch (Exception | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    private <T> T executeMethod(Object implementation, Method method, Object[] args) throws Throwable {
        try {
            return (T) method.invoke(implementation, args);
        } catch (Exception e) {
            if (e.getCause() != null) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            }
            throw e;
        }
    }

    /**
     * 处理安全异常
     * 
     * @param ex 安全异常
     * @param className 类名
     * @param methodName 方法名
     * @param startTime 开始时间
     */
    private void handleSecurityException(SecurityException ex, 
                                            String className, String methodName, 
                                            long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        log.error("Security exception in extension point: {}.{} after {}ms - {}",
                className, methodName, executionTime, ex.getMessage());
    }

    /**
     * 处理执行异常
     * 
     * @param e 异常对象
     * @param className 类名
     * @param methodName 方法名
     * @param startTime 开始时间
     */
    private void handleExecutionException(Exception e, 
                                         String className, String methodName, 
                                         long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        
        log.error("Execution exception in extension point: {}.{} after {}ms",
                className, methodName, executionTime, e);
    }

    private void recordMetrics(String extPoint, String impl, String status, long durationMs) {
        if (metricsCollector != null) {
            metricsCollector.recordInvoke(extPoint, impl, status, durationMs);
        }
    }

    private static String resolveExtPointName(Object implementation) {
        for (Class<?> iface : implementation.getClass().getInterfaces()) {
            if (iface.isInterface()) {
                return iface.getName();
            }
        }
        return implementation.getClass().getName();
    }

    private void reportToStudio(
            String className, String methodName, long durationMs, boolean success, String errorMessage) {
        if (studioReporter == null) {
            return;
        }
        if (success) {
            studioReporter.reportSuccess(className, methodName, durationMs);
        } else {
            studioReporter.reportFailure(className, methodName, durationMs, errorMessage);
        }
    }
}
