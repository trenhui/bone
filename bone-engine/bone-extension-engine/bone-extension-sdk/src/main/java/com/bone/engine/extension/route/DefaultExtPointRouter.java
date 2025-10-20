package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.exception.ExtensionNotFoundException;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.bone.engine.extension.config.ExtensionConfigProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    
    private final ExtensionConfigProperties configProperties;
    
    // 缓存扩展提供者的注解信息，避免重复反射获取，提升性能
    private final Cache<String, Extension> extAnnotationCache;
    
    // 缓存路由结果，避免重复计算路由路径，显著提升性能
    private final Cache<String, Object> extensionRouteCache;

    /**
     * 构造函数
     * 
     * @param extPointRepository 扩展点仓库，非空
     * @throws IllegalArgumentException 当参数为null时抛出
     */
    public DefaultExtPointRouter(@NonNull ExtPointRepository extPointRepository, 
                               ExtensionConfigProperties configProperties) {
        Assert.notNull(extPointRepository, "ExtPointRepository must not be null");
        Assert.notNull(configProperties, "ExtensionConfigProperties must not be null");
        this.extPointRepository = extPointRepository;
        this.configProperties = configProperties;
        
        // 初始化缓存，使用配置属性
        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder()
                .maximumSize(configProperties.getAnnotationCacheMaxSize())
                .expireAfterWrite(configProperties.getCacheExpireAfterWrite())
                .recordStats();
                
        // 如果配置了刷新时间，则添加刷新策略
        if (configProperties.getCacheRefreshAfterWrite() != null) {
            cacheBuilder.refreshAfterWrite(configProperties.getCacheRefreshAfterWrite());
        }
                
        this.extAnnotationCache = cacheBuilder.build();
                
        // 路由缓存使用相同的构建器但设置不同的最大大小
        this.extensionRouteCache = Caffeine.newBuilder()
                .maximumSize(configProperties.getRouteCacheMaxSize())
                .expireAfterWrite(configProperties.getCacheExpireAfterWrite())
                .recordStats()
                .build();
                
        log.info("Initialized extension router with config: cacheEnabled={}, maxAnnotationCacheSize={}, maxRouteCacheSize={}",
                configProperties.isCacheEnabled(), configProperties.getAnnotationCacheMaxSize(), 
                configProperties.getRouteCacheMaxSize());
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
    @SuppressWarnings("unchecked")
    public <C> C locateExtensionProvider(@NonNull Class<C> targetInterface, @NonNull BizContext<?> bizContext) {
        Assert.notNull(targetInterface, "Target interface must not be null");
        Assert.notNull(bizContext, "BizContext must not be null");
        Assert.isTrue(targetInterface.isInterface(), "Target class must be an interface");

        String interfaceName = targetInterface.getCanonicalName();
        String cacheKey = createCacheKey(interfaceName, bizContext);
        
        log.debug("Locating extension provider for interface: {}, with bizContext: {}", 
                interfaceName, bizContext.getBusinessIdentity());

        try {
            // 根据缓存配置决定是否使用缓存
            if (configProperties.isCacheEnabled()) {
                log.debug("Cache enabled, using cached route if available: {}", cacheKey);
                return (C) extensionRouteCache.get(cacheKey, key -> {
                    log.debug("Cache miss for extension route: {}", cacheKey);
                    return findExtensionProvider(targetInterface, interfaceName, bizContext, true);
                });
            } else {
                log.debug("Cache disabled, computing extension provider directly: {}", cacheKey);
                return findExtensionProvider(targetInterface, interfaceName, bizContext, false);
            }
        } catch (Exception e) {
            // 处理异常，确保异常信息准确传递
            if (e instanceof ExtensionNotFoundException) {
                throw e;
            }
            // 包装其他异常为ExtensionNotFoundException
            throw new ExtensionNotFoundException(interfaceName, 
                    bizContext.getBusinessIdentity(), e);
        }
    }
    
    /**
     * 查找扩展提供者的核心方法，包含完整的路由逻辑
     * 
     * @param targetInterface 目标扩展点接口类
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文
     * @param isCacheMiss 是否是缓存未命中的情况
     * @return 匹配的扩展实现
     * @throws IllegalStateException 当找不到匹配的扩展实现时抛出
     */
    @SuppressWarnings("unchecked")
    private <C> C findExtensionProvider(Class<C> targetInterface, String interfaceName, 
                                      BizContext<?> bizContext, boolean isCacheMiss) {
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

        // 缓存未命中时记录详细的调试信息
        if (isCacheMiss) {
            log.debug("Attempted extension lookup paths:");
            log.debug("1. Exact match: {}", interfaceName + "." + bizContext.getBusinessIdentity());
            log.debug("2. Default implementation: {}", interfaceName + "." + bizContext.getDefaultBusinessIdentity());
            log.debug("3. Fallback with DEFAULT bizCode: {}", interfaceName + "." + createDefaultBizCodeKey(bizContext));
        }
        
        // 未找到任何匹配的扩展实现，抛出专用异常
        throw new ExtensionNotFoundException(interfaceName, 
                bizContext.getBusinessIdentity());
    }
    
    /**
     * 创建路由缓存键
     * 综合考虑接口名称、业务上下文标识、环境、分组和属性，生成唯一的缓存键
     * 
     * @param interfaceName 接口名称
     * @param bizContext 业务上下文
     * @return 生成的缓存键
     */
    private String createCacheKey(String interfaceName, BizContext<?> bizContext) {
        // 使用StringBuilder预分配容量以提高性能
        StringBuilder keyBuilder = new StringBuilder(interfaceName.length() + 64)
            .append(interfaceName)
            .append(":")
            .append(bizContext.getBusinessIdentity());
            
        // 添加环境信息
        if (StringUtils.hasText(bizContext.getEnv())) {
            keyBuilder.append(":env:")
                .append(bizContext.getEnv());
        }
        
        // 添加分组信息
        if (StringUtils.hasText(bizContext.getGroup())) {
            keyBuilder.append(":group:")
                .append(bizContext.getGroup());
        }
            
        // 添加属性哈希值以区分不同的属性场景
        if (bizContext.getAttributes() != null && !bizContext.getAttributes().isEmpty()) {
            // 使用更安全的哈希计算方式，减少哈希冲突
            int attrHash = calculateStableHash(bizContext.getAttributes());
            keyBuilder.append(":attrHash:")
                .append(attrHash);
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 计算Map的稳定哈希值，减少哈希冲突
     */
    private int calculateStableHash(Map<String, Object> attributes) {
        // 计算基于键值对的稳定哈希值
        int result = 17;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            // 键和值的哈希码组合计算
            int keyHash = entry.getKey().hashCode();
            int valueHash = entry.getValue() != null ? entry.getValue().hashCode() : 0;
            
            // 结合键和值的哈希，使用31作为素数以减少冲突
            result = 31 * result + (keyHash ^ (valueHash >>> 16));
        }
        return result;
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
     * 查找不指定特定维度的默认扩展实现或标记为isDefault的实现
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
        
        try {
            // 尝试获取默认实现
            C defaultImpl = (C) extPointRepository.get(defaultKey);
            
            // 如果默认实现不存在，尝试查找bizCode为DEFAULT的实现
            if (defaultImpl == null) {
                String fallbackKey = interfaceName + "." + createDefaultBizCodeKey(bizContext);
                log.debug("Default implementation not found, trying fallback with key: {}", fallbackKey);
                defaultImpl = (C) extPointRepository.get(fallbackKey);
                
                if (defaultImpl != null) {
                    log.info("Found fallback implementation for {} with key: {}", interfaceName, fallbackKey);
                }
            }
            

            
            return defaultImpl;
        } catch (Exception e) {
            log.warn("Error locating default implementation for {}", interfaceName, e);
            return null;
        }
    }
    
    /**
     * 创建基于DEFAULT业务编码的键
     * 用于在没有精确匹配的默认实现时作为后备选项
     * 
     * @param bizContext 业务上下文
     * @return 构建的键值
     */
    private String createDefaultBizCodeKey(BizContext<?> bizContext) {
        // 构建格式为: "*.*.*.*" 但替换bizCode部分为"DEFAULT"
        String[] parts = bizContext.getBusinessIdentity().split("\\.");
        if (parts.length >= 2) {
            parts[1] = "DEFAULT"; // 替换业务编码部分
            return String.join(".", parts);
        }
        return "*.*.*.*"; // 回退到完全默认键
    }

    /**
     * 使用SpEL表达式动态匹配扩展提供者
     * 查找所有匹配当前上下文表达式条件的扩展实现，支持新的condition属性和环境/分组匹配
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

        List<C> matchedExtensions = new ArrayList<>(4); // 预设更大容量，支持多匹配情况
        
        for (Object extProvider : extProviderList) {
            Extension extAnnotation = getExtensionAnnotation(extProvider.getClass());
            
            // 首先检查环境和分组匹配
            if (!matchesEnv(extAnnotation, bizContext) || !matchesGroup(extAnnotation, bizContext)) {
                log.debug("Extension provider {} skipped due to env/group mismatch", 
                        extProvider.getClass().getCanonicalName());
                continue;
            }

            // 检查租户匹配（支持多租户匹配）
            if (!matchesTenant(extAnnotation, bizContext)) {
                log.debug("Extension provider {} skipped due to tenant mismatch", 
                        extProvider.getClass().getCanonicalName());
                continue;
            }
            
            // 检查业务编码匹配（支持多业务编码匹配）
            if (!matchesBizCode(extAnnotation, bizContext)) {
                log.debug("Extension provider {} skipped due to bizCode mismatch", 
                        extProvider.getClass().getCanonicalName());
                continue;
            }

            // 优先使用condition属性，兼容旧的expression属性
            String expression = extAnnotation.condition();
            if (!StringUtils.hasText(expression)) {
                expression = extAnnotation.expression(); // 兼容旧版本
            }
            
            if (StringUtils.hasText(expression)) {
                try {
                    if (ExpressionEvaluator.evaluate(expression, bizContext)) {
                        String providerClassName = extProvider.getClass().getCanonicalName();
                        log.info("Expression '{}' matched extension provider: {} for interface: {}",
                                expression, providerClassName, interfaceName);
                        
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
                            expression, extProvider.getClass().getCanonicalName(), e);
                    // 继续尝试其他提供者
                }
            }
        }

        // 处理匹配结果
        if (matchedExtensions.size() == 1) {
            return matchedExtensions.get(0);
        } else if (matchedExtensions.size() > 1) {
            // 使用优先级比较功能选择最优实现
            matchedExtensions.sort((p1, p2) -> compareProviderPriority(p1, p2, bizContext));
            C selectedProvider = matchedExtensions.get(0);
            log.info("Multiple expression matches found, selected provider with priority {}: {}",
                    getExtensionAnnotation(selectedProvider.getClass()).priority(), 
                    selectedProvider.getClass().getCanonicalName());
            return selectedProvider;
        }

        return null;
    }
    
    /**
     * 检查扩展实现的环境是否与业务上下文匹配
     * 
     * @param extension 扩展注解
     * @param bizContext 业务上下文
     * @return 是否匹配
     */
    private boolean matchesEnv(Extension extension, BizContext<?> bizContext) {
        // 如果扩展未指定环境或环境为默认值，则匹配所有环境
        if (!StringUtils.hasText(extension.env()) || "*".equals(extension.env())) {
            return true;
        }
        
        // 否则检查是否与业务上下文的环境匹配
        return extension.env().equals(bizContext.getEnv());
    }
    
    /**
     * 检查扩展实现的分组是否与业务上下文匹配
     * 
     * @param extension 扩展注解
     * @param bizContext 业务上下文
     * @return 是否匹配
     */
    private boolean matchesGroup(Extension extension, BizContext<?> bizContext) {
        // 如果扩展未指定分组或分组为默认值，则匹配所有分组
        if (!StringUtils.hasText(extension.group()) || "*".equals(extension.group())) {
            return true;
        }
        
        // 否则检查是否与业务上下文的分组匹配
        return extension.group().equals(bizContext.getGroup());
    }
    
    /**
     * 检查扩展实现的租户是否与业务上下文匹配，支持多租户匹配
     * 
     * @param extension 扩展注解
     * @param bizContext 业务上下文
     * @return 是否匹配
     */
    private boolean matchesTenant(Extension extension, BizContext<?> bizContext) {
        String contextTenant = bizContext.getTenantCode();
        
        // 优先检查多租户列表
        String[] multiTenantCodes = extension.multiTenantCodes();
        if (multiTenantCodes != null && multiTenantCodes.length > 0) {
            return Arrays.asList(multiTenantCodes).contains(contextTenant);
        }
        
        // 回退到单租户匹配
        String annotationTenant = extension.tenantCode();
        return "*".equals(annotationTenant) || annotationTenant.equals(contextTenant);
    }
    
    /**
     * 检查扩展实现的业务编码是否与业务上下文匹配，支持多业务编码匹配
     * 
     * @param extension 扩展注解
     * @param bizContext 业务上下文
     * @return 是否匹配
     */
    private boolean matchesBizCode(Extension extension, BizContext<?> bizContext) {
        String contextBizCode = bizContext.getBizCode();
        
        // 优先检查多业务编码列表
        String[] multiBizCodes = extension.multiBizCodes();
        if (multiBizCodes != null && multiBizCodes.length > 0) {
            return Arrays.asList(multiBizCodes).contains(contextBizCode);
        }
        
        // 回退到单业务编码匹配
        String annotationBizCode = extension.bizCode();
        return "*".equals(annotationBizCode) || annotationBizCode.equals(contextBizCode);
    }

    /**
     * 从缓存获取扩展提供者的注解信息
     * 缓存机制避免重复反射获取注解，提升性能
     * 
     * @param providerClass 扩展提供者类
     * @return 扩展提供者注解
     */
    /**
     * 从缓存获取扩展提供者的注解信息
     * 缓存机制避免重复反射获取注解，提升性能
     * 
     * @param providerClass 扩展提供者类
     * @return 扩展提供者注解
     * @throws IllegalStateException 如果类上没有@Extension注解
     */
    private Extension getExtensionAnnotation(Class<?> providerClass) {
        String className = providerClass.getName();
        Extension annotation;
        
        if (configProperties.isCacheEnabled()) {
            annotation = extAnnotationCache.get(className, 
                    key -> AnnotationUtils.findAnnotation(providerClass, Extension.class));
        } else {
            // 不使用缓存时直接查找
            annotation = AnnotationUtils.findAnnotation(providerClass, Extension.class);
        }
        
        if (annotation == null) {
            String errorMsg = String.format("Class %s is registered as extension provider but missing @Extension annotation", 
                    providerClass.getName());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        return annotation;
    }
    
    /**
     * 比较两个扩展实现的优先级
     * <p>
     * 优先比较@Extension注解中的priority属性，值越小优先级越高
     * 如果priority相同，则根据匹配的条件数量决定优先级
     * </p>
     * 
     * @param provider1 第一个扩展实现
     * @param provider2 第二个扩展实现
     * @param context 业务上下文
     * @return 比较结果，负数表示provider1优先级更高，正数表示provider2优先级更高，0表示优先级相同
     */
    private int compareProviderPriority(Object provider1, Object provider2, BizContext<?> context) {
        Extension ext1 = getExtensionAnnotation(provider1.getClass());
        Extension ext2 = getExtensionAnnotation(provider2.getClass());
        
        // 优先比较显式设置的优先级
        int priorityCompare = Integer.compare(ext1.priority(), ext2.priority());
        if (priorityCompare != 0) {
            return priorityCompare; // 值越小优先级越高
        }
        
        // 优先级相同时，比较匹配条件数量
        int matchCount1 = countMatchingConditions(ext1, context);
        int matchCount2 = countMatchingConditions(ext2, context);
        
        return Integer.compare(matchCount2, matchCount1); // 匹配条件越多优先级越高
    }
    
    /**
     * 计算匹配条件的数量
     * 
     * @param extension 扩展注解
     * @param context 业务上下文
     * @return 匹配的条件数量
     */
    private int countMatchingConditions(Extension extension, BizContext<?> context) {
        int count = 0;
        
        // 检查租户匹配（支持多租户）
        if (matchesTenant(extension, context)) {
            // 多租户列表优先于单租户
            if (extension.multiTenantCodes() != null && extension.multiTenantCodes().length > 0) {
                count += 2; // 多租户匹配权重更高
            } else if (!"*".equals(extension.tenantCode())) {
                count++;
            }
        }
        
        // 检查业务编码匹配（支持多业务编码）
        if (matchesBizCode(extension, context)) {
            // 多业务编码列表优先于单业务编码
            if (extension.multiBizCodes() != null && extension.multiBizCodes().length > 0) {
                count += 2; // 多业务编码匹配权重更高
            } else if (!"*".equals(extension.bizCode())) {
                count++;
            }
        }
        
        // 检查用例匹配
        if (!"*".equals(extension.useCase()) && 
            extension.useCase().equals(context.getUseCase())) {
            count++;
        }
        
        // 检查场景匹配
        if (!"*".equals(extension.scenario()) && 
            extension.scenario().equals(context.getScenario())) {
            count++;
        }
        
        // 检查环境匹配
        if (StringUtils.hasText(extension.env()) && 
            !"*".equals(extension.env()) && 
            extension.env().equals(context.getEnv())) {
            count++;
        }
        
        // 检查分组匹配
        if (StringUtils.hasText(extension.group()) && 
            !"*".equals(extension.group()) && 
            extension.group().equals(context.getGroup())) {
            count++;
        }
        
        // 检查条件表达式匹配（如果有条件表达式）
        String expression = StringUtils.hasText(extension.condition()) ? 
            extension.condition() : extension.expression();
        if (StringUtils.hasText(expression)) {
            try {
                if (ExpressionEvaluator.evaluate(expression, context)) {
                    count++;
                }
            } catch (Exception e) {
                // 表达式计算失败不计入匹配
                log.debug("Expression evaluation failed for condition: {}", expression);
            }
        }
        
        // 检查是否为默认实现
        if (extension.isDefault()) {
            count += 3; // 默认实现权重更高
        }
        
        return count;
    }
    
    /**
     * 清除缓存，重新加载扩展点信息
     */
    public void clearCache() {
        if (configProperties.isCacheEnabled()) {
            extAnnotationCache.invalidateAll();
            extensionRouteCache.invalidateAll();
            log.info("Extension caches cleared (annotation cache and route cache)");
        }
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        if (configProperties.isCacheEnabled()) {
            return String.format("Cache enabled: true\nAnnotation Cache Stats: %s\nRoute Cache Stats: %s", 
                    extAnnotationCache.stats().toString(),
                    extensionRouteCache.stats().toString());
        } else {
            return "Cache enabled: false";
        }
    }
    
    /**
     * 预热路由缓存，提高首次访问性能
     * @param targetInterface 目标接口
     * @param bizContext 业务上下文
     */
    public <C> void warmupCache(Class<C> targetInterface, BizContext<?> bizContext) {
        try {
            if (configProperties.isCacheEnabled()) {
                String interfaceName = targetInterface.getCanonicalName();
                String cacheKey = createCacheKey(interfaceName, bizContext);
                
                // 预先计算并缓存路由结果
                C provider = locateExtensionProvider(targetInterface, bizContext);
                extensionRouteCache.put(cacheKey, provider);
                log.debug("Warmed up cache for interface: {} with context: {}", 
                        interfaceName, bizContext.getBusinessIdentity());
            }
        } catch (Exception e) {
            log.warn("Failed to warmup cache for interface: {}", targetInterface.getName(), e);
        }
    }
}
