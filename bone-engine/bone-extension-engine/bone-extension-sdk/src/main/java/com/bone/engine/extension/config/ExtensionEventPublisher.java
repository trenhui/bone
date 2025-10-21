package com.bone.engine.extension.config;

import com.bone.engine.extension.context.BizContext;

/**
 * 扩展点事件发布器接口
 * <p>
 * 负责发布扩展点相关的事件，用于监控和扩展扩展点的行为
 * <strong>主要事件类型：</strong>
 * <ul>
 *   <li>扩展点执行前事件</li>
 *   <li>扩展点执行后事件</li>
 *   <li>扩展点异常事件</li>
 *   <li>扩展点路由选择事件</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public interface ExtensionEventPublisher {

    /**
     * 发布扩展点执行前事件
     *
     * @param extPointInterface 扩展点接口
     * @param extensionImpl 扩展点实现类
     * @param context 业务上下文
     */
    void publishBeforeEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context);

    /**
     * 发布扩展点执行后事件
     *
     * @param extPointInterface 扩展点接口
     * @param extensionImpl 扩展点实现类
     * @param context 业务上下文
     * @param result 执行结果
     */
    void publishAfterEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Object result);

    /**
     * 发布扩展点异常事件
     *
     * @param extPointInterface 扩展点接口
     * @param extensionImpl 扩展点实现类
     * @param context 业务上下文
     * @param exception 异常信息
     */
    void publishExceptionEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Exception exception);

    /**
     * 发布扩展点路由选择事件
     *
     * @param extPointInterface 扩展点接口
     * @param selectedImpl 选中的实现类
     * @param context 业务上下文
     */
    void publishRouteEvent(Class<?> extPointInterface, Object selectedImpl, BizContext<?> context);

    /**
     * 发布扩展点注册事件
     *
     * @param extPointInterface 扩展点接口
     * @param extensionImpl 扩展点实现类
     */
    void publishRegisterEvent(Class<?> extPointInterface, Object extensionImpl);
}