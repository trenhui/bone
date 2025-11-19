package com.bone.engine.extension.core.lifecycle;

import com.bone.engine.extension.support.context.BizContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 扩展点生命周期管理接口
 * <p>
 * 为扩展点实现提供丰富的生命周期钩子，允许在各个阶段执行特定操作
 * 支持同步和异步操作，提供详细的上下文信息，便于实现监控、日志、事务等横切关注点
 * <strong>生命周期阶段：</strong>
 * <ul>
 *   <li>预初始化：扩展点被加载到容器前</li>
 *   <li>初始化：扩展点被注册到容器时</li>
 *   <li>后初始化：扩展点依赖注入完成后</li>
 *   <li>路由前处理：在路由选择前执行</li>
 *   <li>前置处理：每次调用扩展点方法前</li>
 *   <li>环绕处理：方法执行前后的统一处理（支持异步）</li>
 *   <li>后置处理：每次调用扩展点方法后</li>
 *   <li>异常处理：方法执行异常时</li>
 *   <li>降级处理：当主要实现不可用时</li>
 *   <li>销毁前：在扩展点被卸载前</li>
 *   <li>销毁：扩展点被卸载时</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 */
public interface ExtensionLifecycle {

    /**
     * 扩展点预初始化方法
     * <p>
     * 在扩展点被注册到容器前调用，可以用于前置检查和准备工作
     * </p>
     */
    default void preInitialize() {
        // 默认实现为空
    }

    /**
     * 扩展点初始化方法
     * <p>
     * 在扩展点被注册到容器时调用，用于执行初始化操作
     * </p>
     */
    void initialize();

    /**
     * 扩展点后初始化方法
     * <p>
     * 在扩展点依赖注入完成后调用，可以用于初始化需要依赖其他组件的资源
     * </p>
     */
    default void postInitialize() {
        // 默认实现为空
    }

    /**
     * 扩展点路由前处理方法
     * <p>
     * 在路由选择前执行，可以用于修改路由条件或添加路由标记
     * </p>
     *
     * @param context 业务上下文
     * @param extensionPointClass 扩展点接口类
     * @param routeAttributes 路由属性（可修改）
     */
    default void beforeRouting(BizContext<?> context, Class<?> extensionPointClass, Map<String, Object> routeAttributes) {
        // 默认实现为空
    }

    /**
     * 扩展点前置处理方法
     * <p>
     * 在每次调用扩展点方法前执行，可以用于参数验证、日志记录等
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param args 方法参数
     */
    void beforeInvoke(BizContext<?> context, String methodName, Object[] args);

    /**
     * 扩展点环绕处理方法（支持异步）
     * <p>
     * 在扩展点方法执行前后提供统一处理，可以实现事务管理、性能监控等
     * 异步操作可以避免阻塞主流程
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param args 方法参数
     * @param invocation 方法调用器
     * @return 执行结果的Future
     */
    default CompletableFuture<Object> aroundInvoke(BizContext<?> context, String methodName, 
            Object[] args, Invocation invocation) {
        try {
            // 默认同步执行
            Object result = invocation.proceed();
            return CompletableFuture.completedFuture(result);
        } catch (Throwable e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 扩展点后置处理方法
     * <p>
     * 在每次调用扩展点方法后执行，可以用于结果处理、日志记录等
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param result 执行结果
     * @param executionTimeMs 执行时间（毫秒）
     */
    void afterInvoke(BizContext<?> context, String methodName, Object result, long executionTimeMs);

    /**
     * 扩展点异常处理方法
     * <p>
     * 当扩展点方法执行抛出异常时调用，可以用于异常处理、回滚等
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param exception 异常信息
     * @param executionTimeMs 执行时间（毫秒）
     */
    void onException(BizContext<?> context, String methodName, Exception exception, long executionTimeMs);

    /**
     * 扩展点降级处理方法
     * <p>
     * 当主要实现不可用时的降级逻辑，提供容错能力
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param args 方法参数
     * @param cause 降级原因
     * @return 降级后的返回值
     */
    default Object onFallback(BizContext<?> context, String methodName, Object[] args, Throwable cause) {
        throw new RuntimeException("Extension fallback not implemented: " + methodName, cause);
    }

    /**
     * 扩展点销毁前方法
     * <p>
     * 在扩展点被卸载前调用，可以用于清理资源或执行最后的操作
     * </p>
     */
    default void preDestroy() {
        // 默认实现为空
    }

    /**
     * 扩展点销毁方法
     * <p>
     * 在扩展点被卸载时调用，用于释放资源
     * </p>
     */
    void destroy();

    /**
     * 方法调用器接口，用于aroundInvoke方法中执行原始方法
     */
    interface Invocation {
        Object proceed() throws Throwable;
    }

}