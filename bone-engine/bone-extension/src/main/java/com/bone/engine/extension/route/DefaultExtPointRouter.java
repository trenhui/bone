package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtProvider;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认扩展点路由器，根据业务上下文定位合适的扩展实现
 *
 * @author renhui.trh 2023-11-1
 */
@Slf4j
public class DefaultExtPointRouter implements ExtPointRouter {
    private final ExtPointRepository extProviderRepo;
    // 缓存扩展提供者的注解信息，避免重复反射获取
    private static final ConcurrentMap<String, ExtProvider> EXT_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    public DefaultExtPointRouter(ExtPointRepository extProviderRepo) {
        Assert.notNull(extProviderRepo, "ExtPointRepository must not be null");
        this.extProviderRepo = extProviderRepo;
    }

    /**
     * 根据业务上下文定位合适的扩展提供者
     * 定位策略：
     * 1. 精确匹配：根据tenantCode、bizCode、useCase、scenario四个维度查找
     * 2. 表达式匹配：使用SpEL表达式进行动态匹配
     * 3. 默认实现：查找默认的扩展实现
     *
     * @param targetClz 目标扩展点接口类
     * @param bizContext 业务上下文
     * @return 匹配的扩展提供者实现
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws IllegalStateException 当找不到匹配的扩展实现时抛出
     */
    @Override
    public <C> C locateExtProvider(Class<C> targetClz, BizContext bizContext) {
        Assert.notNull(targetClz, "Target class must not be null");
        Assert.notNull(bizContext, "BizContext must not be null");
        Assert.isTrue(targetClz.isInterface(), "Target class must be an interface");

        String interfaceName = targetClz.getCanonicalName();
        log.debug("Locating extension provider for interface: {}, with bizContext: {}", 
                interfaceName, bizContext.getBizIdentity());

        // 1. 精确匹配：根据业务标识精确查找
        C extProvider = locateExactMatch(interfaceName, bizContext);
        if (extProvider != null) {
            log.debug("Found exact match extension provider for {}", interfaceName);
            return extProvider;
        }

        // 2. 表达式匹配：使用SpEL表达式动态匹配
        extProvider = locateByExpression(targetClz, interfaceName, bizContext);
        if (extProvider != null) {
            log.debug("Found expression match extension provider for {}", interfaceName);
            return extProvider;
        }

        // 3. 默认实现：查找默认扩展实现
        extProvider = locateDefaultImplementation(interfaceName, bizContext);
        if (extProvider != null) {
            log.debug("Found default extension provider for {}", interfaceName);
            return extProvider;
        }

        // 未找到任何匹配的扩展实现
        String errorMsg = String.format("No extension provider found for interface: %s with bizContext: %s", 
                interfaceName, bizContext.getBizIdentity());
        log.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    /**
     * 精确匹配扩展提供者
     */
    private <C> C locateExactMatch(String interfaceName, BizContext bizContext) {
        String extProviderKey = interfaceName + "." + bizContext.getBizIdentity();
        log.debug("Trying exact match with key: {}", extProviderKey);
        return locate(extProviderKey);
    }

    /**
     * 查找默认扩展实现
     */
    private <C> C locateDefaultImplementation(String interfaceName, BizContext bizContext) {
        String defaultKey = interfaceName + "." + bizContext.getDefaultBizIdentity();
        log.debug("Trying default implementation with key: {}", defaultKey);
        return locate(defaultKey);
    }

    /**
     * 从仓库中获取扩展提供者
     */
    @SuppressWarnings("unchecked")
    private <C> C locate(String extProviderKey) {
        return (C) extProviderRepo.get(extProviderKey);
    }

    /**
     * 根据表达式匹配扩展提供者
     */
    private <C> C locateByExpression(Class<C> targetClz, String interfaceName, BizContext bizContext) {
        String expressionListKey = interfaceName + ".expression.extProviderList";
        List<Object> extProviderList = (List<Object>) extProviderRepo.get(expressionListKey);
        
        if (CollectionUtils.isEmpty(extProviderList)) {
            log.debug("No expression-based extension providers found for {}", interfaceName);
            return null;
        }

        List<C> matchedExtProviders = new ArrayList<>(1); // 预设容量，通常最多匹配一个
        
        for (Object extProvider : extProviderList) {
            ExtProvider extAnnotation = EXT_ANNOTATION_CACHE.computeIfAbsent(
                    extProvider.getClass().getCanonicalName(),
                    k -> AnnotationUtils.findAnnotation(extProvider.getClass(), ExtProvider.class)
            );

            if (StringUtils.hasText(extAnnotation.expression())) {
                try {
                    if (ExpressionEvaluator.evaluate(extAnnotation.expression(), bizContext)) {
                        String providerClassName = extProvider.getClass().getCanonicalName();
                        log.info("Expression '{}' matched extension provider: {} for interface: {}",
                                extAnnotation.expression(), providerClassName, interfaceName);
                        
                        // 验证类型兼容性
                        if (targetClz.isInstance(extProvider)) {
                            matchedExtProviders.add(targetClz.cast(extProvider));
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
        if (matchedExtProviders.size() == 1) {
            return matchedExtProviders.get(0);
        } else if (matchedExtProviders.size() > 1) {
            String errorMsg = String.format("Multiple expression matches found for interface %s with bizContext %s: %s",
                    interfaceName, bizContext.getBizIdentity(), matchedExtProviders);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        return null;
    }
}
