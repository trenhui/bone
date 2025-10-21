package com.bone.metadata.sdk.domain.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;

/**
 * SDK异常处理器
 * 提供统一的异常处理、日志记录和监控功能
 */
public class ExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);
    private static final ExceptionHandler INSTANCE = new ExceptionHandler();
    
    // 异常统计计数器
    private final Map<String, AtomicLong> exceptionCounters = new ConcurrentHashMap<>();
    // 敏感字段掩码映射
    private final Map<String, String> sensitiveFields = new ConcurrentHashMap<>();
    
    static {
        // 初始化敏感字段
        INSTANCE.registerSensitiveField("password");
        INSTANCE.registerSensitiveField("pwd");
        INSTANCE.registerSensitiveField("secret");
        INSTANCE.registerSensitiveField("token");
        INSTANCE.registerSensitiveField("auth");
        INSTANCE.registerSensitiveField("credential");
        INSTANCE.registerSensitiveField("credit_card");
        INSTANCE.registerSensitiveField("ssn");
    }
    
    private ExceptionHandler() {
        // 私有构造函数
    }
    
    /**
     * 获取异常处理器实例
     * @return 异常处理器单例
     */
    public static ExceptionHandler getInstance() {
        return INSTANCE;
    }
    
    /**
     * 处理异常
     * @param e 异常对象
     * @return 包装后的异常
     */
    public SDKException handleException(Throwable e) {
        if (e == null) {
            logger.warn("Handling null exception");
            return new SDKException("Null exception encountered");
        }
        
        // 增加异常计数
        incrementExceptionCount(e.getClass().getSimpleName());
        
        // 如果已经是SDKException，直接返回
        if (e instanceof SDKException) {
            logException((SDKException) e);
            return (SDKException) e;
        }
        
        // 根据异常类型进行转换和增强
        SDKException enhancedException;
        
        if (e instanceof IllegalArgumentException) {
            enhancedException = new SDKException("ILLEGAL_ARGUMENT", e.getMessage(), e);
        } else if (e instanceof SecurityException) {
            enhancedException = new SqlInjectionRiskException("Security violation detected", e)
                    .setRiskLevel("HIGH");
        } else if (e instanceof ClassCastException) {
            enhancedException = new SDKException("CLASS_CAST_ERROR", "Type conversion failed: " + e.getMessage(), e);
        } else if (e instanceof NullPointerException) {
            enhancedException = new SDKException("NULL_POINTER", "Null reference encountered", e);
        } else {
            // 默认转换为SDK异常
            enhancedException = new SDKException("UNEXPECTED_ERROR", e.getMessage() != null ? e.getMessage() : "Unknown error", e);
        }
        
        // 添加通用上下文信息
        enhancedException.addContext("timestamp", Instant.now().toString())
                        .addContext("exceptionType", e.getClass().getName())
                        .addContext("threadName", Thread.currentThread().getName());
        
        // 记录异常
        logException(enhancedException);
        
        return enhancedException;
    }
    
    /**
     * 记录异常日志
     * @param exception SDK异常
     */
    private void logException(SDKException exception) {
        if (exception == null) return;
        
        // 准备日志消息，屏蔽敏感信息
        String maskedMessage = maskSensitiveInfo(exception.getMessage());
        String errorId = exception.getErrorId();
        String errorCode = exception.getErrorCode();
        
        // 根据错误级别决定日志级别
        if ("SQL_INJECTION_RISK".equals(errorCode) || "SECURITY_ERROR".equals(errorCode)) {
            logger.error("[{}] [{}] SECURITY ALERT: {}", errorId, errorCode, maskedMessage, exception);
        } else if (exception.getCause() != null && exception.getCause() instanceof RuntimeException) {
            logger.error("[{}] [{}] Error occurred: {}", errorId, errorCode, maskedMessage, exception);
        } else {
            logger.warn("[{}] [{}] Warning occurred: {}", errorId, errorCode, maskedMessage);
        }
        
        // 记录异常上下文（确保敏感信息已被屏蔽）
        if (!exception.getContext().isEmpty()) {
            Map<String, Object> maskedContext = maskSensitiveInfoInContext(exception.getContext());
            logger.debug("[{}] Exception context: {}", errorId, maskedContext);
        }
    }
    
    /**
     * 增加异常计数
     * @param exceptionType 异常类型名称
     */
    private void incrementExceptionCount(String exceptionType) {
        exceptionCounters.computeIfAbsent(exceptionType, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 屏蔽消息中的敏感信息
     * @param message 原始消息
     * @return 屏蔽后的消息
     */
    private String maskSensitiveInfo(String message) {
        if (message == null) return null;
        
        String masked = message;
        for (String sensitiveField : sensitiveFields.keySet()) {
            masked = masked.replaceAll(
                "(?i)(" + sensitiveField + "[\\s]*=[\\s]*['\"]([^'\"]*))['\"]", 
                "$1***"
            );
        }
        return masked;
    }
    
    /**
     * 屏蔽上下文中的敏感信息
     * @param context 原始上下文
     * @return 屏蔽后的上下文
     */
    private Map<String, Object> maskSensitiveInfoInContext(Map<String, Object> context) {
        if (context == null) return new ConcurrentHashMap<>();
        
        Map<String, Object> maskedContext = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 检查键是否包含敏感字段
            boolean isSensitive = false;
            for (String sensitiveField : sensitiveFields.keySet()) {
                if (key.toLowerCase().contains(sensitiveField.toLowerCase())) {
                    isSensitive = true;
                    break;
                }
            }
            
            if (isSensitive && value instanceof String) {
                maskedContext.put(key, "***");
            } else if (value instanceof Map) {
                // 递归处理嵌套Map
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                maskedContext.put(key, maskSensitiveInfoInContext(nestedMap));
            } else {
                maskedContext.put(key, value);
            }
        }
        
        return maskedContext;
    }
    
    /**
     * 注册敏感字段
     * @param fieldName 敏感字段名
     */
    public void registerSensitiveField(String fieldName) {
        if (fieldName != null && !fieldName.isEmpty()) {
            sensitiveFields.put(fieldName.toLowerCase(), fieldName);
        }
    }
    
    /**
     * 获取异常统计信息
     * @return 异常统计映射
     */
    public Map<String, Long> getExceptionStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : exceptionCounters.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().get());
        }
        return stats;
    }
    
    /**
     * 重置异常统计
     */
    public void resetExceptionStatistics() {
        exceptionCounters.clear();
        logger.info("Exception statistics reset");
    }
    
    /**
     * 处理查询构建异常
     * @param message 错误消息
     * @param entityType 实体类型
     * @param phase 构建阶段
     * @return 查询构建异常
     */
    public QueryBuildException createQueryBuildException(String message, Class<?> entityType, String phase) {
        QueryBuildException exception = new QueryBuildException(message);
        
        if (entityType != null) {
            exception.setEntityType(entityType);
        }
        
        if (phase != null) {
            exception.setBuildPhase(phase);
        }
        
        logException(exception);
        return exception;
    }
    
    /**
     * 处理查询执行异常
     * @param message 错误消息
     * @param sql SQL语句
     * @param cause 根本原因
     * @return 查询执行异常
     */
    public QueryExecutionException createQueryExecutionException(String message, String sql, Throwable cause) {
        QueryExecutionException exception = new QueryExecutionException(message, cause);
        
        if (sql != null) {
            exception.setSql(sql);
        }
        
        logException(exception);
        return exception;
    }
}