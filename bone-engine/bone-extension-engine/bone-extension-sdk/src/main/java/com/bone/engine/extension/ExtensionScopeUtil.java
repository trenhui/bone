package com.bone.engine.extension;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 上下文管理增强工具类
 * 提供统一的上下文创建、配置和管理功能，解决代码重复和标准化问题
 */

/**
 * 扩展点作用域工具类
 * <p>
 * 提供统一的上下文管理功能，简化扩展点框架中的上下文创建、属性设置和生命周期管理
 * 封装了常用的上下文操作，提供标准化的属性管理和上下文传递机制
 * </p>
 *
 * @author AI Assistant
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExtensionScopeUtil {
    private static final Logger log = LoggerFactory.getLogger(ExtensionScopeUtil.class);

    // 标准属性名称常量
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_REQUEST_ID = "requestId";
    public static final String ATTR_TRANSACTION_ID = "transactionId";
    public static final String ATTR_TIMESTAMP = "timestamp";
    public static final String ATTR_SOURCE = "source";
    public static final String ATTR_TRACE_ID = "traceId";

    /**
     * 使用租户编码创建上下文作用域
     *
     * @param tenantCode 租户编码
     * @return 上下文管理器
     */
    public static ExtensionScope withTenant(@NonNull String tenantCode) {
        Assert.hasText(tenantCode, "Tenant code must not be empty");
        log.debug("Creating context scope for tenant: {}", tenantCode);
        return ExtensionContextManager.withTenant(tenantCode)
                .withAttribute(ATTR_REQUEST_ID, generateRequestId())
                .withAttribute(ATTR_TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 使用业务编码创建上下文作用域
     *
     * @param bizCode 业务编码
     * @return 上下文管理器
     */
    public static ExtensionScope withBusiness(@NonNull String bizCode) {
        Assert.hasText(bizCode, "Business code must not be empty");
        log.debug("Creating context scope for business: {}", bizCode);
        return ExtensionContextManager.withBusiness(bizCode)
                .withAttribute(ATTR_REQUEST_ID, generateRequestId())
                .withAttribute(ATTR_TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 使用租户和业务编码创建上下文作用域
     *
     * @param tenantCode 租户编码
     * @param bizCode    业务编码
     * @return 上下文管理器
     */
    public static ExtensionScope with(@NonNull String tenantCode, @NonNull String bizCode) {
        Assert.hasText(tenantCode, "Tenant code must not be empty");
        Assert.hasText(bizCode, "Business code must not be empty");
        log.debug("Creating context scope for tenant: {} and business: {}", tenantCode, bizCode);
        return ExtensionContextManager.with(tenantCode, bizCode)
                .withAttribute(ATTR_REQUEST_ID, generateRequestId())
                .withAttribute(ATTR_TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 使用业务数据创建上下文作用域
     *
     * @param data    业务数据
     * @param <T>     业务数据类型
     * @return 上下文管理器
     */
    public static <T> ExtensionScope withData(@NonNull T data) {
        BizContext<T> context = BizContext.create();
        context.setData(data);
        log.debug("Creating context scope with data: {}", data.getClass().getSimpleName());
        return ExtensionContextManager.with(context)
                .withAttribute(ATTR_REQUEST_ID, generateRequestId())
                .withAttribute(ATTR_TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 使用租户编码、业务编码和业务数据创建上下文作用域
     *
     * @param tenantCode 租户编码
     * @param bizCode    业务编码
     * @param data       业务数据
     * @param <T>        业务数据类型
     * @return 上下文管理器
     */
    public static <T> ExtensionScope with(@NonNull String tenantCode, @NonNull String bizCode, @NonNull T data) {
        Assert.hasText(tenantCode, "Tenant code must not be empty");
        Assert.hasText(bizCode, "Business code must not be empty");
        BizContext<T> context = BizContext.of(tenantCode, bizCode);
        context.setData(data);
        log.debug("Creating context scope for tenant: {}, business: {} with data: {}",
                tenantCode, bizCode, data.getClass().getSimpleName());
        return ExtensionContextManager.with(context)
                .withAttribute(ATTR_REQUEST_ID, generateRequestId())
                .withAttribute(ATTR_TIMESTAMP, System.currentTimeMillis());
    }

    /**
     * 设置用户ID属性
     *
     * @param scope   上下文管理器
     * @param userId  用户ID
     * @return 上下文管理器
     */
    public static ExtensionScope withUserId(@NonNull ExtensionScope scope, @NonNull String userId) {
        Assert.hasText(userId, "User ID must not be empty");
        scope.withAttribute(ATTR_USER_ID, userId);
        return scope;
    }

    /**
     * 设置事务ID属性
     *
     * @param scope         上下文管理器
     * @param transactionId 事务ID
     * @return 上下文管理器
     */
    public static ExtensionScope withTransactionId(@NonNull ExtensionScope scope, @NonNull String transactionId) {
        Assert.hasText(transactionId, "Transaction ID must not be empty");
        scope.withAttribute(ATTR_TRANSACTION_ID, transactionId);
        return scope;
    }

    /**
     * 设置来源属性
     *
     * @param scope  上下文管理器
     * @param source 来源
     * @return 上下文管理器
     */
    public static ExtensionScope withSource(@NonNull ExtensionScope scope, @NonNull String source) {
        Assert.hasText(source, "Source must not be empty");
        scope.withAttribute(ATTR_SOURCE, source);
        return scope;
    }

    /**
     * 在指定上下文中执行操作
     *
     * @param tenantCode 租户编码
     * @param bizCode    业务编码
     * @param action     要执行的操作
     * @param <R>        返回类型
     * @return 操作结果
     */
    public static <R> R doWithContext(@NonNull String tenantCode, @NonNull String bizCode, @NonNull Supplier<R> action) {
        try (ExtensionScope scope = with(tenantCode, bizCode)) {
            return action.get();
        }
    }

    /**
     * 在指定上下文中执行操作（无返回值）
     *
     * @param tenantCode 租户编码
     * @param bizCode    业务编码
     * @param action     要执行的操作
     */
    public static void doWithContextVoid(@NonNull String tenantCode, @NonNull String bizCode, @NonNull Runnable action) {
        try (ExtensionScope scope = with(tenantCode, bizCode)) {
            action.run();
        }
    }

    /**
     * 创建标准业务上下文范围
     * 自动设置租户、用户ID、事务ID、跟踪ID和时间戳等标准属性
     */
    public static ExtensionScope createStandardScope(String tenantCode, String userId) {
        Assert.hasText(tenantCode, "Tenant code must not be empty");
        Assert.hasText(userId, "User ID must not be empty");
        
        String transactionId = generateTransactionId();
        String traceId = generateTraceId();
        
        log.debug("Creating standard context scope for tenant: {}, user: {}", tenantCode, userId);
        return ExtensionContextManager.withTenant(tenantCode)
                .withAttribute(ATTR_USER_ID, userId)
                .withAttribute(ATTR_TRANSACTION_ID, transactionId)
                .withAttribute(ATTR_TRACE_ID, traceId)
                .withAttribute(ATTR_TIMESTAMP, Instant.now().toEpochMilli());
    }
    
    /**
     * 创建增强的业务上下文
     * 自动从请求对象提取信息并设置到上下文中
     */
    public static <T extends BaseRequest> BizContext<T> createEnhancedContext(T request) {
        Assert.notNull(request, "Request must not be null");
        
        BizContext<T> context = ExtensionContextManager.fromData(request);
        
        // 确保设置标准属性
        if (!context.hasAttribute(ATTR_USER_ID) && request.getUserId() != null) {
            context.withAttribute(ATTR_USER_ID, request.getUserId());
        }
        
        // 生成或使用现有事务ID
        String transactionId = (String) context.getAttributeOrDefault(ATTR_TRANSACTION_ID,
                generateTransactionId());
        context.withAttribute(ATTR_TRANSACTION_ID, transactionId);
        
        // 生成跟踪ID
        if (!context.hasAttribute(ATTR_TRACE_ID)) {
            context.withAttribute(ATTR_TRACE_ID, generateTraceId());
        }
        
        // 确保有时间戳
        if (!context.hasAttribute(ATTR_TIMESTAMP)) {
            context.withAttribute(ATTR_TIMESTAMP, Instant.now().toEpochMilli());
        }
        
        return context;
    }
    
    /**
     * 执行带上下文的业务操作
     * 自动创建上下文并管理生命周期，简化异常处理
     */
    public static <T, R> R executeWithContext(String tenantCode, T request, 
                                             ContextFunction<T, R> function) {
        Assert.hasText(tenantCode, "Tenant code must not be empty");
        Assert.notNull(request, "Request must not be null");
        Assert.notNull(function, "Function must not be null");
        
        String userId = extractUserId(request);
        
        try (ExtensionScope scope = createStandardScope(tenantCode, userId)) {
            // 创建业务上下文并设置请求数据
            BizContext<T> context = ExtensionContextManager.fromData(request);
            
            // 确保业务上下文包含标准属性
            context.withAttribute(ATTR_USER_ID, userId)
                   .withAttribute(ATTR_TRANSACTION_ID, generateTransactionId())
                   .withAttribute(ATTR_TRACE_ID, generateTraceId())
                   .withAttribute(ATTR_TIMESTAMP, Instant.now().toEpochMilli());
                   
            return function.apply(context);
        } catch (Exception e) {
            log.error("Error in context execution, tenant={}, userId={}", tenantCode, userId, e);
            throw new RuntimeException("Context execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成请求ID
     *
     * @return 请求ID
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成事务ID
     *
     * @return 事务ID
     */
    public static String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 生成跟踪ID
     *
     * @return 跟踪ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 从请求对象提取用户ID（可定制）
     */
    private static <T> String extractUserId(T request) {
        // 实现根据不同请求类型提取用户ID的逻辑
        // 可以使用反射或接口方法
        if (request instanceof BaseRequest) {
            return ((BaseRequest) request).getUserId();
        }
        return "UNKNOWN";
    }
    
    /**
     * 函数式接口，用于带上下文的业务操作
     */
    @FunctionalInterface
    public interface ContextFunction<T, R> {
        R apply(BizContext<T> context) throws Exception;
    }
    
    /**
     * 基础请求接口，定义获取用户ID的方法
     */
    public interface BaseRequest {
        String getUserId();
    }

    /**
     * 验证上下文的有效性
     *
     * @param context 业务上下文
     * @return 是否有效
     */
    public static boolean isValidContext(BizContext<?> context) {
        if (context == null) {
            return false;
        }
        // 至少需要有租户编码或业务编码
        return context.getTenantCode() != null || context.getBizCode() != null;
    }

    /**
     * 创建上下文快照
     * <p>
     * 用于在多线程环境中安全地传递上下文信息
     * </p>
     *
     * @param context 业务上下文
     * @return 上下文快照
     */
    public static ContextSnapshot createSnapshot(BizContext<?> context) {
        if (context == null) {
            return new ContextSnapshot(null, null, new ConcurrentHashMap<>());
        }
        return new ContextSnapshot(
                context.getTenantCode(),
                context.getBizCode(),
                context.getAttributes() != null ? new ConcurrentHashMap<>(context.getAttributes()) : new ConcurrentHashMap<>()
        );
    }

    /**
     * 上下文快照类，用于在多线程环境中安全地传递上下文信息
     */
    public static class ContextSnapshot {
        private final String tenantCode;
        private final String bizCode;
        private final Map<String, Object> attributes;

        public ContextSnapshot(String tenantCode, String bizCode, Map<String, Object> attributes) {
            this.tenantCode = tenantCode;
            this.bizCode = bizCode;
            this.attributes = attributes;
        }

        /**
         * 应用快照到当前线程
         *
         * @return 上下文管理器
         */
        public ExtensionScope apply() {
            ExtensionScope scope;
            if (tenantCode != null && bizCode != null) {
                scope = with(tenantCode, bizCode);
            } else if (tenantCode != null) {
                scope = withTenant(tenantCode);
            } else if (bizCode != null) {
                scope = withBusiness(bizCode);
            } else {
                scope = ExtensionContextManager.withContext();
            }
            
            // 应用属性
            if (attributes != null) {
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    scope.withAttribute(entry.getKey(), entry.getValue());
                }
            }
            
            return scope;
        }

        public String getTenantCode() {
            return tenantCode;
        }

        public String getBizCode() {
            return bizCode;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextSnapshot that = (ContextSnapshot) o;
            return Objects.equals(tenantCode, that.tenantCode) &&
                   Objects.equals(bizCode, that.bizCode) &&
                   Objects.equals(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantCode, bizCode, attributes);
        }
    }
}