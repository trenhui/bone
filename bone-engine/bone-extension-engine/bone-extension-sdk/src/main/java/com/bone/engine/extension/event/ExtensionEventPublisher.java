package com.bone.engine.extension.event;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.event.ExtensionEvent.EventType;

/**
 * 扩展点事件发布器接口，定义扩展点事件的发布机制
 * <p>
 * 提供统一的事件发布接口，支持同步和异步事件发布，便于监控和扩展扩展点的行为
 * 实现观察者模式，降低系统组件间的耦合度
 * 集成版本管理事件，支持多版本扩展点的事件通知
 *
 * @author renhui.trh
 * @since 1.0.0
 */
public interface ExtensionEventPublisher {
    
    /**
     * 发布扩展点事件
     * 
     * @param event 扩展点事件对象
     */
    void publishEvent(ExtensionEvent<?> event);
    
    /**
     * 发布扩展点注册前事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param extensionImplName 扩展点实现名称
     */
    void publishBeforeRegister(Object source, String extensionPointName, String extensionImplName);
    
    /**
     * 发布扩展点注册后事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param extensionImplName 扩展点实现名称
     */
    void publishAfterRegister(Object source, String extensionPointName, String extensionImplName);
    
    /**
     * 发布扩展点调用前事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param extensionImplName 扩展点实现名称
     * @param bizContext 业务上下文
     */
    <T> void publishBeforeInvoke(Object source, String extensionPointName, 
                               String extensionImplName, BizContext<T> bizContext);
    
    /**
     * 发布扩展点调用成功事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param extensionImplName 扩展点实现名称
     * @param bizContext 业务上下文
     * @param result 执行结果
     * @param executionTimeMs 执行耗时（毫秒）
     */
    <T> void publishAfterInvokeSuccess(Object source, String extensionPointName, 
                                     String extensionImplName, BizContext<T> bizContext,
                                     Object result, long executionTimeMs);
    
    /**
     * 发布扩展点调用失败事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param extensionImplName 扩展点实现名称
     * @param bizContext 业务上下文
     * @param error 异常信息
     * @param executionTimeMs 执行耗时（毫秒）
     */
    <T> void publishAfterInvokeFailure(Object source, String extensionPointName, 
                                     String extensionImplName, BizContext<T> bizContext,
                                     Throwable error, long executionTimeMs);
    
    /**
     * 发布扩展点未找到事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param bizContext 业务上下文
     */
    <T> void publishExtensionNotFound(Object source, String extensionPointName, BizContext<T> bizContext);
    
    /**
     * 发布扩展点路由决策事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param selectedImpl 选中的实现名称
     * @param bizContext 业务上下文
     */
    <T> void publishRoutingDecision(Object source, String extensionPointName, 
                                 String selectedImpl, BizContext<T> bizContext);
    
    /**
     * 发布扩展点缓存命中事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param bizContext 业务上下文
     */
    <T> void publishCacheHit(Object source, String extensionPointName, BizContext<T> bizContext);
    
    /**
     * 发布扩展点缓存未命中事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param bizContext 业务上下文
     */
    <T> void publishCacheMiss(Object source, String extensionPointName, BizContext<T> bizContext);
    
    /**
     * 发布扩展点配置变更事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点接口名称
     * @param changeType 变更类型
     */
    void publishConfigurationChanged(Object source, String extensionPointName, String changeType);
    
    /**
     * 检查事件发布器是否启用
     * 
     * @return 如果启用则返回true
     */
    boolean isEnabled();
    
    /**
     * 设置事件发布器启用状态
     * 
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);
    
    /**
     * 检查特定事件类型是否启用
     * 
     * @param eventType 事件类型
     * @return 如果启用则返回true
     */
    boolean isEventTypeEnabled(EventType eventType);
    
    /**
     * 设置特定事件类型的启用状态
     * 
     * @param eventType 事件类型
     * @param enabled 是否启用
     */
    void setEventTypeEnabled(EventType eventType, boolean enabled);
    
    /**
     * 发布版本注册事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点名称
     * @param version 版本号
     * @param extensionImplName 扩展实现名称
     */
    void publishVersionRegister(Object source, String extensionPointName, String version, String extensionImplName);
    
    /**
     * 发布版本切换事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点名称
     * @param oldVersion 旧版本
     * @param newVersion 新版本
     * @param bizContext 业务上下文
     * @param <T> 业务上下文类型
     */
    <T> void publishVersionSwitch(Object source, String extensionPointName, String oldVersion, 
                                     String newVersion, BizContext<T> bizContext);
    
    /**
     * 发布版本兼容性检查事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点名称
     * @param targetVersion 目标版本
     * @param isCompatible 是否兼容
     * @param bizContext 业务上下文
     * @param <T> 业务上下文类型
     */
    <T> void publishVersionCompatibilityCheck(Object source, String extensionPointName, 
                                               String targetVersion, boolean isCompatible, 
                                               BizContext<T> bizContext);
    
    /**
     * 发布废弃版本使用事件
     * 
     * @param source 事件源
     * @param extensionPointName 扩展点名称
     * @param deprecatedVersion 废弃版本
     * @param recommendedVersion 推荐版本
     * @param bizContext 业务上下文
     * @param <T> 业务上下文类型
     */
    <T> void publishDeprecatedVersionUsed(Object source, String extensionPointName, 
                                            String deprecatedVersion, String recommendedVersion, 
                                            BizContext<T> bizContext);
}