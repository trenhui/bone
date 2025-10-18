package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;
import org.springframework.lang.NonNull;

/**
 * 扩展点路由器接口，负责根据业务上下文定位合适的扩展实现
 * <p>
 * 路由策略是扩展点框架的核心机制，不同的实现可以提供不同的路由算法：
 * <ul>
 *   <li>精确匹配路由</li>
 *   <li>表达式动态路由</li>
 *   <li>规则引擎路由</li>
 *   <li>权重路由</li>
 * </ul>
 * <p>
 * 路由过程应考虑性能优化，通常通过缓存热点路由结果来提升性能。
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 * @see DefaultExtPointRouter 默认实现的扩展点路由器
 */
public interface ExtPointRouter {
    /**
     * 根据业务上下文定位合适的扩展实现
     * 
     * @param <C> 扩展点接口类型
     * @param targetInterface 目标扩展点接口类
     * @param bizContext 业务上下文，包含路由所需的业务维度信息
     * @return 匹配的扩展实现实例，不会返回null
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws IllegalStateException 当找不到匹配的扩展实现时抛出
     */
    <C> C locateExtensionProvider(@NonNull Class<C> targetInterface, @NonNull BizContext<?> bizContext);
}
