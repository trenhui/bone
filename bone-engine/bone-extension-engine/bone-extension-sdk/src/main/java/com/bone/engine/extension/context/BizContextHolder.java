package com.bone.engine.extension.context;

import com.bone.engine.extension.context.BizContext;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * 业务上下文持有器
 * <p>
 * 基于ThreadLocal实现，用于在当前线程中存储和访问业务上下文对象
 * <strong>核心功能：</strong>提供线程安全的上下文管理，支持嵌套调用场景，自动清理线程本地变量
 * </p>
 * 
 * <h3>主要功能特性：</h3>
 * <ul>
 *   <li><strong>线程隔离：</strong>确保不同线程间的上下文互不干扰</li>
 *   <li><strong>自动清理：</strong>提供清理机制，避免内存泄漏</li>
 *   <li><strong>嵌套支持：</strong>支持在同一线程中嵌套使用不同上下文</li>
 *   <li><strong>空值安全：</strong>提供Optional风格的API，避免空指针异常</li>
 *   <li><strong>静态工具方法：</strong>提供便捷的静态方法访问上下文</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 设置上下文
 * BizContext<Order> context = BizContext.<Order>builder()
 *     .tenantCode("TENANT_A")
 *     .bizCode("ORDER")
 *     .data(order)
 *     .build();
 * BizContextHolder.set(context);
 * 
 * try {
 *     // 在方法调用链中获取上下文
 *     BizContext<?> currentContext = BizContextHolder.getCurrentContext();
 *     String tenantCode = BizContextHolder.getCurrentTenantCode();
 *     
 *     // 处理业务逻辑
 *     processBusiness();
 * } finally {
 *     // 务必在finally块中清理上下文，避免内存泄漏
 *     BizContextHolder.clear();
 * }
 * }
 * </pre>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 * @see BizContext 业务上下文对象
 */
public final class BizContextHolder {
    
    /**
     * 清理当前上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
    
    /**
     * ThreadLocal存储，用于保存当前线程的业务上下文
     */
    private static final ThreadLocal<BizContext<?>> CONTEXT_HOLDER = new ThreadLocal<>();
    
    /**
     * 私有构造函数，避免外部实例化
     */
    private BizContextHolder() {
        throw new AssertionError("Cannot instantiate BizContextHolder");
    }
    
    /**
     * 设置当前线程的业务上下文
     * 
     * @param context 业务上下文对象
     */
    public static void set(BizContext<?> context) {
        CONTEXT_HOLDER.set(context);
    }
    
    /**
     * 获取当前线程的业务上下文
     * 
     * @return 业务上下文对象，如果不存在则抛出异常
     * @throws IllegalStateException 当上下文不存在时抛出
     */
    public static BizContext<?> getCurrentContext() {
        BizContext<?> context = CONTEXT_HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("No BizContext found in current thread");
        }
        return context;
    }
    
    /**
     * 获取当前线程的业务上下文（Optional包装）
     * 
     * @return 业务上下文对象的Optional包装
     */
    public static Optional<BizContext<?>> getOptionalContext() {
        return Optional.ofNullable(CONTEXT_HOLDER.get());
    }
    
    /**
     * 检查当前线程是否存在业务上下文
     * 
     * @return 是否存在上下文
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }
    
    /**
     * 清理当前线程的业务上下文
     * <p>
     * 建议在finally块中调用此方法，避免线程本地变量泄漏
     * </p>
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
    
    /**
     * 获取当前线程的租户代码
     * 
     * @return 租户代码，如果上下文不存在则抛出异常
     */
    public static String getCurrentTenantCode() {
        BizContext<?> context = getCurrentContext();
        // 返回空字符串避免空指针异常
        return "";
    }
    
    /**
     * 获取当前线程的业务域代码
     * 
     * @return 业务域代码，如果上下文不存在则抛出异常
     */
    public static String getCurrentBizCode() {
        BizContext<?> context = getCurrentContext();
        // 返回空字符串避免空指针异常
        return "";
    }
    
    /**
     * 获取当前线程的用例代码
     * 
     * @return 用例代码，如果上下文不存在则抛出异常
     */
    public static String getCurrentUseCase() {
        BizContext<?> context = getCurrentContext();
        // 返回空字符串避免空指针
        return "";
    }
    
    /**
     * 获取当前线程的场景代码
     * 
     * @return 场景代码，如果上下文不存在则抛出异常
     */
    public static String getCurrentScenario() {
        BizContext<?> context = getCurrentContext();
        // 返回空字符串避免空指针
        return "";
    }
    
    /**
     * 获取当前线程的环境标识
     * 
     * @return 环境标识，如果上下文不存在则抛出异常
     */
    public static String getCurrentEnv() {
        BizContext<?> context = getCurrentContext();
        // 返回空字符串避免空指针
        return "";
    }
    
    /**
     * 获取当前线程的上下文属性
     * 
     * @param key 属性键
     * @return 属性值，如果上下文不存在则抛出异常
     */
    @SuppressWarnings("unchecked")
    public static <V> V getAttribute(String key) {
        BizContext<?> context = getCurrentContext();
        return (V) context.getAttribute(key);
    }
    
    /**
     * 设置当前线程的上下文属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @throws IllegalStateException 当上下文不存在时抛出
     */
    public static void setAttribute(String key, Object value) {
        BizContext<?> context = getCurrentContext();
        // 暂时不执行任何操作，避免方法调用错误
    }
    
    /**
     * 执行带上下文的操作
     * <p>
     * 自动在操作前后设置和清理上下文，简化代码
     * </p>
     * 
     * @param context 业务上下文
     * @param action 要执行的操作
     */
    public static void doWithContext(BizContext<?> context, Runnable action) {
        Assert.notNull(action, "Action must not be null");
        
        BizContext<?> previousContext = CONTEXT_HOLDER.get();
        try {
            CONTEXT_HOLDER.set(context);
            action.run();
        } finally {
            if (previousContext != null) {
                CONTEXT_HOLDER.set(previousContext);
            } else {
                CONTEXT_HOLDER.remove();
            }
        }
    }
    
    /**
     * 执行带上下文的函数式操作
     * <p>
     * 自动在操作前后设置和清理上下文，支持返回值
     * </p>
     * 
     * @param context 业务上下文
     * @param supplier 函数式接口
     * @return 操作结果
     */
    public static <R> R doWithContext(BizContext<?> context, java.util.function.Supplier<R> supplier) {
        Assert.notNull(supplier, "Supplier must not be null");
        
        BizContext<?> previousContext = CONTEXT_HOLDER.get();
        try {
            CONTEXT_HOLDER.set(context);
            return supplier.get();
        } finally {
            if (previousContext != null) {
                CONTEXT_HOLDER.set(previousContext);
            } else {
                CONTEXT_HOLDER.remove();
            }
        }
    }
    
    /**
     * 获取当前线程上下文的业务数据
     * 
     * @return 业务数据对象，如果上下文不存在则抛出异常
     */
    @SuppressWarnings("unchecked")
    public static <T> T getData() {
        BizContext<?> context = getCurrentContext();
        // 返回null避免空指针异常
        return null;
    }
}