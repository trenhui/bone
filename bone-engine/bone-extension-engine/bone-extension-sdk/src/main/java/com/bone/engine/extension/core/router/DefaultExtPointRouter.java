package com.bone.engine.extension.core.router;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.support.config.ExtensionProperties;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.config.RouterConfiguration;
import com.bone.engine.extension.support.utils.ExtPointUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bone.engine.extension.core.lifecycle.ExtensionLifecycle;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import com.bone.engine.extension.support.config.ExtensionConfigManager;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import com.bone.engine.extension.monitor.ExtPointFrameworkEndpoint;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.bone.engine.extension.support.config.ConfigChangeListener;

/**
 * 默认扩展点路由器实现
 * <p>
 * 实现基于业务上下文的智能路由选择，支持多级匹配策略、缓存优化和动态表达式评估
 * <strong>核心功能：</strong>扩展点注册管理、智能路由匹配、缓存优化、表达式评估
 * </p>
 *
 * <h3>实现特性：</h3>
 * <ul>
 *   <li><strong>高级缓存：</strong>基于Caffeine实现高效缓存，支持自动过期、容量限制和统计</li>
 *   <li><strong>多级匹配：</strong>支持精确匹配、部分匹配和默认匹配</li>
 *   <li><strong>动态表达式：</strong>支持Spring EL表达式进行复杂条件匹配</li>
 *   <li><strong>标签路由：</strong>增强的标签匹配能力，支持复杂标签表达式</li>
 *   <li><strong>统计分析：</strong>提供路由统计信息，便于性能分析</li>
 *   <li><strong>优先级管理：</strong>支持基于优先级的实现选择</li>
 *   <li><strong>路由预热：</strong>支持路由结果预热，减少首次访问延迟</li>
 *   <li><strong>降级策略：</strong>支持路由失败时的降级处理</li>
 * </ul>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 * @see ExtPointRouter 扩展点路由器接口
 * @see BizContext 业务上下文
 */
@Component
public class DefaultExtPointRouter implements ExtPointRouter, SmartInitializingSingleton, InitializingBean, ConfigChangeListener {

    // 使用统一配置管理
    private final RouterConfiguration config = RouterConfiguration.getInstance();
    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);

    // Spring上下文
    private final ApplicationContext applicationContext;

    // 组件依赖
    private final CacheManager cacheManager;
    private final RouteScoreCalculator scoreCalculator;
    private final WeightAndGraySelector weightAndGraySelector;
    private final RouteStatsCollector statsCollector;

    // 表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    // 扩展点注册服务
    private final ExtensionRegister extensionRegister;

    /**
     * 构造函数，支持自动注入ApplicationContext和RouteStatsCollector
     */
    @Autowired
    public DefaultExtPointRouter(ApplicationContext applicationContext,
                                 RouteStatsCollector routeStatsCollector,
                                 ExtensionRegister extensionRegister) {
        // 参数校验
        Assert.notNull(applicationContext, "ApplicationContext must not be null");
        Assert.notNull(routeStatsCollector, "RouteStatsCollector must not be null");
        Assert.notNull(extensionRegister, "ExtensionRegistry must not be null");

        // 初始化共享属性
        this.applicationContext = applicationContext;
        this.statsCollector = routeStatsCollector;
        this.extensionRegister = extensionRegister;

        // 从ApplicationContext获取CacheManager Bean，避免重复创建实例
        this.cacheManager = applicationContext.getBean(CacheManager.class);
        this.scoreCalculator = new RouteScoreCalculator();
        this.weightAndGraySelector = new WeightAndGraySelector();
    }


    /**
     * 构造函数，支持自定义组件配置
     */
    public DefaultExtPointRouter(ApplicationContext applicationContext,
                                 CacheManager cacheManager,
                                 RouteScoreCalculator scoreCalculator,
                                 WeightAndGraySelector weightAndGraySelector,
                                 RouteStatsCollector statsCollector,
                                 ExtensionRegister extensionRegister) {
        // 参数校验
        Assert.notNull(applicationContext, "ApplicationContext must not be null");
        Assert.notNull(cacheManager, "CacheManager must not be null");
        Assert.notNull(statsCollector, "RouteStatsCollector must not be null");
        Assert.notNull(extensionRegister, "ExtensionRegistry must not be null");

        // 初始化所有属性
        this.applicationContext = applicationContext;
        this.cacheManager = cacheManager;
        this.scoreCalculator = scoreCalculator;
        this.weightAndGraySelector = weightAndGraySelector;
        this.statsCollector = statsCollector;
        this.extensionRegister = extensionRegister;
    }

    // 扩展点配置属性
    @Autowired(required = false)
    private ExtensionProperties extensionProperties;

    // 路由失败的降级策略
    private final AtomicReference<Function<Throwable, Boolean>> fallbackStrategy =
            new AtomicReference<>(ex -> true);

    // 扩展点生命周期处理器
    @Autowired(required = false)
    private ExtensionLifecycle extensionLifecycle;

    // 扩展点配置管理器
    @Autowired(required = false)
    private ExtensionConfigManager configManager;

    // 配置访问工具方法 - 获取整型配置值
    private int getIntConfig(Supplier<Integer> configSupplier, int defaultValue) {
        try {
            return configSupplier != null ? configSupplier.get() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // 获取缓存过期时间配置（秒）
    private int getCacheExpireTime() {
        // 配置优先级：ExtensionConfigManager > ExtensionProperties > RouterConfiguration > 默认值
        return getIntConfig(
                () -> {
                    // 优先从ExtensionConfigManager获取（单位：秒）
                    if (configManager != null) {
                        return (int) (configManager.getExtPointCacheExpireTime(null) / 1000);
                    }
                    // 回退到ExtensionProperties（单位：秒）
                    if (extensionProperties != null && extensionProperties.getCache() != null) {
                        return (int) (extensionProperties.getCache().getExpireTime() / 1000);
                    }
                    // 使用统一配置
                    return (int) (config.getCacheExpireTime() * 60); // 转换为秒
                },
                300 // 默认5分钟（300秒）
        );
    }

    // 获取缓存最大容量配置
    private int getCacheMaxSize() {
        // 配置优先级：ExtensionConfigManager > ExtensionProperties > RouterConfiguration > 默认值
        return getIntConfig(
                () -> {
                    // 优先从ExtensionConfigManager获取
                    if (configManager != null) {
                        return configManager.getExtPointCacheMaxSize(null);
                    }
                    // 回退到ExtensionProperties
                    if (extensionProperties != null && extensionProperties.getCache() != null) {
                        return extensionProperties.getCache().getMaxSize();
                    }
                    // 使用统一配置
                    return (int) config.getCacheMaxSize();
                },
                1000 // 默认1000个条目
        );
    }

    // 配置访问工具方法 - 获取布尔配置值
    private boolean getBooleanConfig(Supplier<Boolean> configSupplier, boolean defaultValue) {
        try {
            return configSupplier != null ? configSupplier.get() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // 配置访问工具方法 - 获取长整型配置值
    private long getLongConfig(Supplier<Long> configSupplier, long defaultValue) {
        try {
            return configSupplier != null ? configSupplier.get() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // 是否启用权重路由
    private boolean isWeightedRoutingEnabled() {
        // 优先从extensionProperties获取，否则使用统一配置
        return getBooleanConfig(
                () -> extensionProperties != null && extensionProperties.getRouter() != null &&
                        extensionProperties.getRouter().getWeighted() != null ?
                        extensionProperties.getRouter().getWeighted().isEnabled() : config.isWeightedRoutingEnabled(),
                config.isWeightedRoutingEnabled()
        );
    }

    // 是否启用灰度发布
    private boolean isGrayReleaseEnabled() {
        // 优先从extensionProperties获取，否则使用统一配置
        return getBooleanConfig(
                () -> extensionProperties != null && extensionProperties.getRouter() != null &&
                        extensionProperties.getRouter().getGrayRelease() != null ?
                        extensionProperties.getRouter().getGrayRelease().isEnabled() : config.isGrayReleaseEnabled(),
                config.isGrayReleaseEnabled()
        );
    }

    // 是否启用指标收集
    private boolean isMetricsEnabled() {
        // 优先从extensionProperties获取，否则使用统一配置
        return getBooleanConfig(
                () -> extensionProperties != null && extensionProperties.getRouter() != null &&
                        extensionProperties.getRouter().getMetrics() != null ?
                        extensionProperties.getRouter().getMetrics().isEnabled() : config.isStatsEnabled(),
                config.isStatsEnabled()
        );
    }

    // 获取性能警告阈值（毫秒）
    private long getWarningThreshold() {
        // 优先从extensionProperties获取，否则使用统一配置
        return getLongConfig(
                () -> extensionProperties != null && extensionProperties.getRouter() != null &&
                        extensionProperties.getRouter().getMetrics() != null ?
                        extensionProperties.getRouter().getMetrics().getWarningThreshold() : config.getSlowThresholdMs(),
                config.getSlowThresholdMs()
        );
    }

    @Override
    public void afterPropertiesSet() {
        // 确保CacheManager不为null
        if (cacheManager != null) {
            // 委托给CacheManager初始化缓存
            cacheManager.initializeCache((int) getCacheExpireTime(), (int) getCacheMaxSize());

            log.info("Initialized extPoint router cache with expireTime={}s, maxSize={}, cacheEnabled={}",
                    getCacheExpireTime(), getCacheMaxSize(), enableCache);
        }

        // 初始化计数器
        lastOptimizeTime = System.currentTimeMillis();
        log.info("DefaultExtPointRouter initialized with weighted routing={}, gray release={}",
                isWeightedRoutingEnabled(), isGrayReleaseEnabled());
    }

    /**
     * 获取缓存键 - 使用CacheKeyFactory统一管理
     */
    private String getCacheKey(Class<?> extPointClass, BizContext<?> context) {
        // 添加空值检查以提高健壮性
        if (extPointClass == null) {
            throw new IllegalArgumentException("Extension point class cannot be null");
        }
        return CacheKeyFactory.createExtPointCacheKey(extPointClass, context);
    }

    /**
     * 获取方法名称，如果方法为null则返回默认值
     */
    private String getMethodName(Method method) {
        return method != null ? method.getName() : "default";
    }

    /**
     * 设置路由降级策略
     */
    public void setFallbackStrategy(Function<Throwable, Boolean> fallbackStrategy) {
        this.fallbackStrategy.set(fallbackStrategy);
    }

    /**
     * 设置扩展点生命周期处理器
     */
    public void setExtensionLifecycle(ExtensionLifecycle extensionLifecycle) {
        this.extensionLifecycle = extensionLifecycle;
    }

    /**
     * 使用反射设置布尔字段值的通用方法
     */
    private <T> void setBooleanField(T targetObject, String fieldName, boolean value, String configName) {
        if (targetObject == null) {
            return;
        }

        try {
            // 反射设置值，避免修改接口
            java.lang.reflect.Field field = targetObject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(targetObject, value);
            log.info("{} set to: {}", configName, value);
            clearCache();
        } catch (Exception e) {
            log.warn("Failed to update {} configuration", configName, e);
        }
    }

    /**
     * 设置是否启用权重路由
     */
    public void setWeightedRoutingEnabled(boolean weightedRoutingEnabled) {
        if (extensionProperties != null && extensionProperties.getRouter() != null) {
            setBooleanField(
                    extensionProperties.getRouter().getWeighted(),
                    "enabled",
                    weightedRoutingEnabled,
                    "Weighted routing enabled"
            );
        }
    }

    /**
     * 设置是否启用灰度发布
     */
    public void setGrayReleaseEnabled(boolean grayReleaseEnabled) {
        if (extensionProperties != null && extensionProperties.getRouter() != null) {
            setBooleanField(
                    extensionProperties.getRouter().getGrayRelease(),
                    "enabled",
                    grayReleaseEnabled,
                    "Gray release enabled"
            );
        }
    }

    public void onConfigChanged(Set<String> changedKeys) {
        if (changedKeys == null || changedKeys.isEmpty()) {
            return;
        }

        boolean needClearCache = false;

        // 检查是否有与路由相关的配置变更
        for (String key : changedKeys) {
            if (key.contains("router") || key.contains("cache") || key.contains("weighted") || key.contains("gray")) {
                needClearCache = true;
                break;
            }
        }

        if (needClearCache) {
            log.info("Clearing route cache due to configuration changes: {}", changedKeys);
            clearCache();

            // 重新初始化路由相关配置
            try {
                reinitializeConfig();
            } catch (Exception e) {
                log.error("Failed to reinitialize configuration after config change", e);
            }
        }
    }

    public String[] getConfigKeyPrefixes() {
        return new String[]{
                "bone.extension.router",
                "bone.extension.cache",
                "bone.extension.weighted",
                "bone.extension.gray"
        };
    }

    /**
     * 清理所有缓存
     */
    public void clearCache() {
        // 委托给CacheManager清理缓存
        cacheManager.clearAllCache();
        log.info("Route cache cleared");
        log.info("Route rule cache cleared");
    }

    /**
     * 重新初始化配置
     */
    private void reinitializeConfig() {
        try {
            // 配置已通过ExtensionProperties统一管理，无需在这里重新读取

            // 重新初始化缓存
            afterPropertiesSet();

            log.info("Router configuration reinitialized");
        } catch (Exception e) {
            log.error("Failed to reinitialize router configuration", e);
            throw new RuntimeException("Failed to reinitialize router configuration", e);
        }
    }

    /**
     * 使用反射设置长整型字段值的通用方法
     */
    private <T> void setLongField(T targetObject, String fieldName, long value, String configName) {
        if (targetObject == null) {
            return;
        }

        try {
            // 反射设置值，避免修改接口
            java.lang.reflect.Field field = targetObject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setLong(targetObject, value);
            log.info("{} set to: {}", configName, value);
        } catch (Exception e) {
            log.warn("Failed to update {} configuration", configName, e);
        }
    }

    /**
     * 设置是否启用指标收集
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        if (extensionProperties != null && extensionProperties.getRouter() != null) {
            setBooleanField(
                    extensionProperties.getRouter().getMetrics(),
                    "enabled",
                    metricsEnabled,
                    "Metrics enabled"
            );
        }
    }

    /**
     * 设置性能警告阈值
     */
    public void setWarningThreshold(long warningThreshold) {
        if (extensionProperties != null && extensionProperties.getRouter() != null) {
            setLongField(
                    extensionProperties.getRouter().getMetrics(),
                    "warningThreshold",
                    warningThreshold,
                    "Warning threshold (ms)"
            );
        }
    }

    // 用于向后兼容的临时字段
    private boolean enableCache = true; // 是否启用缓存（可覆盖全局配置）
    private boolean enableEventPublish = true; // 是否启用事件发布

    /**
     * 设置是否启用缓存
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
        // 更新缓存启用状态
        // 注意：这个方法主要用于向后兼容ExtensionAutoConfiguration
        // 主要配置应通过ExtensionProperties统一管理
        log.debug("Cache enable state set to: {}", enableCache);
    }

    /**
     * 获取缓存是否启用
     *
     * @return 缓存是否启用
     */
    public boolean isCacheEnabled() {
        return enableCache;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        // 委托给CacheManager获取缓存统计信息
        return cacheManager.getCacheStats();
    }

    /**
     * 记录路由统计
     */
    private void recordRouteStats(Class<?> extPointClass, Object implementation) {
        // 委托给statsCollector记录路由统计信息
        statsCollector.recordRouteStats(extPointClass, implementation);
    }

    /**
     * 记录路由失败统计
     */
    private void recordRouteFailure(Class<?> extPointClass, Throwable ex) {
        // 委托给statsCollector记录路由失败信息
        statsCollector.recordRouteFailure(extPointClass, ex);
    }

    /**
     * 检查扩展实现是否在有效期内
     */
    private boolean isWithinValidTimeRange(Extension extension) {
        LocalDateTime now = LocalDateTime.now();

        // 检查开始时间
        if (StringUtils.hasText(extension.startTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(extension.startTime(), DateTimeFormatter.ISO_DATE_TIME);
                if (now.isBefore(startTime)) {
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid start time format: {}", extension.startTime(), e);
            }
        }

        // 检查结束时间
        if (StringUtils.hasText(extension.endTime())) {
            try {
                LocalDateTime endTime = LocalDateTime.parse(extension.endTime(), DateTimeFormatter.ISO_DATE_TIME);
                if (now.isAfter(endTime)) {
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid end time format: {}", extension.endTime(), e);
            }
        }

        return true;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, BizContext<?> context) {
        if (!StringUtils.hasText(condition) || context == null) {
            return true;
        }

        try {
            // 检查表达式缓存
            // 直接使用表达式解析器解析，确保表达式能正确处理
            Expression expression = expressionParser.parseExpression(condition);

            // 创建评估上下文，增加安全配置
            EvaluationContext evalContext = new StandardEvaluationContext();

            // 添加常用变量
            evalContext.setVariable("tenantCode", safeGetNestedProperty(context, "tenantCode") != null ? safeGetNestedProperty(context, "tenantCode") : "DEFAULT");
            evalContext.setVariable("bizCode", safeGetNestedProperty(context, "bizCode") != null ? safeGetNestedProperty(context, "bizCode") : "");
            evalContext.setVariable("useCase", safeGetNestedProperty(context, "useCase") != null ? safeGetNestedProperty(context, "useCase") : "");
            evalContext.setVariable("scenario", safeGetNestedProperty(context, "scenario") != null ? safeGetNestedProperty(context, "scenario") : "");
            evalContext.setVariable("context", context);
            evalContext.setVariable("env", "PROD");
            evalContext.setVariable("userGroup", "DEFAULT");
            evalContext.setVariable("currentTime", LocalDateTime.now());

            // 安全地添加上下文属性
            context.getAllAttributes().forEach((key, value) -> {
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    evalContext.setVariable(key, value);
                }
            });

            // 添加属性集合
            try {
                Method method = context.getClass().getMethod("getAllAttributes");
                Map<String, Object> attributes = (Map<String, Object>) method.invoke(context);
                evalContext.setVariable("attributes", attributes);
            } catch (Exception e) {
                evalContext.setVariable("attributes", Collections.emptyMap());
            }

            // 添加安全的辅助方法
            evalContext.setVariable("hasAttribute", (Function<String, Boolean>) key -> {
                try {
                    Method method = context.getClass().getMethod("containsAttribute", String.class);
                    return (Boolean) method.invoke(context, key);
                } catch (Exception e) {
                    return false;
                }
            });
            evalContext.setVariable("getAttribute", (Function<String, Object>) key -> {
                try {
                    Method method = context.getClass().getMethod("getAttribute", String.class);
                    Object value = method.invoke(context, key);
                    // 只返回基本类型
                    return (value instanceof String || value instanceof Number || value instanceof Boolean) ? value : null;
                } catch (Exception e) {
                    return null;
                }
            });

            // 评估表达式
            Object result = expression.getValue(evalContext);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}, will return false", condition, e);
            return false;
        }
    }

    /**
     * 获取嵌套属性值
     */
    private Object getNestedProperty(Object obj, String propertyPath) {
        return scoreCalculator.getNestedProperty(obj, propertyPath);
    }

    /**
     * 安全地获取嵌套属性值
     */
    private String safeGetNestedProperty(Object obj, String propertyName) {
        try {
            Object value = getNestedProperty(obj, propertyName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public <T> T route(Class<T> extPointClass, BizContext<?> context) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(context, "Business context cannot be null");

        // 准备路由属性
        Map<String, Object> routeAttributes = new HashMap<>();
        // 调用路由前生命周期方法
        if (extensionLifecycle != null) {
            extensionLifecycle.beforeRouting(context, extPointClass, routeAttributes);
        }

        try {
            // 检查是否启用缓存
            boolean useCache = this.enableCache;

            // 尝试从缓存获取
            if (useCache) {
                String cacheKey = getCacheKey(extPointClass, context);
                @SuppressWarnings("unchecked")
                T cachedResult = (T) cacheManager.getFromCache(cacheKey, k -> doRouteWithStats(extPointClass, context));
                if (cachedResult != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache {} for extPoint: {}, result: {}",
                                cacheManager.isCacheHit(cacheKey) ? "hit" : "miss and loaded",
                                extPointClass.getSimpleName(),
                                cachedResult.getClass().getSimpleName());
                    }
                    recordRouteStats(extPointClass, cachedResult);
                    return cachedResult;
                }
            }

            // 执行路由并记录统计
            T result = doRouteWithStats(extPointClass, context);

            return result;
        } catch (Exception e) {
            log.error("Route error for extPoint: {}", extPointClass.getName(), e);
            recordRouteFailure(extPointClass, e);

            // 执行降级策略
            boolean fallbackToDefault = fallbackStrategy.get().apply(e);
            if (fallbackToDefault) {
                T defaultImpl = getDefaultImplementation(extPointClass);
                if (defaultImpl != null) {
                    log.warn("Fallback to default implementation: {} for extPoint: {}",
                            defaultImpl.getClass().getSimpleName(),
                            extPointClass.getSimpleName());
                    return defaultImpl;
                }
            }

            // 如果没有默认实现且允许抛出异常，重新抛出
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to route extPoint: " + extPointClass.getName(), e);
        }
    }

    /**
     * 执行路由并记录统计信息
     */

    /**
     * 执行路由并记录统计信息
     */
    /**
     * 带统计信息的路由执行方法
     * 添加细粒度监控、慢调用检测和自适应缓存优化
     */
    private <T> T doRouteWithStats(Class<T> extPointClass, BizContext<?> context) {
        long startTime = System.nanoTime();
        boolean success = false;
        Object result = null;

        try {
            // 执行路由
            result = doRoute(extPointClass, context);
            success = true;
            return (T) result;
        } catch (Exception e) {
            if (statsCollector != null) {
                statsCollector.recordRouteFailure(extPointClass, e);
            }
            throw e;
        } finally {
            long endTime = System.nanoTime();
            long durationNanos = endTime - startTime;
            long durationMs = durationNanos / 1_000_000;

            // 使用统一配置判断是否启用统计
            if ((config.isStatsEnabled() || isMetricsEnabled()) && statsCollector != null) {
                // 使用statsCollector记录所有统计信息

                // 记录路由统计
                if (result != null) {
                    statsCollector.recordRouteStats(extPointClass, result);
                }

                // 记录详细指标
                long warningThresholdMs = getWarningThreshold();
                statsCollector.recordMetrics(extPointClass, success, durationMs, warningThresholdMs);

                // 慢调用检测和详情记录
                long slowThreshold = Math.min(warningThresholdMs, config.getSlowThresholdMs());
                if (durationMs > slowThreshold) {
                    statsCollector.recordSlowCall(extPointClass, durationMs, slowThreshold);
                    log.warn("Slow route detected for {}: {}ms (threshold: {}ms)",
                            extPointClass.getName(), durationMs, slowThreshold);

                    // 记录慢调用上下文信息
                    if (log.isDebugEnabled()) {
                        log.debug("Slow route context: tenantCode={}, bizCode={}, useCase={}, scenario={}",
                                context.getTenantCode(), context.getBizCode(),
                                context.getUseCase(), context.getScenario());
                    }
                }
            }

            // 记录详细日志
            if (log.isTraceEnabled()) {
                log.trace("Route for extPoint: {} took {}ms, success: {}, result: {}",
                        extPointClass.getSimpleName(),
                        durationMs,
                        success,
                        result != null ? result.getClass().getSimpleName() : "null");
            }

            // 定期执行缓存优化（每1000次调用或每分钟调用一次）
            if (isCacheOptimizationNeeded()) {
                try {
                    if (cacheManager != null) {
                        cacheManager.optimizeCacheConfig();
                    }
                } catch (Exception e) {
                    log.error("Failed to optimize cache configuration", e);
                }
            }
        }
    }

    // 用于控制缓存优化频率的计数器
    private final AtomicLong routeCounter = new AtomicLong(0);
    private volatile long lastOptimizeTime = System.currentTimeMillis();

    /**
     * 检查是否需要执行缓存优化
     * 基于调用次数和时间间隔的双重触发机制，完全使用统一配置
     */
    private boolean isCacheOptimizationNeeded() {
        if (!config.isCacheOptimizationEnabled()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long optimizeIntervalMs = config.getOptimizeIntervalMs();
        long callThreshold = config.getOptimizeCallThreshold();

        // 检查是否达到调用阈值
        boolean callThresholdReached = routeCounter.incrementAndGet() % callThreshold == 0;

        // 检查是否达到时间间隔阈值
        boolean timeIntervalReached = (currentTime - lastOptimizeTime) > optimizeIntervalMs;

        if (callThresholdReached || timeIntervalReached) {
            // 获取当前的lastOptimizeTime用于CAS比较
            long currentLastOptimize = lastOptimizeTime;

            // 双重检查确保仍然满足条件
            if (callThresholdReached || (currentTime - currentLastOptimize) > optimizeIntervalMs) {
                // 更新时间戳并返回true
                lastOptimizeTime = currentTime;
                return true;
            }
        }
        return false;
    }

    // 移除单独的recordMetrics方法，使用statsCollector代替

    /**
     * 预热路由缓存
     */
    public <T> void warmupCache(Class<T> extPointClass, List<BizContext<?>> contexts) {
        boolean warmupEnabled = applicationContext.getEnvironment().getProperty("bone.extension.router.warmup.enabled", Boolean.class, false);
        if (!warmupEnabled || CollectionUtils.isEmpty(contexts)) {
            return;
        }

        log.info("Warming up cache for extPoint: {} with {} contexts",
                extPointClass.getSimpleName(), contexts.size());

        for (BizContext<?> context : contexts) {
            try {
                // 委托给CacheManager进行缓存预热
                cacheManager.warmupCache(extPointClass, context);
            } catch (Exception e) {
                log.warn("Failed to warmup cache for extPoint: {} with context: {}",
                        extPointClass.getSimpleName(), context, e);
            }
        }

        log.info("Cache warmup completed for extPoint: {}", extPointClass.getSimpleName());
    }

    /**
     * 执行实际的路由匹配
     */
    @SuppressWarnings("unchecked")
    private <T> T doRoute(Class<T> extPointClass, BizContext<?> context) {
        try {
            // 尝试从规则缓存获取预过滤的实现列表
            List<Object> implementations = getOrCreateRouteRuleCache(extPointClass);
            if (CollectionUtils.isEmpty(implementations)) {
                log.warn("No implementations found for extPoint: {}", extPointClass.getName());
                return getDefaultImplementation(extPointClass);
            }

            // 过滤有效的实现
            List<Object> validImpls = implementations.stream()
                    .filter(impl -> {
                        Extension extension = impl.getClass().getAnnotation(Extension.class);
                        if (extension == null) {
                            return false;
                        }

                        // 快速检查 - 先检查基本条件
                        if (!extension.enabled()) {
                            return false;
                        }

                        // 检查时间范围
                        if (!isWithinValidTimeRange(extension)) {
                            return false;
                        }

                        // 检查条件表达式（性能开销较大，放在最后）
                        return evaluateCondition(extension.condition(), context);
                    })
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(validImpls)) {
                // 没有匹配的实现，返回默认实现
                return getDefaultImplementation(extPointClass);
            }

            // 按匹配得分和优先级排序
            validImpls.sort((impl1, impl2) -> {
                Extension ext1 = impl1.getClass().getAnnotation(Extension.class);
                Extension ext2 = impl2.getClass().getAnnotation(Extension.class);

                // 使用RouteScoreCalculator计算匹配得分
                int score1 = scoreCalculator.calculateMatchScore(ext1, context);
                int score2 = scoreCalculator.calculateMatchScore(ext2, context);

                if (score1 != score2) {
                    return Integer.compare(score2, score1); // 得分高的优先
                }

                // 得分相同，比较优先级
                if (ext1.priority() != ext2.priority()) {
                    return Integer.compare(ext1.priority(), ext2.priority()); // 优先级低的数值小，优先
                }

                // 最后比较类名，确保排序稳定性
                return impl1.getClass().getName().compareTo(impl2.getClass().getName());
            });

            // 记录详细路由日志
            if (log.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Route details for extPoint: " + extPointClass.getSimpleName() + "\n");
                sb.append("Context: " + context + "\n");
                for (int i = 0; i < validImpls.size() && i < 5; i++) { // 只记录前5个
                    Object impl = validImpls.get(i);
                    Extension ext = impl.getClass().getAnnotation(Extension.class);
                    sb.append(String.format("  Rank %d: %s (score=%d, priority=%d)\n",
                            i + 1,
                            impl.getClass().getSimpleName(),
                            scoreCalculator.calculateMatchScore(ext, context),
                            ext.priority()));
                }
                log.trace(sb.toString());
            }

            // 应用权重路由和灰度发布策略
            @SuppressWarnings("unchecked")
            T selectedImplementation = (T) weightAndGraySelector.applyWeightAndGrayRelease(
                    validImpls, extPointClass, context, isWeightedRoutingEnabled(), isGrayReleaseEnabled());

            // 安全检查，避免空指针异常
            if (selectedImplementation == null) {
                log.warn("Weight and gray selector returned null for extPoint: {}", extPointClass.getName());
                return getDefaultImplementation(extPointClass);
            }

            if (log.isDebugEnabled()) {
                log.debug("Selected implementation: {} for extPoint: {}",
                        selectedImplementation.getClass().getSimpleName(),
                        extPointClass.getSimpleName());
            }

            return selectedImplementation;
        } catch (Exception e) {
            log.error("Error occurred during routing for {}", extPointClass.getName(), e);
            try {
                return getDefaultImplementation(extPointClass);
            } catch (Exception ex) {
                log.error("Failed to get default implementation for {}", extPointClass.getName(), ex);
                return null;
            }
        }
    }

    // 移除权重路由和灰度发布相关的方法，使用WeightAndGraySelector组件代替

    /**
     * 获取或创建路由规则缓存
     */
    @SuppressWarnings("unchecked")
    private List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass) {
        try {
            // 避免lambda表达式中的类型推断问题
            if (extensionRegister == null) {
                log.warn("ExtensionRegistry is null, cannot get implementations for {}", extPointClass.getName());
                return new ArrayList<>();
            }
            List<Object> implementations = (List<Object>) extensionRegister.getAllImplementations((Class<Object>) extPointClass);
            return cacheManager.getOrCreateRouteRuleCache(extPointClass, k -> implementations);
        } catch (Exception e) {
            log.warn("Failed to create route rule cache for {}", extPointClass.getName(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 刷新路由规则缓存
     */
    public void refreshRouteRuleCache(Class<?> extPointClass) {
        cacheManager.refreshRouteRuleCache(extPointClass);
        log.info("Refreshed route rule cache for extPoint: {}",
                extPointClass != null ? extPointClass.getSimpleName() : "ALL");
    }


    public void clearCache(Class<?> extPointClass) {
        Assert.notNull(extPointClass, "ExtensionPoint class must not be null");

        // 使用CacheManager清理缓存
        cacheManager.clearCache(extPointClass);

        // 同时清理路由规则缓存
        refreshRouteRuleCache(extPointClass);

        log.debug("Cleared cache for extPoint: {}", extPointClass.getName());
    }

    public void clearAllCache() {
        cacheManager.clearAllCache();
        // 刷新路由规则缓存
        refreshRouteRuleCache(null);
        log.debug("Cleared all route cache");
    }

    @SuppressWarnings("unchecked")
    public <T> void registerImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtensionPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");

        // 使用统一的注册服务
        if (extensionRegister != null) {
            extensionRegister.registerImplementation(extPointClass, implementation);
        } else {
            // 验证实现类是否实现了扩展点接口
            Assert.isAssignable(extPointClass, implementation.getClass(),
                    "Implementation must implement the extPoint interface");

            // 获取Extension注解
            Extension extension = implementation.getClass().getAnnotation(Extension.class);
            if (extension == null) {
                log.warn("Implementation {} does not have @Extension annotation",
                        implementation.getClass().getName());
            }
        }

        // 清理缓存
        clearCache(extPointClass);

        log.info("Registered implementation: {} for extPoint: {}",
                implementation.getClass().getSimpleName(),
                extPointClass.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public <T> void unregisterImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtensionPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");

        // 使用统一的注册服务
        if (extensionRegister != null) {
            extensionRegister.unregisterImplementation(extPointClass, implementation);
        }

        // 清理缓存
        clearCache(extPointClass);

        log.info("Unregistered implementation: {} for extPoint: {}",
                implementation.getClass().getSimpleName(),
                extPointClass.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public <T> T getDefaultImplementation(Class<T> extPointClass) {
        if (extensionRegister == null) {
            return null;
        }
        return extensionRegister.getDefaultImplementation(extPointClass);
    }

    /**
     * 设置默认实现（运行时覆盖）
     */
    @SuppressWarnings("unchecked")
    public <T> void setDefaultImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtensionPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");

        // 使用统一的注册服务
        if (extensionRegister != null) {
            extensionRegister.setDefaultImplementation(extPointClass, implementation);
        } else {
            // 验证实现类是否实现了扩展点接口
            Assert.isAssignable(extPointClass, implementation.getClass(),
                    "Implementation must implement the extPoint interface");
        }

        clearCache(extPointClass);

        log.info("Set default implementation: {} for extPoint: {}",
                implementation.getClass().getSimpleName(),
                extPointClass.getSimpleName());
    }

    public Map<String, Map<String, Long>> getRouteStats() {
        // 直接调用statsCollector.getRouteStats()，它已经返回正确的类型
        return statsCollector.getRouteStats();
    }

    /**
     * 重置路由统计信息
     */
    public void resetRouteStats() {
        statsCollector.resetRouteStats();
        log.info("Reset route statistics");
    }

    /**
     * 获取扩展点的实现统计信息
     */
    public Map<String, Integer> getImplementationStats() {
        if (extensionRegister == null) {
            return Collections.emptyMap();
        }
        return extensionRegister.getImplementationStats();
    }

    /**
     * 在Spring容器初始化完成后，自动注册所有扩展点实现
     */
    public void afterSingletonsInstantiated() {
        long startTime = System.currentTimeMillis();
        log.info("Initializing extPoint router...");

        try {
            // 获取所有被@Extension注解的实现类
            Map<String, Object> extPointImplementationBeans =
                    applicationContext.getBeansWithAnnotation(Extension.class);

            int totalRegistered = 0;
            int failedRegistrations = 0;

            for (Map.Entry<String, Object> entry : extPointImplementationBeans.entrySet()) {
                String beanName = entry.getKey();
                Object implementation = entry.getValue();

                try {
                    // 使用ExtPointUtils查找所有扩展点接口（包括父类中的）
                    boolean registered = false;
                    List<Class<?>> extPointInterfaces = ExtPointUtils.findExtPointInterfaces(implementation.getClass());
                    for (Class<?> iface : extPointInterfaces) {
                        // 使用原始类型和类型转换解决泛型类型不匹配问题
                        registerImplementation((Class) iface, implementation);
                        registered = true;
                        totalRegistered++;
                    }

                    if (!registered) {
                        log.warn("Implementation bean {} has @Extension annotation but implements no @ExtensionPoint interfaces",
                                beanName);
                        failedRegistrations++;
                    }
                } catch (Exception e) {
                    log.error("Failed to register implementation: {}", beanName, e);
                    failedRegistrations++;
                }
            }

            // 记录初始化统计
            int totalExtensions = extensionRegister != null ? extensionRegister.getRegisteredProviderCount() : 0;
            int extPointCount = extensionRegister != null ? extensionRegister.getExtPointCount() : 0;

            long costTime = System.currentTimeMillis() - startTime;
            log.info("ExtensionPoint router initialized in {}ms with {} extension implementations across {} extension points",
                    costTime, totalExtensions, extPointCount);

            if (failedRegistrations > 0) {
                log.warn("Failed to register {} extension implementations", failedRegistrations);
            }

            // 打印扩展点实现详情
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Extension point implementations summary:\n");
                if (extensionRegister != null) {
                    Map<String, Integer> implementationStats = extensionRegister.getImplementationStats();
                    implementationStats.forEach((extPointName, count) -> {
                        sb.append(String.format("  %s: %d implementations\n",
                                extPointName, count));
                        // 注意：这里无法获取具体的实现详情，因为统一注册服务可能不会暴露这级别的信息
                    });
                } else {
                    sb.append("  No extension registry available, cannot display implementation details\n");
                }
                log.debug(sb.toString());
            }

        } catch (Exception e) {
            log.error("Failed to initialize extPoint router", e);
            throw new RuntimeException("Failed to initialize extPoint router", e);
        }
    }
}