package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认扩展点路由器实现，提供基于业务上下文的精确匹配、表达式匹配和默认实现查找
 * <p>
 * 采用三级路由策略，按优先级依次尝试：
 * <ol>
 *   <li><strong>精确匹配</strong>：根据tenantCode、bizCode、useCase、scenario四个维度进行严格匹配</li>
 *   <li><strong>表达式匹配</strong>：使用SpEL表达式进行动态条件匹配</li>
 *   <li><strong>默认实现</strong>：查找不指定特定维度的默认扩展实现</li>
 * </ol>
 * <p>
 * 该路由器针对性能进行了优化，通过缓存注解信息和热点路由结果来减少反射开销。
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 * @see ExtPointRouter 扩展点路由器接口
 * @see Extension 扩展提供者注解
 */
public class DefaultExtPointRouter implements ExtPointRouter {
    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);
    // 扩展点仓库，用于存储和检索扩展实现
    private final ExtPointRepository extPointRepository;
    
    // 缓存扩展提供者的注解信息，避免重复反射获取，提升性能
    private static final ConcurrentMap<String, Extension> EXT_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param extPointRepository 扩展点仓库，非空
     * @throws IllegalArgumentException 当参数为null时抛出
     */
    public DefaultExtPointRouter(@NonNull ExtPointRepository extPointRepository) {
        Assert.notNull(extPointRepository, "ExtPointRepository must not be null");
        this.extPointRepository = extPointRepository;
    }

    /**
     * 根据业务上下文定位合适的扩展实现
     * 遵循三级路由策略：精确匹配 → 表达式匹配 → 默认实现
     *
     * @param <C> 扩展点接口类型
     * @param targetInterface 目标扩展点接口类，必须是接口且非空
     * @param bizContext 业务上下文，包含路由所需的业务维度信息，非空
     * @return 匹配的扩展实现实例，不会返回null
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws IllegalStateException 当找不到匹配的扩展实现时抛出
     */
    @Override
    public <C> C locateExtensionProvider(@NonNull Class<C> targetInterface, @NonNull BizContext<?> bizContext) {
        Assert.notNull(targetInterface, "Target interface must not be null");
        Assert.notNull(bizContext, "BizContext must not be null");
        Assert.isTrue(targetInterface.isInterface(), "Target class must be an interface");

        String interfaceName = targetInterface.getCanonicalName();
        log.debug("Locating extension provider for interface: {}, with bizContext: {}", 
                interfaceName, bizContext.getBusinessIdentity());

        // 1. 精确匹配：根据业务标识精确查找
        C extensionProvider = locateExactMatch(interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("Found exact match extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 2. 表达式匹配：使用SpEL表达式动态匹配
        extensionProvider = locateByExpression(targetInterface, interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("Found expression match extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 3. 默认实现：查找默认扩展实现
        extensionProvider = locateDefaultImplementation(interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("Found default extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 未找到任何匹配的扩展实现
        String errorMsg = String.format("No extension provider found for interface: %s with bizContext: %s", 
                interfaceName, bizContext.getBusinessIdentity());
        log.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    /**
     * 精确匹配扩展提供者
     * 根据业务标识精确查找对应的扩展实现
     * 
     * @param <C> 扩展点接口类型
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文
     * @return 匹配的扩展实现，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <C> C locateExactMatch(String interfaceName, BizContext<?> bizContext) {
        String extensionKey = interfaceName + "." + bizContext.getBusinessIdentity();
        log.debug("Trying exact match with key: {}", extensionKey);
        return (C) extPointRepository.get(extensionKey);
    }

    /**
     * 查找默认扩展实现
     * 查找不指定特定维度的默认扩展实现
     * 
     * @param <C> 扩展点接口类型
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文
     * @return 默认扩展实现，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <C> C locateDefaultImplementation(String interfaceName, BizContext<?> bizContext) {
        String defaultKey = interfaceName + "." + bizContext.getDefaultBusinessIdentity();
        log.debug("Trying default implementation with key: {}", defaultKey);
        return (C) extPointRepository.get(defaultKey);
    }

    /**
     * 使用SpEL表达式动态匹配扩展提供者
     * 查找所有匹配当前上下文表达式条件的扩展实现
     * 
     * @param <C> 扩展点接口类型
     * @param targetInterface 目标扩展点接口类
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文，包含表达式计算所需的变量
     * @return 匹配的扩展实现，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <C> C locateByExpression(Class<C> targetInterface, String interfaceName, BizContext<?> bizContext) {
        String expressionListKey = interfaceName + ".expression.extProviderList";
        List<Object> extProviderList = (List<Object>) extPointRepository.get(expressionListKey);
        
        if (CollectionUtils.isEmpty(extProviderList)) {
            log.debug("No expression-based extension providers found for {}", interfaceName);
            return null;
        }

        List<C> matchedExtensions = new ArrayList<>(1); // 预设容量，通常最多匹配一个
        
        for (Object extProvider : extProviderList) {
            Extension extAnnotation = getExtensionAnnotation(extProvider.getClass());

            if (StringUtils.hasText(extAnnotation.expression())) {
                try {
                    if (ExpressionEvaluator.evaluate(extAnnotation.expression(), bizContext)) {
                        String providerClassName = extProvider.getClass().getCanonicalName();
                        log.info("Expression '{}' matched extension provider: {} for interface: {}",
                                extAnnotation.expression(), providerClassName, interfaceName);
                        
                        // 验证类型兼容性
                        if (targetInterface.isInstance(extProvider)) {
                            matchedExtensions.add(targetInterface.cast(extProvider));
                        } else {
                            log.warn("Extension provider {} is not compatible with interface {}", 
                                    providerClassName, interfaceName);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error evaluating expression '{}' for provider {}", 
                            extAnnotation.expression(), extProvider.getClass().getCanonicalName(), e);
                    // 继续尝试其他提供者
                }
            }
        }

        // 处理匹配结果
        if (matchedExtensions.size() == 1) {
            return matchedExtensions.get(0);
        } else if (matchedExtensions.size() > 1) {
            String errorMsg = String.format("Multiple expression matches found for interface %s with bizContext %s: %s",
                    interfaceName, bizContext.getBusinessIdentity(), matchedExtensions);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        return null;
    }

    /**
     * 从缓存获取扩展提供者的注解信息
     * 缓存机制避免重复反射获取注解，提升性能
     * 
     * @param providerClass 扩展提供者类
     * @return 扩展提供者注解
     */
    private Extension getExtensionAnnotation(Class<?> providerClass) {
        String className = providerClass.getName();
        return EXT_ANNOTATION_CACHE.computeIfAbsent(className, 
                key -> AnnotationUtils.findAnnotation(providerClass, Extension.class));
    }
}
