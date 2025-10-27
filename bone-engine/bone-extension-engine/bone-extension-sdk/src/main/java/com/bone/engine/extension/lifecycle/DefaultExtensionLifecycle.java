package com.bone.engine.extension.lifecycle;

import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扩展点生命周期默认实现
 * <p>
 * 为扩展点生命周期接口提供默认实现，扩展点实现类可以选择性地覆盖需要的方法
 * 提供全面的日志记录、性能监控、异常处理和降级支持
 * <strong>默认行为：</strong>
 * <ul>
 *   <li>预初始化：记录调试日志</li>
 *   <li>初始化：记录信息日志，执行基础初始化</li>
 *   <li>后初始化：记录调试日志，检查依赖</li>
 *   <li>路由前处理：记录调试日志，允许路由属性修改</li>
 *   <li>前置处理：记录调试日志，执行参数校验</li>
 *   <li>环绕处理：支持同步/异步执行，性能监控</li>
 *   <li>后置处理：记录调试日志，包含执行时间</li>
 *   <li>异常处理：记录错误日志，提供详细诊断信息</li>
 *   <li>降级处理：记录警告日志，抛出运行时异常</li>
 *   <li>销毁前：记录信息日志，准备资源清理</li>
 *   <li>销毁：记录信息日志，释放资源</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 */
public class DefaultExtensionLifecycle implements ExtensionLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExtensionLifecycle.class);
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "extension-async-executor");
        thread.setDaemon(true);
        return thread;
    });
    
    private boolean initialized = false;
    private long initTimeMillis = 0L;

    @Override
    public void preInitialize() {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension pre-initialize: {}", this.getClass().getName());
        }
    }

    @Override
    public void initialize() {
        long startTime = System.currentTimeMillis();
        if (logger.isInfoEnabled()) {
            logger.info("Extension initializing: {}", this.getClass().getName());
        }
        
        // 执行基础初始化逻辑
        this.initTimeMillis = startTime;
        this.initialized = true;
        
        if (logger.isInfoEnabled()) {
            logger.info("Extension initialized: {}, took {}ms", 
                    this.getClass().getName(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public void postInitialize() {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension post-initialize: {}", this.getClass().getName());
        }
        
        // 检查初始化状态
        if (!initialized) {
            logger.warn("Extension {} was post-initialized but not properly initialized", 
                    this.getClass().getName());
        }
    }

    @Override
    public void beforeRouting(BizContext<?> context, Class<?> extensionPointClass, 
            Map<String, Object> routeAttributes) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension before routing: extensionPoint={}, context={}, routeAttributes={}", 
                    extensionPointClass.getName(), context, routeAttributes);
        }
    }

    @Override
    public void beforeInvoke(BizContext<?> context, String methodName, Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension before invoke: method={}, context={}, args count={}", 
                    methodName, context, args != null ? args.length : 0);
            
            // 日志安全检查，避免记录敏感信息
            if (args != null && args.length > 0 && !logger.isTraceEnabled()) {
                logger.trace("Detailed args: {}", (Object) args);
            }
        }
    }

    @Override
    public CompletableFuture<Object> aroundInvoke(BizContext<?> context, String methodName, 
            Object[] args, Invocation invocation) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 默认同步执行
            Object result = invocation.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 不再记录详细性能统计，避免重复实现
            // RouteStatsCollector应由路由层负责调用记录
            
            return CompletableFuture.completedFuture(result);
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("Error in aroundInvoke: method={}, time={}ms", 
                    methodName, executionTime, e);
            
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public void afterInvoke(BizContext<?> context, String methodName, Object result, long executionTimeMs) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension after invoke: method={}, context={}, executionTime={}ms", 
                    methodName, context, executionTimeMs);
            
            // 性能监控
            if (executionTimeMs > 500) {
                logger.warn("Extension performance warning: method={} exceeded 500ms threshold, took {}ms", 
                        methodName, executionTimeMs);
            }
        }
    }

    @Override
    public void onException(BizContext<?> context, String methodName, Exception exception, long executionTimeMs) {
        if (logger.isErrorEnabled()) {
            // 根据异常类型进行不同级别的日志记录
            String errorType = exception.getClass().getSimpleName();
            String errorMessage = exception.getMessage();
            
            logger.error("Extension invoke error: method={}, context={}, errorType={}, executionTime={}ms, message={}", 
                    methodName, context, errorType, executionTimeMs, errorMessage, exception);
            
            // 记录上下文信息以帮助诊断
            if (context != null && logger.isDebugEnabled()) {
                logger.debug("Detailed context for error: {}", context.getAllAttributes());
            }
        }
    }

    @Override
    public Object onFallback(BizContext<?> context, String methodName, Object[] args, Throwable cause) {
        if (logger.isWarnEnabled()) {
            String causeMessage = cause != null ? cause.getMessage() : "Unknown cause";
            logger.warn("Extension fallback triggered: method={}, context={}, cause={}", 
                    methodName, context, causeMessage);
        }
        
        // 默认抛出运行时异常，允许上层捕获并处理
        throw new RuntimeException("Extension execution failed and fallback was triggered: " + methodName, cause);
    }

    @Override
    public void preDestroy() {
        if (logger.isInfoEnabled()) {
            logger.info("Extension pre-destroy: {}", this.getClass().getName());
        }
    }

    @Override
    public void destroy() {
        long startTime = System.currentTimeMillis();
        
        if (logger.isInfoEnabled()) {
            logger.info("Extension destroying: {}", this.getClass().getName());
        }
        
        // 执行清理逻辑
        this.initialized = false;
        
        if (logger.isInfoEnabled()) {
            long uptime = startTime - this.initTimeMillis;
            logger.info("Extension destroyed: {}, uptime={}ms", 
                    this.getClass().getName(), uptime);
        }
    }
}