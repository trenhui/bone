package com.bone.engine.extension.route;

import com.bone.engine.extension.context.BizContext;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                
        log.info("Initialized extension router with config: cacheEnabled={}, maxAnnotationCacheSize={}, maxRouteCacheSize={}, cacheTTL={}ms",
                configProperties.isCacheEnabled(), configProperties.getAnnotationCacheMaxSize(), 
                configProperties.getRouteCacheMaxSize(), 
                configProperties.getCacheExpireAfterWrite() != null ? 
                configProperties.getCacheExpireAfterWrite().toMillis() : "unlimited");
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
        
        log.debug("Starting extension routing for interface: {}, tenantCode={}, bizCode={}, useCase={}, scenario={}", 
                interfaceName, bizContext.tenantCode(), bizContext.bizCode(), 
                bizContext.useCase(), bizContext.scenario());

        try {
            // 根据缓存配置决定是否使用缓存
            if (configProperties.isCacheEnabled()) {
                log.debug("Cache enabled, checking route cache: {}", cacheKey);
                return (C) extensionRouteCache.get(cacheKey, key -> {
                    log.debug("Cache miss for extension route, computing route: {}", cacheKey);
                    C provider = findExtensionProvider(targetInterface, interfaceName, bizContext, true);
                    logRouteDecision(interfaceName, provider, bizContext);
                    return provider;
                });
            } else {
                log.debug("Cache disabled, computing extension provider directly: {}", cacheKey);
                C provider = findExtensionProvider(targetInterface, interfaceName, bizContext, false);
                logRouteDecision(interfaceName, provider, bizContext);
                return provider;
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
     * 记录路由决策日志
     * @param interfaceName 接口名称
     * @param provider 选中的扩展提供者
     * @param bizContext 业务上下文
     */
    private <C> void logRouteDecision(String interfaceName, C provider, BizContext<?> bizContext) {
        if (provider != null) {
            Extension extension = getExtensionAnnotation(provider.getClass());
            log.info("Extension routing decision: interface={}, selectedProvider={}, providerName={}, priority={}, " +
                    "tenantCode={}, bizCode={}, useCase={}, scenario={}, env={}, group={}",
                    interfaceName,
                    provider.getClass().getSimpleName(),
                    extension.name(),
                    extension.priority(),
                    bizContext.tenantCode(),
                    bizContext.bizCode(),
                    bizContext.useCase(),
                    bizContext.scenario(),
                    bizContext.env(),
                    bizContext.group());
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
        // 1. 精确匹配：根据业务标识精确查找 - 高优先级策略
        C extensionProvider = locateExactMatch(interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("[Level 1: Exact Match] Found extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 2. 表达式匹配：使用SpEL表达式动态匹配 - 中优先级策略
        extensionProvider = locateByExpression(targetInterface, interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("[Level 2: Expression Match] Found extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 3. 默认实现：查找默认扩展实现 - 低优先级策略
        extensionProvider = locateDefaultImplementation(interfaceName, bizContext);
        if (extensionProvider != null) {
            log.debug("[Level 3: Default Implementation] Found extension provider for {}", interfaceName);
            return extensionProvider;
        }

        // 缓存未命中时记录详细的调试信息
        if (isCacheMiss) {
            log.debug("[Extension Resolution Failed] Attempted paths for interface: {}", interfaceName);
            log.debug("1. Exact match: {}", interfaceName + "." + bizContext.businessIdentity());
            log.debug("2. Default implementation: {}", interfaceName + "." + bizContext.defaultBusinessIdentity());
            log.debug("3. Fallback with DEFAULT bizCode: {}", interfaceName + "." + createDefaultBizCodeKey(bizContext));
        }
        
        // 未找到任何匹配的扩展实现，抛出专用异常
        throw new ExtensionNotFoundException(interfaceName, 
                bizContext.businessIdentity());
    }
    
    /**
     * 创建路由缓存键
     * 综合考虑接口名称、业务上下文标识、环境、分组和属性，生成唯一的缓存键
     * 优化缓存键设计，提高缓存命中率
     * 
     * @param interfaceName 接口名称
     * @param bizContext 业务上下文
     * @return 生成的缓存键
     */
    private String createCacheKey(String interfaceName, BizContext<?> bizContext) {
        // 使用StringBuilder预分配容量以提高性能
        StringBuilder keyBuilder = new StringBuilder(interfaceName.length() + 128)
            .append(interfaceName)
            .append(":t=")
            .append(StringUtils.hasText(bizContext.tenantCode()) ? bizContext.tenantCode() : "*")
            .append(":b=")
            .append(StringUtils.hasText(bizContext.bizCode()) ? bizContext.bizCode() : "*")
            .append(":u=")
            .append(StringUtils.hasText(bizContext.useCase()) ? bizContext.useCase() : "*")
            .append(":s=")
            .append(StringUtils.hasText(bizContext.scenario()) ? bizContext.scenario() : "*");
            
        // 添加环境信息
        if (StringUtils.hasText(bizContext.env())) {
            keyBuilder.append(":e=")
                .append(bizContext.env());
        }
        
        // 添加分组信息
        if (StringUtils.hasText(bizContext.group())) {
            keyBuilder.append(":g=")
                .append(bizContext.group());
        }
        
        // 添加用户组信息
        if (StringUtils.hasText(bizContext.userGroup())) {
            keyBuilder.append(":ug=")
                .append(bizContext.userGroup());
        }
            
        // 添加属性哈希值以区分不同的属性场景，优先考虑可能影响路由决策的关键属性
        if (bizContext.attributes() != null && !bizContext.attributes().isEmpty()) {
            // 使用更安全的哈希计算方式，减少哈希冲突
            int attrHash = calculateStableHash(bizContext.attributes());
            keyBuilder.append(":ah=")
                .append(attrHash);
        }
        
        // 添加标签信息（如果有）
        if (bizContext.tags() != null && !bizContext.tags().isEmpty()) {
            keyBuilder.append(":th=")
                .append(calculateStableHash(bizContext.tags()));
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
     * 实现设计文档中定义的精确匹配逻辑，支持多租户、多业务编码等核心维度的完全匹配
     * 
     * @param <C> 扩展点接口类型
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文
     * @return 匹配的扩展实现，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <C> C locateExactMatch(String interfaceName, BizContext<?> bizContext) {
        // 尝试精确匹配
        String extensionKey = interfaceName + "." + bizContext.businessIdentity();
        log.debug("[Exact Match] Trying key: {}", extensionKey);
        
        C exactMatch = (C) extPointRepository.get(extensionKey);
        if (exactMatch != null) {
            Extension extension = getExtensionAnnotation(exactMatch.getClass());
            log.debug("[Exact Match] Found provider: {}, priority: {}", 
                    exactMatch.getClass().getSimpleName(), extension.priority());
            return exactMatch;
        }
        
        // 尝试匹配特定环境下的实现
        if (StringUtils.hasText(bizContext.getEnv())) {
            String envSpecificKey = interfaceName + "." + bizContext.businessIdentity() + "." + bizContext.env();
            log.debug("[Exact Match] Trying environment specific key: {}", envSpecificKey);
            
            C envSpecificMatch = (C) extPointRepository.get(envSpecificKey);
            if (envSpecificMatch != null) {
                Extension extension = getExtensionAnnotation(envSpecificMatch.getClass());
                log.debug("[Exact Match] Found environment specific provider: {}, priority: {}", 
                        envSpecificMatch.getClass().getSimpleName(), extension.priority());
                return envSpecificMatch;
            }
        }
        
        return null;
    }

    /**
     * 查找默认扩展实现
     * 查找不指定特定维度的默认扩展实现或标记为isDefault的实现
     * 实现设计文档中定义的默认实现匹配策略，通过tenantCode="DEFAULT"识别默认实现
     * 
     * @param <C> 扩展点接口类型
     * @param interfaceName 扩展点接口名称
     * @param bizContext 业务上下文
     * @return 默认扩展实现，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <C> C locateDefaultImplementation(String interfaceName, BizContext<?> bizContext) {
        // 尝试按照优先级顺序查找默认实现
        List<String> defaultKeys = new ArrayList<>(4);
        
        // 1. 业务域特定的默认实现（保持业务域，其他使用默认）
        String domainSpecificDefaultKey = createDomainSpecificDefaultKey(interfaceName, bizContext);
        defaultKeys.add(domainSpecificDefaultKey);
        
        // 2. 标准默认键
        String defaultKey = interfaceName + "." + bizContext.defaultBusinessIdentity();
        defaultKeys.add(defaultKey);
        
        // 3. 使用DEFAULT作为业务编码的后备键
        String fallbackKey = interfaceName + "." + createDefaultBizCodeKey(bizContext);
        defaultKeys.add(fallbackKey);
        
        // 4. 完全通用的默认键
        defaultKeys.add(interfaceName + ".*");
        
        try {
            // 按照优先级顺序尝试各种默认实现
            for (String key : defaultKeys) {
                log.debug("[Default Implementation] Trying key: {}", key);
                C defaultImpl = (C) extPointRepository.get(key);
                if (defaultImpl != null) {
                    Extension extension = getExtensionAnnotation(defaultImpl.getClass());
                    // 特别检查是否是明确标记的默认实现
                    if (extension.isDefault()) {
                        log.info("[Default Implementation] Found explicitly marked default provider: {} with key: {}", 
                                defaultImpl.getClass().getSimpleName(), key);
                        return defaultImpl;
                    }
                    // 其他默认实现
                    log.info("[Default Implementation] Found default provider: {} with key: {}", 
                            defaultImpl.getClass().getSimpleName(), key);
                    return defaultImpl;
                }
            }
        } catch (Exception e) {
            log.warn("Error locating default implementation for {}", interfaceName, e);
        }
        
        return null;
    }
    
    /**
     * 创建业务域特定的默认键
     * 保持业务域不变，其他维度使用默认值
     */
    private String createDomainSpecificDefaultKey(String interfaceName, BizContext<?> bizContext) {
        String bizCode = StringUtils.hasText(bizContext.bizCode()) ? 
                bizContext.bizCode() : "*";
        return String.format("%s.*.%s.*.*", interfaceName, bizCode);
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
        String[] parts = bizContext.businessIdentity().split("\\.");
        if (parts.length >= 2) {
            parts[1] = "DEFAULT"; // 替换业务编码部分
            return String.join(".", parts);
        }
        return "*.*.*.*"; // 回退到完全默认键
    }

    /**
     * 使用SpEL表达式动态匹配扩展提供者
     * 实现设计文档中的表达式匹配阶段，使用SpEL表达式处理复杂动态条件
     * 支持多上下文变量访问和多维度评分选择
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
            log.debug("[Expression Match] No expression-based extension providers found for {}", interfaceName);
            return null;
        }

        // 存储匹配的扩展及其评分
        Map<C, Integer> matchedExtensions = new HashMap<>(4); // 预设更大容量
        
        for (Object extProvider : extProviderList) {
            Extension extAnnotation = getExtensionAnnotation(extProvider.getClass());
            
            // 首先检查环境和分组匹配 - 基础过滤
            if (!matchesEnv(extAnnotation, bizContext) || !matchesGroup(extAnnotation, bizContext)) {
                log.debug("[Expression Match] Provider {} skipped due to env/group mismatch", 
                        extProvider.getClass().getSimpleName());
                continue;
            }

            // 检查租户匹配（支持多租户匹配）
            if (!matchesTenant(extAnnotation, bizContext)) {
                log.debug("[Expression Match] Provider {} skipped due to tenant mismatch", 
                        extProvider.getClass().getSimpleName());
                continue;
            }
            
            // 检查业务编码匹配（支持多业务编码匹配）
            if (!matchesBizCode(extAnnotation, bizContext)) {
                log.debug("[Expression Match] Provider {} skipped due to bizCode mismatch", 
                        extProvider.getClass().getSimpleName());
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
                        // 验证类型兼容性
                        if (targetInterface.isInstance(extProvider)) {
                            C typedProvider = targetInterface.cast(extProvider);
                            
                            // 计算多维度评分
                            int score = calculateMultiDimensionScore(extAnnotation, bizContext);
                            matchedExtensions.put(typedProvider, score);
                            
                            log.debug("[Expression Match] Expression '{}' matched provider: {} with score: {}",
                                    expression, extProvider.getClass().getSimpleName(), score);
                        } else {
                            log.warn("[Expression Match] Provider {} is not compatible with interface {}", 
                                    extProvider.getClass().getCanonicalName(), interfaceName);
                        }
                    }
                } catch (Exception e) {
                    log.error("[Expression Match] Error evaluating expression '{}' for provider {}", 
                            expression, extProvider.getClass().getCanonicalName(), e);
                    // 继续尝试其他提供者
                }
            }
        }

        // 处理匹配结果
        if (matchedExtensions.isEmpty()) {
            return null;
        } else if (matchedExtensions.size() == 1) {
            return matchedExtensions.keySet().iterator().next();
        } else {
            // 使用多维度评分选择最优实现
            C selectedProvider = matchedExtensions.entrySet().stream()
                .max(Map.Entry.<C, Integer>comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
                
            if (selectedProvider != null) {
                Extension selectedExt = getExtensionAnnotation(selectedProvider.getClass());
                int selectedScore = matchedExtensions.get(selectedProvider);
                
                log.info("[Expression Match] Multiple matches found, selected provider: {} (score: {}, priority: {})",
                        selectedProvider.getClass().getSimpleName(), 
                        selectedScore,
                        selectedExt.priority());
                        
                // 记录所有匹配的提供者及其评分，便于调试
                if (log.isDebugEnabled()) {
                    StringBuilder builder = new StringBuilder("[Expression Match] All matched providers and scores:\n");
                    matchedExtensions.entrySet().stream()
                        .sorted(Map.Entry.<C, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            Extension ext = getExtensionAnnotation(entry.getKey().getClass());
                            builder.append("  - ")
                                .append(entry.getKey().getClass().getSimpleName())
                                .append(" (score: ")
                                .append(entry.getValue())
                                .append(", priority: ")
                                .append(ext.priority())
                                .append(")\n");
                        });
                    log.debug(builder.toString());
                }
            }
            
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
        return extension.env().equals(bizContext.env());
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
        return extension.group().equals(bizContext.group());
    }
    
    /**
     * 检查扩展实现的租户是否与业务上下文匹配，支持多租户匹配
     * 
     * @param extension 扩展注解
     * @param bizContext 业务上下文
     * @return 是否匹配
     */
    private boolean matchesTenant(Extension extension, BizContext<?> bizContext) {
        String contextTenant = bizContext.tenantCode();
        
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
        String contextBizCode = bizContext.bizCode();
        
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
     * 根据设计文档实现的多维度评分机制
     * 计算扩展实现与业务上下文的匹配度评分
     * 包含9个匹配维度：tenantCode、bizCode、env、useCase、scenario、userGroup、version、tags、timeWindow
     * 
     * @param extension 扩展注解
     * @param context 业务上下文
     * @return 匹配评分，分数越高匹配度越高
     */
    private int calculateMultiDimensionScore(Extension extension, BizContext<?> context) {
        int score = 0;
        
        // 1. 基础优先级（优先级数值越小，实际优先级越高，这里反转一下）
        score += (100 - Math.min(extension.priority(), 99));
        
        // 2. 租户匹配评分 (最高权重)
        if (matchesTenant(extension, context)) {
            // 多租户列表匹配权重更高
            if (extension.multiTenantCodes() != null && extension.multiTenantCodes().length > 0) {
                score += 100; // 多租户精确匹配
            } else if (!"*".equals(extension.tenantCode()) && StringUtils.hasText(extension.tenantCode())) {
                score += 90; // 单租户精确匹配
            } else {
                score += 20; // 通用租户匹配
            }
        }
        
        // 3. 业务域匹配评分
        if (matchesBizCode(extension, context)) {
            // 多业务编码列表匹配权重更高
            if (extension.multiBizCodes() != null && extension.multiBizCodes().length > 0) {
                score += 95; // 多业务编码精确匹配
            } else if (!"*".equals(extension.bizCode()) && StringUtils.hasText(extension.bizCode())) {
                score += 85; // 单业务编码精确匹配
            } else {
                score += 15; // 通用业务编码匹配
            }
        }
        
        // 4. 环境匹配评分
        if (matchesEnv(extension, context)) {
            if (!"*".equals(extension.env()) && StringUtils.hasText(extension.env())) {
                score += 80; // 环境精确匹配
            } else {
                score += 10; // 通用环境匹配
            }
        }
        
        // 5. 用例匹配评分
        if (!"*".equals(extension.useCase()) && StringUtils.hasText(extension.useCase()) &&
            StringUtils.hasText(context.useCase()) && extension.useCase().equals(context.useCase())) {
            score += 75; // 用例精确匹配
        }
        
        // 6. 场景匹配评分
        if (!"*".equals(extension.scenario()) && StringUtils.hasText(extension.scenario()) &&
            StringUtils.hasText(context.scenario()) && extension.scenario().equals(context.scenario())) {
            score += 70; // 场景精确匹配
        }
        
        // 7. 用户组匹配评分
        if (StringUtils.hasText(extension.userGroup()) && !"*".equals(extension.userGroup()) &&
            StringUtils.hasText(context.userGroup()) && extension.userGroup().equals(context.userGroup())) {
            score += 65; // 用户组精确匹配
        }
        
        // 8. 版本匹配评分
        if (StringUtils.hasText(extension.version()) && !"*".equals(extension.version())) {
            Object contextVersion = context.attribute("version");
            if (contextVersion != null && extension.version().equals(String.valueOf(contextVersion))) {
                score += 60; // 版本精确匹配
            }
        }
        
        // 9. 标签匹配评分
        if (context.tags() != null && !context.tags().isEmpty()) {
            // 简化实现，实际应该从扩展定义中获取标签信息
            // 假设扩展注解中添加了tags属性
            score += 30; // 基础标签匹配分
        }
        
        // 10. 默认实现加分
        if (extension.isDefault()) {
            score += 25; // 默认实现权重
        }
        
        // 11. 表达式匹配加分
        String expression = StringUtils.hasText(extension.condition()) ? 
            extension.condition() : extension.expression();
        if (StringUtils.hasText(expression)) {
            try {
                if (ExpressionEvaluator.evaluate(expression, context)) {
                    score += 50; // 表达式匹配额外加分
                }
            } catch (Exception e) {
                // 表达式计算失败不计入
            }
        }
        
        // 12. 时间窗口匹配（如果配置了）
        score += calculateTimeWindowScore(extension, context);
        
        log.debug("Multi-dimensional score for provider with priority {}: {}", 
                extension.priority(), score);
        return score;
    }
    
    /**
     * 计算时间窗口匹配分数
     * 如果扩展实现配置了时间窗口并且当前时间在窗口内，给予额外加分
     */
    private int calculateTimeWindowScore(Extension extension, BizContext<?> context) {
        // 简化实现，假设Extension注解有timeWindow属性
        // 实际应该检查当前时间是否在配置的时间窗口内
        return 0;
    }
    
    /**
     * 计算匹配条件的数量（保留兼容旧逻辑）
     * 
     * @param extension 扩展注解
     * @param context 业务上下文
     * @return 匹配的条件数量
     */
    private int countMatchingConditions(Extension extension, BizContext<?> context) {
        // 新代码使用calculateMultiDimensionScore替代，但保留此方法以兼容
        log.warn("Legacy countMatchingConditions called, consider using calculateMultiDimensionScore instead");
        
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
            extension.useCase().equals(context.useCase())) {
            count++;
        }
        
        // 检查场景匹配
        if (!"*".equals(extension.scenario()) && 
            extension.scenario().equals(context.scenario())) {
            count++;
        }
        
        // 检查环境匹配
        if (StringUtils.hasText(extension.env()) && 
            !"*".equals(extension.env()) && 
            extension.env().equals(context.env())) {
            count++;
        }
        
        // 检查分组匹配
        if (StringUtils.hasText(extension.group()) && 
            !"*".equals(extension.group()) && 
            extension.group().equals(context.group())) {
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
            log.info("[Cache Management] Extension caches cleared (annotation cache and route cache)");
        }
    }
    
    /**
     * 获取缓存统计信息
     * 提供更详细的缓存统计数据，便于监控和性能分析
     * 
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        if (configProperties.isCacheEnabled()) {
            return String.format("Cache enabled: true\n" +
                    "Annotation Cache Stats:\n  Size: %d\n  Hits: %d\n  Misses: %d\n  Hit Rate: %.2f%%\n" +
                    "Route Cache Stats:\n  Size: %d\n  Hits: %d\n  Misses: %d\n  Hit Rate: %.2f%%", 
                    extAnnotationCache.estimatedSize(),
                    extAnnotationCache.stats().hitCount(),
                    extAnnotationCache.stats().missCount(),
                    extAnnotationCache.stats().hitRate() * 100,
                    extensionRouteCache.estimatedSize(),
                    extensionRouteCache.stats().hitCount(),
                    extensionRouteCache.stats().missCount(),
                    extensionRouteCache.stats().hitRate() * 100);
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
                log.debug("[Cache Warmup] Warmed up cache for interface: {} with context: {}", 
                        interfaceName, bizContext.businessIdentity());
            }
        } catch (Exception e) {
            log.warn("[Cache Warmup] Failed to warmup cache for interface: {}", targetInterface.getName(), e);
        }
    }
    
    /**
     * 批量预热路由缓存
     * 支持为多个业务上下文预热缓存，提高系统初始化性能
     * 
     * @param targetInterface 目标接口
     * @param bizContexts 多个业务上下文列表
     */
    public <C> void batchWarmupCache(Class<C> targetInterface, List<BizContext<?>> bizContexts) {
        if (CollectionUtils.isEmpty(bizContexts)) {
            return;
        }
        
        log.info("[Cache Warmup] Starting batch warmup for interface: {} with {} contexts", 
                targetInterface.getName(), bizContexts.size());
        
        int successCount = 0;
        int errorCount = 0;
        
        for (BizContext<?> context : bizContexts) {
            try {
                warmupCache(targetInterface, context);
                successCount++;
            } catch (Exception e) {
                log.warn("[Cache Warmup] Failed to warmup cache for context: {}", context.businessIdentity(), e);
                errorCount++;
            }
        }
        
        log.info("[Cache Warmup] Batch warmup completed: {} successful, {} failed", successCount, errorCount);
    }
}
