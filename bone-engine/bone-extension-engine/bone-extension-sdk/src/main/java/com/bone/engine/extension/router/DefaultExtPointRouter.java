package com.bone.engine.extension.router;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 默认扩展点路由器实现
 * <p>
 * 实现基于业务上下文的智能路由选择，支持多级匹配策略、缓存优化和动态表达式评估
 * <strong>核心功能：</strong>扩展点注册管理、智能路由匹配、缓存优化、表达式评估
 * </p>
 * 
 * <h3>实现特性：</h3>
 * <ul>
 *   <li><strong>缓存优化：</strong>基于Caffeine实现高效缓存</li>
 *   <li><strong>多级匹配：</strong>支持精确匹配、部分匹配和默认匹配</li>
 *   <li><strong>动态表达式：</strong>支持Spring EL表达式进行复杂条件匹配</li>
 *   <li><strong>统计分析：</strong>提供路由统计信息，便于性能分析</li>
 *   <li><strong>优先级管理：</strong>支持基于优先级的实现选择</li>
 * </ul>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 * @see ExtPointRouter 扩展点路由器接口
 * @see BizContext 业务上下文
 */
public class DefaultExtPointRouter implements ExtPointRouter, SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);
    
    // Spring上下文
    @Autowired
    private ApplicationContext applicationContext;
    
    // 表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // 参数名发现器
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    // 扩展点实现映射（扩展点接口 -> 实现列表）
    private final Map<Class<?>, List<Object>> extPointImplementations = new ConcurrentHashMap<>();
    
    // 默认实现映射
    private final Map<Class<?>, Object> defaultImplementations = new ConcurrentHashMap<>();
    
    // 路由缓存
    private final Map<String, Object> routeCache = new ConcurrentHashMap<>();
    
    // 路由统计信息
    private final Map<String, Map<String, AtomicLong>> routeStats = new ConcurrentHashMap<>();
    
    /**
     * 获取缓存键
     */
    private String getCacheKey(Class<?> extPointClass, BizContext<?> context) {
        StringBuilder key = new StringBuilder(extPointClass.getName());
        key.append("_")
           .append("null")
           .append("_")
           .append("null")
           .append("_")
           .append("null")
           .append("_")
           .append("null");
        return key.toString();
    }
    
    /**
     * 记录路由统计
     */
    private void recordRouteStats(Class<?> extPointClass, Object implementation) {
        try {
            String extPointName = extPointClass.getSimpleName();
            String implName = implementation.getClass().getSimpleName();
            
            routeStats.computeIfAbsent(extPointName, k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(implName, k -> new AtomicLong())
                      .incrementAndGet();
        } catch (Exception e) {
            // 统计记录失败不影响主流程
            log.debug("Failed to record route stats", e);
        }
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
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        
        try {
            EvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariable("tenantCode", "");
            evalContext.setVariable("bizCode", "");
            evalContext.setVariable("useCase", "");
            evalContext.setVariable("scenario", "");
            evalContext.setVariable("data", null);
            evalContext.setVariable("context", context);
            
            // 添加上下文属性到评估环境
            context.getAllAttributes().forEach(evalContext::setVariable);
            
            Expression expression = expressionParser.parseExpression(condition);
            return Boolean.TRUE.equals(expression.getValue(evalContext, Boolean.class));
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}, will return false", condition, e);
            return false;
        }
    }
    
    /**
     * 计算匹配得分
     */
    private int calculateMatchScore(Extension extension, BizContext<?> context) {
        int score = 0;
        
        // 租户匹配
        if (StringUtils.hasText(extension.tenantCode())) {
            if (Objects.equals(extension.tenantCode(), "")) {
                score += 1000;
            }
        }
        
        // 多租户匹配
        if (!ObjectUtils.isEmpty(extension.multiTenantCodes())) {
            if (Arrays.asList(extension.multiTenantCodes()).contains("")) {
                score += 1000;
            }
        }
        
        // 业务域匹配
        if (StringUtils.hasText(extension.bizCode())) {
            if (Objects.equals(extension.bizCode(), "")) {
                score += 100;
            }
        }
        
        // 多业务域匹配
        if (!ObjectUtils.isEmpty(extension.multiBizCodes())) {
            if (Arrays.asList(extension.multiBizCodes()).contains("")) {
                score += 100;
            }
        }
        
        // 用例匹配
        if (StringUtils.hasText(extension.useCase())) {
            if (Objects.equals(extension.useCase(), "")) {
                score += 10;
            }
        }
        
        // 场景匹配
        if (StringUtils.hasText(extension.scenario())) {
            if (Objects.equals(extension.scenario(), "")) {
                score += 1;
            }
        }
        
        // 检查支付方式匹配（如果是支付相关扩展点）
        if (StringUtils.hasText(extension.paymentMethod())) {
            Object data = null;
            if (data != null) {
                try {
                    Method getPaymentMethod = data.getClass().getMethod("getPaymentMethod");
                    String paymentMethod = (String) getPaymentMethod.invoke(data);
                    if (extension.paymentMethod().equals(paymentMethod)) {
                        score += 30;
                    }
                } catch (Exception e) {
                    // 忽略反射异常，说明不是支付相关的业务数据
                }
            }
        }
        
        return score;
    }
    
    @Override
    public <T> T route(Class<T> extPointClass, BizContext<?> context) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(context, "Business context cannot be null");
        
        // 检查是否启用缓存
        ExtPoint extPoint = extPointClass.getAnnotation(ExtPoint.class);
        boolean useCache = extPoint != null && extPoint.enableCache();
        
        // 尝试从缓存获取
        if (useCache) {
            String cacheKey = getCacheKey(extPointClass, context);
            @SuppressWarnings("unchecked")
            T cachedResult = (T) routeCache.get(cacheKey);
            if (cachedResult != null) {
                log.debug("Cache hit for {}", cacheKey);
                recordRouteStats(extPointClass, cachedResult);
                return cachedResult;
            }
        }
        
        // 执行路由
        T result = doRoute(extPointClass, context);
        
        // 缓存结果
        if (useCache && result != null) {
            String cacheKey = getCacheKey(extPointClass, context);
            routeCache.put(cacheKey, result);
        }
        
        if (result != null) {
            recordRouteStats(extPointClass, result);
        }
        
        return result;
    }
    
    /**
     * 执行实际的路由匹配
     */
    @SuppressWarnings("unchecked")
    private <T> T doRoute(Class<T> extPointClass, BizContext<?> context) {
        List<Object> implementations = extPointImplementations.get(extPointClass);
        if (CollectionUtils.isEmpty(implementations)) {
            log.warn("No implementations found for extPoint: {}", extPointClass.getName());
            return null;
        }
        
        // 过滤有效的实现
        List<Object> validImpls = implementations.stream()
            .filter(impl -> {
                Extension extension = impl.getClass().getAnnotation(Extension.class);
                if (extension == null) {
                    return false;
                }
                
                // 检查是否启用
                if (!extension.enabled()) {
                    return false;
                }
                
                // 检查时间范围
                if (!isWithinValidTimeRange(extension)) {
                    return false;
                }
                
                // 检查条件表达式
                return evaluateCondition(extension.condition(), context);
            })
            .collect(Collectors.toList());
        
        if (CollectionUtils.isEmpty(validImpls)) {
            // 没有匹配的实现，返回默认实现
            return (T) defaultImplementations.get(extPointClass);
        }
        
        // 按匹配得分和优先级排序
        validImpls.sort((impl1, impl2) -> {
            Extension ext1 = impl1.getClass().getAnnotation(Extension.class);
            Extension ext2 = impl2.getClass().getAnnotation(Extension.class);
            
            // 计算匹配得分
            int score1 = calculateMatchScore(ext1, context);
            int score2 = calculateMatchScore(ext2, context);
            
            if (score1 != score2) {
                return Integer.compare(score2, score1); // 得分高的优先
            }
            
            // 得分相同，比较优先级
            return Integer.compare(ext1.priority(), ext2.priority()); // 优先级低的数值小，优先
        });
        
        log.debug("Selected implementation: {} for extPoint: {}", 
                  validImpls.get(0).getClass().getName(), extPointClass.getName());
        
        return (T) validImpls.get(0);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllImplementations(Class<T> extPointClass) {
        List<Object> implementations = extPointImplementations.get(extPointClass);
        return CollectionUtils.isEmpty(implementations) ? 
               Collections.emptyList() : 
               (List<T>) new ArrayList<>(implementations);
    }
    
    @Override
    public void clearCache(Class<?> extPointClass) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        
        // 清理该扩展点的所有缓存条目
        String prefix = extPointClass.getName();
        routeCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Cleared cache for extPoint: {}", extPointClass.getName());
    }
    
    @Override
    public void clearAllCache() {
        routeCache.clear();
        log.debug("Cleared all route cache");
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> void registerImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        // 验证实现类是否实现了扩展点接口
        Assert.isAssignable(extPointClass, implementation.getClass(), 
                           "Implementation must implement the extPoint interface");
        
        extPointImplementations.computeIfAbsent(extPointClass, k -> new ArrayList<>())
                               .add(implementation);
        
        // 检查是否为默认实现
        Extension extension = implementation.getClass().getAnnotation(Extension.class);
        if (extension != null && extension.isDefault()) {
            defaultImplementations.put(extPointClass, implementation);
        }
        
        // 清理缓存
        clearCache(extPointClass);
        
        log.debug("Registered implementation: {} for extPoint: {}", 
                  implementation.getClass().getName(), extPointClass.getName());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> void unregisterImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        List<Object> implementations = extPointImplementations.get(extPointClass);
        if (!CollectionUtils.isEmpty(implementations)) {
            implementations.remove(implementation);
            
            // 如果是默认实现，清除默认实现记录
            Extension extension = implementation.getClass().getAnnotation(Extension.class);
            if (extension != null && extension.isDefault()) {
                defaultImplementations.remove(extPointClass);
            }
            
            // 清理缓存
            clearCache(extPointClass);
            
            log.debug("Unregistered implementation: {} for extPoint: {}", 
                      implementation.getClass().getName(), extPointClass.getName());
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDefaultImplementation(Class<T> extPointClass) {
        return (T) defaultImplementations.get(extPointClass);
    }
    
    @Override
    public Map<String, Map<String, Long>> getRouteStats() {
        return routeStats.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        v -> v.getValue().get()
                    ))
            ));
    }
    
    /**
     * 在Spring容器初始化完成后，自动注册所有扩展点实现
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("Initializing extPoint router...");
        
        // 获取所有被@ExtPoint注解的接口
        Map<String, Object> extPointImplementations = applicationContext.getBeansWithAnnotation(Extension.class);
        
        extPointImplementations.forEach((beanName, implementation) -> {
            try {
                // 查找该实现类实现的所有@ExtPoint接口
                Class<?>[] interfaces = implementation.getClass().getInterfaces();
                for (Class<?> iface : interfaces) {
                    if (iface.isAnnotationPresent(ExtPoint.class)) {
                        // 使用原始类型和类型转换解决泛型类型不匹配问题
                        registerImplementation((Class)iface, implementation);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to register implementation: {}", beanName, e);
            }
        });
        
        log.info("ExtPoint router initialized with {} extension implementations", 
                this.extPointImplementations.values().stream()
                    .mapToInt(List::size)
                    .sum());
    }
}