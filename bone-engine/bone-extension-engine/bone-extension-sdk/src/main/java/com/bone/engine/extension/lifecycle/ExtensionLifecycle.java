package com.bone.engine.extension.lifecycle;

import com.bone.engine.extension.context.BizContext;

/**
 * 扩展点生命周期管理接口
 * <p>
 * 为扩展点实现提供生命周期钩子，允许在初始化和销毁时执行特定操作
 * <strong>生命周期阶段：</strong>
 * <ul>
 *   <li>初始化：扩展点被加载和初始化时</li>
 *   <li>前置处理：每次调用扩展点方法前</li>
 *   <li>后置处理：每次调用扩展点方法后</li>
 *   <li>销毁：扩展点被卸载时</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public interface ExtensionLifecycle {

    /**
     * 扩展点初始化方法
     * <p>
     * 在扩展点被注册到容器时调用，用于执行初始化操作
     * </p>
     */
    void initialize();

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
     * 扩展点后置处理方法
     * <p>
     * 在每次调用扩展点方法后执行，可以用于结果处理、日志记录等
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param result 执行结果
     */
    void afterInvoke(BizContext<?> context, String methodName, Object result);

    /**
     * 扩展点异常处理方法
     * <p>
     * 当扩展点方法执行抛出异常时调用，可以用于异常处理、回滚等
     * </p>
     *
     * @param context 业务上下文
     * @param methodName 方法名
     * @param exception 异常信息
     */
    void onException(BizContext<?> context, String methodName, Exception exception);

    /**
     * 扩展点销毁方法
     * <p>
     * 在扩展点被卸载时调用，用于释放资源
     * </p>
     */
    void destroy();
}