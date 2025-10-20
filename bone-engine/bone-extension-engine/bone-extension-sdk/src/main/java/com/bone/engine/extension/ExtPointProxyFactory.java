package com.bone.engine.extension;

import com.bone.engine.extension.config.ExtensionConfigProperties;
import com.bone.engine.extension.invoker.ExtPointInvocationHandler;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.route.ExtPointRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 扩展点代理工厂，负责创建扩展点接口的动态代理实例
 * <p>
 * 采用工厂模式和代理模式，实现扩展点的动态路由和调用
 * <p>
 * 主要功能：
 * <ul>
 *   <li>创建扩展点接口的JDK动态代理</li>
 *   <li>创建类的CGLIB代理（支持非接口扩展）</li>
 *   <li>根据业务上下文动态路由到合适的扩展实现</li>
 *   <li>缓存路由结果，提升性能</li>
 *   <li>提供超时控制和健壮的错误处理</li>
 *   <li>支持性能监控和指标收集</li>
 * </ul>
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 */
@Component
@EnableConfigurationProperties(ExtensionConfigProperties.class)
public class ExtPointProxyFactory implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(ExtPointProxyFactory.class);
    private static final AtomicLong INVOCATION_COUNTER = new AtomicLong(0);
    
    private final ExtPointRepository extPointRepository;
    private final ExtPointRouter extPointRouter;
    private final ExtensionConfigProperties configProperties;
    
    // 缓存方法路由结果，提升性能
    private final ConcurrentHashMap<CacheKey, Object> providerCache = new ConcurrentHashMap<>();
    
    // 缓存创建的代理实例
    private final ConcurrentHashMap<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param extPointRepository 扩展提供者仓库
     * @param extPointRouter 扩展点路由器
     * @param configProperties 扩展点配置属性
     */
    @Autowired
    public ExtPointProxyFactory(ExtPointRepository extPointRepository, 
                               ExtPointRouter extPointRouter,
                               ExtensionConfigProperties configProperties) {
        this.extPointRepository = Objects.requireNonNull(extPointRepository, "ExtPointRepository must not be null");
        this.extPointRouter = Objects.requireNonNull(extPointRouter, "ExtPointRouter must not be null");
        this.configProperties = Objects.requireNonNull(configProperties, "ExtensionConfigProperties must not be null");
        
        log.info("ExtPointProxyFactory initialized with config: cacheEnabled={}, metricsEnabled={}, securityCheckEnabled={}",
                configProperties.isCacheEnabled(), configProperties.isMetricsEnabled(), configProperties.isSecurityCheckEnabled());
    }

    /**
     * 代理方法调用处理
     * 
     * @param proxy 代理对象
     * @param method 被调用的方法
     * @param args 方法参数
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 记录调用开始时间用于性能监控
        final long startTime = System.currentTimeMillis();
        final long invocationId = INVOCATION_COUNTER.incrementAndGet();
        Class<?> extPointType = method.getDeclaringClass();
        String methodName = method.getName();
        
        try {
            // 处理Object类的方法
            if (Object.class.equals(extPointType)) {
                return method.invoke(this, args);
            }

            // 获取当前业务上下文
            BizContext<?> bizContext = getCurrentContext();
            
            // 参数验证
            if (bizContext == null) {
                throw new IllegalArgumentException("Business context cannot be null");
            }

            // 安全检查
            if (configProperties.isSecurityCheckEnabled()) {
                performSecurityCheck(extPointType, method, bizContext);
            }

            // 从缓存获取或定位扩展提供者
            Object extProvider = locateExtension(extPointType, bizContext);
            
            if (extProvider == null) {
                String errorMsg = String.format("No extension provider found for %s with context %s",
                        extPointType.getName(), bizContext.getBusinessIdentity());
                log.error("[ExtPoint-Invocation-{}] Failed to find extension provider: {}", invocationId, errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            
            // 执行扩展点方法调用（支持超时控制）
            return invokeExtensionWithTimeout(extProvider, method, args, invocationId, methodName);
            
        } catch (TimeoutException e) {
            // 处理超时异常
            String errorMsg = String.format("Extension invocation timed out after %dms: %s.%s", 
                    configProperties.getInvocationTimeoutMillis(), extPointType.getName(), methodName);
            log.error("[ExtPoint-Invocation-{}] {}", invocationId, errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            // 统一异常处理
            log.error("[ExtPoint-Invocation-{}] Error invoking extension {}.{}: {}", 
                    invocationId, extPointType.getName(), methodName, e.getMessage(), e);
            
            // 包装原始异常以便于诊断
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                throw new RuntimeException("Error invoking extension method", e);
            }
        } finally {
            // 记录性能指标（如果启用）
            if (configProperties.isMetricsEnabled()) {
                recordPerformanceMetrics(extPointType.getName(), methodName, startTime, System.currentTimeMillis());
            }
        }
    }
    
    /**
     * 获取当前业务上下文
     * 
     * @return 业务上下文对象
     */
    private BizContext<?> getCurrentContext() {
        // 使用BizContextService获取上下文
        BizContext<?> context = ExtensionContextManager.getCurrent();
        if (context == null) {
            // 如果上下文为空，创建默认上下文并记录警告
            log.warn("No business context found, using default context");
            context = new BizContext<>(null, null, null, null, null, new HashMap<>());
        }
        return context;
    }
    
    /**
     * 定位扩展提供者
     *
     * @param extPointType 扩展点类型
     * @param bizContext 业务上下文
     * @return 扩展提供者实例
     */
    @SuppressWarnings("unchecked")
    private Object locateExtension(Class<?> extPointType, BizContext<?> bizContext) {
        // 创建缓存键
        CacheKey cacheKey = new CacheKey(extPointType, bizContext);
        
        // 尝试从缓存获取
        Object provider = providerCache.get(cacheKey);
        if (provider != null) {
            log.trace("Cache hit for extension provider: {} with context {}", 
                    extPointType.getName(), bizContext.getBusinessIdentity());
            return provider;
        }
        
        // 如果缓存未命中，使用路由器定位扩展提供者
        log.trace("Cache miss for extension provider: {} with context {}", 
                extPointType.getName(), bizContext.getBusinessIdentity());
        provider = extPointRouter.locateExtensionProvider((Class<Object>) extPointType, bizContext);
        
        // 缓存结果（仅在非空时）
        if (provider != null) {
            providerCache.put(cacheKey, provider);
        }
        
        return provider;
    }
    
    /**
     * 执行安全检查
     */
    private void performSecurityCheck(Class<?> extPointType, Method method, BizContext<?> bizContext) {
        // 实现安全检查逻辑，例如权限验证、敏感操作检查等
        if (method.isAnnotationPresent(Secure.class)) {
            String requiredPermission = method.getAnnotation(Secure.class).requiredPermission();
            if (requiredPermission != null && !requiredPermission.isEmpty() && 
                    !checkPermission(bizContext, requiredPermission)) {
                throw new SecurityException("Permission denied for operation: " + method.getName());
            }
        }
        log.trace("Security check passed for {}.{}", extPointType.getName(), method.getName());
    }
    
    /**
     * 执行超时控制的扩展点方法调用
     */
    private Object invokeExtensionWithTimeout(Object extProvider, Method method, Object[] args, 
                                             long invocationId, String methodName) throws Throwable {
        // 记录调用日志
        log.debug("[ExtPoint-Invocation-{}] Invoking extension method {} on provider {}", 
                invocationId, methodName, extProvider.getClass().getSimpleName());

        // 如果没有配置超时或超时时间小于等于0，则直接调用
        if (!configProperties.isTimeoutControlEnabled() || 
                configProperties.getInvocationTimeoutMillis() <= 0) {
            return ExtPointInvocationHandler.invoke(extProvider, method, args);
        }

        // 使用CompletableFuture实现超时控制
        CompletableFuture<Object> future = 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return ExtPointInvocationHandler.invoke(extProvider, method, args);
                    } catch (Throwable e) {
                        throw new CompletionException(e);
                    }
                });

        try {
            return future.get(configProperties.getInvocationTimeoutMillis(), 
                    TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true); // 尝试取消任务
            throw new TimeoutException("Extension invocation timed out");
        } catch (CompletionException e) {
            // 解包原始异常
            throw e.getCause();
        }
    }
    
    /**
     * 记录性能指标
     */
    private void recordPerformanceMetrics(String extPointType, String methodName, 
                                        long startTime, long endTime) {
        long executionTime = endTime - startTime;
        String metricKey = String.format("extpoint.%s.%s", extPointType, methodName);
        
        // 这里可以集成各种监控系统，如Prometheus、Micrometer等
        log.debug("Performance metric: {} - execution time: {}ms", metricKey, executionTime);
        
        // 对于慢调用记录警告日志
        if (executionTime > configProperties.getSlowInvocationThresholdMillis()) {
            log.warn("Slow extension invocation detected: {} - took {}ms", 
                    metricKey, executionTime);
        }
    }
    
    /**
     * 检查权限（简化版，实际应集成系统权限框架）
     */
    private boolean checkPermission(BizContext<?> bizContext, String requiredPermission) {
        // 从业务上下文获取权限信息进行验证
        // 这里是一个简化的实现，实际应用中应集成具体的权限管理框架
        Map<String, Object> attributes = bizContext.getAttributes();
        if (attributes != null) {
            Object permissions = attributes.get("user_permissions");
            if (permissions instanceof java.util.Collection) {
                return ((java.util.Collection<?>) permissions).contains(requiredPermission);
            }
        }
        return false;
    }
    
    // 权限检查注解定义
    private @interface Secure {
        String requiredPermission() default "";
    }

    /**
     * 创建接口代理
     *
     * @param interfaceClass 接口类型
     * @return 代理实例
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> interfaceClass) {
        Objects.requireNonNull(interfaceClass, "Interface class must not be null");
        
        // 验证是否为接口
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Target class must be an interface: " + interfaceClass.getName());
        }
        
        // 验证是否为扩展点接口
        if (!interfaceClass.isAnnotationPresent(ExtPoint.class)) {
            log.warn("Creating proxy for non-ExtPoint interface: {}", interfaceClass.getName());
        }
        
        // 从缓存获取或创建新代理
        return (T) proxyCache.computeIfAbsent(interfaceClass, clazz -> {
            log.debug("Creating proxy for extension point interface: {}", clazz.getName());
            return Proxy.newProxyInstance(
                    clazz.getClassLoader(),
                    new Class<?>[]{clazz},
                    this
            );
        });
    }

    /**
     * 创建类代理（使用CGLIB）
     *
     * @param targetClass 目标类
     * @return 代理实例
     */
    @SuppressWarnings("unchecked")
    public <T> T createClassProxy(Class<T> targetClass) {
        Objects.requireNonNull(targetClass, "Target class must not be null");
        
        // 验证不能是final类
        if (java.lang.reflect.Modifier.isFinal(targetClass.getModifiers())) {
            throw new IllegalArgumentException("Cannot proxy final class: " + targetClass.getName());
        }
        
        log.debug("Creating CGLIB proxy for extension point class: {}", targetClass.getName());
        
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                // 处理Object类的方法
                if (method.getDeclaringClass() == Object.class) {
                    return proxy.invokeSuper(obj, args);
                }
                
                // 获取当前业务上下文
                BizContext<?> bizContext = getCurrentContext();
                
                // 获取扩展点接口类型
                Class<?> extPointType = method.getDeclaringClass();
                
                // 定位扩展提供者
                Object extProvider = locateExtension(extPointType, bizContext);
                
                if (extProvider == null) {
                    String errorMsg = String.format("No extension provider found for %s with context %s",
                            extPointType.getName(), bizContext.getBusinessIdentity());
                    log.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
                
                // 记录调用日志
                log.debug("Invoking extension method {} on provider {}", 
                        method.getName(), extProvider.getClass().getSimpleName());
                
                // 直接调用扩展提供者的方法
                return ExtPointInvocationHandler.invoke(extProvider, method, args);
            }
        });
        
        return (T) enhancer.create();
    }

    /**
     * 清除代理缓存
     */
    public void clearCache() {
        providerCache.clear();
        proxyCache.clear();
        log.debug("Extension point proxy caches cleared");
    }

    /**
     * 缓存键内部类
     */
    private static class CacheKey {
        private final Class<?> extPointType;
        private final BizContext<?> context;
        
        public CacheKey(Class<?> extPointType, BizContext<?> context) {
            this.extPointType = extPointType;
            this.context = context;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return extPointType.equals(cacheKey.extPointType) && 
                   context.equals(cacheKey.context);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(extPointType, context);
        }
    }
    
    /**
     * 兼容旧的ExtPointProxy类
     */
    @Deprecated
    public static class ExtPointProxy extends ExtPointProxyFactory {
        
        @Autowired
        public ExtPointProxy(ExtPointRepository extPointRepository) {
            // 使用默认的路由器和配置
            super(extPointRepository, null, null);
        }
    }
}