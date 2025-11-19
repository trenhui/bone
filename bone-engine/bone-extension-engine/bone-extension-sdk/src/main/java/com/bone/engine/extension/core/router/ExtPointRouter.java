package com.bone.engine.extension.core.router;

import com.bone.engine.extension.support.context.BizContext;

import java.util.Map;

/**
 * 扩展点路由器接口
 * <p>
 * 负责根据业务上下文为扩展点接口选择合适的实现类
 * <strong>核心功能：</strong>实现扩展点的动态路由匹配，支持缓存优化，保证路由结果一致性
 * </p>
 * 
 * <h3>路由引擎设计原则：</h3>
 * <ul>
 *   <li><strong>高性能：</strong>优化路由算法，支持缓存机制</li>
 *   <li><strong>可扩展性：</strong>支持自定义路由策略</li>
 *   <li><strong>准确性：</strong>确保匹配规则精确一致</li>
 *   <li><strong>可观测性：</strong>提供路由过程日志和追踪能力</li>
 * </ul>
 * 
 * <h3>路由匹配策略：</h3>
 * <ol>
 *   <li>优先匹配精确条件（tenantCode、bizCode等）</li>
 *   <li>根据匹配条件数量和优先级排序</li>
 *   <li>支持动态条件表达式匹配</li>
 *   <li>在无匹配实现时回退到默认实现</li>
 * </ol>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 * @see DefaultExtPointRouter 默认路由实现
 * @see BizContext 业务上下文
 */
public interface ExtPointRouter {
    
    /**
     * 为指定的扩展点接口选择合适的实现类
     * 
     * @param <T> 扩展点接口类型
     * @param extPointClass 扩展点接口类
     * @param context 业务上下文
     * @return 匹配的实现类实例，如果没有找到则返回null
     */
    <T> T route(Class<T> extPointClass, BizContext<?> context);
    

    /**
     * 清除指定扩展点接口的路由缓存
     * 
     * @param extPointClass 扩展点接口类
     */
    void clearCache(Class<?> extPointClass);

    
    /**
     * 注册扩展点实现
     * 
     * @param <T> 扩展点接口类型
     * @param extPointClass 扩展点接口类
     * @param implementation 实现类实例
     */
    <T> void registerImplementation(Class<T> extPointClass, T implementation);
    
    /**
     * 取消注册扩展点实现
     * 
     * @param <T> 扩展点接口类型
     * @param extPointClass 扩展点接口类
     * @param implementation 实现类实例
     */
    <T> void unregisterImplementation(Class<T> extPointClass, T implementation);
    
    /**
     * 获取扩展点接口的默认实现
     * 
     * @param <T> 扩展点接口类型
     * @param extPointClass 扩展点接口类
     * @return 默认实现类实例，如果没有则返回null
     */
    <T> T getDefaultImplementation(Class<T> extPointClass);
    
    /**
     * 获取路由匹配统计信息
     * 
     * @return 路由统计信息映射
     */
    Map<String, Map<String, Long>> getRouteStats();
}