package com.bone.engine.extension.proxy;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.config.ExtensionConfigManager;
import com.bone.engine.extension.lifecycle.ExtensionLifecycle;
import com.bone.engine.extension.router.ExtPointRouter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
// 移除不存在的导入

/**
 * 扩展点代理工厂
 * <p>
 * 负责创建扩展点接口的动态代理实例，实现扩展点的路由和调用
 * <strong>核心功能：</strong>
 * <ul>
 *   <li>为扩展点接口创建动态代理</li>
 *   <li>处理扩展点方法调用的路由选择</li>
 *   <li>集成事件发布机制</li>
 *   <li>支持缓存优化</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtPointProxyFactory implements ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;
    
    @Autowired
    private ExtPointRouter extPointRouter;
    
    // 事件发布器被移除，因为ExtensionEventPublisher类不存在
    
    @Autowired
    private ExtensionConfigManager configManager;
    
    @Autowired(required = false)
    private ExtensionLifecycle defaultLifecycle;

    // 代理实例缓存
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();
    
    // 扩展点生命周期实例缓存
    private final Map<Object, ExtensionLifecycle> lifecycleCache = new ConcurrentHashMap<>();
    
    private boolean enableCache = true;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化配置
        configManager.init();
    }

    /**
     * 创建扩展点代理实例
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> extPointInterface) {
        // 验证是否是有效的扩展点接口
        validateExtPointInterface(extPointInterface);
        
        // 检查缓存
        if (enableCache && proxyCache.containsKey(extPointInterface)) {
            return (T) proxyCache.get(extPointInterface);
        }
        
        // 创建代理实例
        T proxy = (T) Proxy.newProxyInstance(
                extPointInterface.getClassLoader(),
                new Class<?>[]{extPointInterface},
                new ExtPointInvocationHandler(extPointInterface)
        );
        
        // 缓存代理实例
        if (enableCache) {
            proxyCache.put(extPointInterface, proxy);
        }
        
        return proxy;
    }

    /**
     * 验证是否是有效的扩展点接口
     */
    private void validateExtPointInterface(Class<?> extPointInterface) {
        if (!extPointInterface.isInterface()) {
            throw new IllegalArgumentException("ExtPoint must be an interface");
        }
        
        if (!extPointInterface.isAnnotationPresent(ExtPoint.class)) {
            throw new IllegalArgumentException("Interface must be annotated with @ExtPoint: " + extPointInterface.getName());
        }
    }

    /**
     * 扩展点方法调用处理器
     */
    private class ExtPointInvocationHandler implements InvocationHandler {
        
        private final Class<?> extPointInterface;
        private final String extPointName;
        private final ExtPoint extPointAnnotation;

        public ExtPointInvocationHandler(Class<?> extPointInterface) {
            this.extPointInterface = extPointInterface;
            this.extPointAnnotation = AnnotationUtils.getAnnotation(extPointInterface, ExtPoint.class);
            this.extPointName = extPointAnnotation.name();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理Object类的方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            
            // 获取当前业务上下文
            BizContext<?> context = getBizContextFromArgs(args);
            
            // 如果仍然没有上下文，创建默认上下文
            if (context == null) {
                // 直接创建默认上下文实例
                context = new BizContext<>(null, null, null, null, null, null, null, null, null, null, null);
            }
            
            // 检查扩展点是否启用
            if (!configManager.isExtPointEnabled(extPointName)) {
                throw new IllegalStateException("Extension point is disabled: " + extPointName);
            }
            
            // 准备路由属性
            Map<String, Object> routeAttributes = new HashMap<>();
            
            // 执行路由前生命周期方法
            ExtensionLifecycle lifecycle = getLifecycleForExtension(null); // 使用默认生命周期进行路由前处理
            lifecycle.beforeRouting(context, extPointInterface, routeAttributes);
            
            // 选择合适的扩展点实现
            Object targetImpl = null;
            try {
                targetImpl = extPointRouter.route(extPointInterface, context);
                if (targetImpl == null) {
                    throw new IllegalStateException("No suitable extension implementation found for: " + extPointName);
                }
            } catch (Exception e) {
                // 移除路由失败事件发布，因为ExtensionEventPublisher类不存在
                
                // 尝试降级处理
                lifecycle = getLifecycleForExtension(null);
                return lifecycle.onFallback(context, method.getName(), args, (Throwable)e);
            }
            
            // 移除路由事件发布，因为ExtensionEventPublisher类不存在
            
            // 获取目标实现的生命周期处理器
            lifecycle = getLifecycleForExtension(targetImpl);
            
            // 记录开始时间
            final long startTime = System.currentTimeMillis();
            
            // 执行前置处理
            lifecycle.beforeInvoke(context, method.getName(), args);
            
            // 移除执行前事件发布，因为ExtensionEventPublisher类不存在
            
            Object result;
            try {
                // 执行环绕处理（支持异步）
                final Method finalMethod = method;
                final Object finalTargetImpl = targetImpl;
                CompletableFuture<Object> future = lifecycle.aroundInvoke(context, finalMethod.getName(), args, () -> {
                    try {
                        return finalMethod.invoke(finalTargetImpl, args);
                    } catch (Throwable e) {
                        throw e instanceof Exception ? (Exception) e : new RuntimeException(e);
                    }
                });
                
                // 同步等待结果（如果需要异步执行，可以直接返回future）
                result = future.get();
                
                // 计算执行时间
                long executionTimeMs = System.currentTimeMillis() - startTime;
                
                // 执行后置处理
                lifecycle.afterInvoke(context, method.getName(), result, executionTimeMs);
                
                // 移除事件发布，因为ExtensionEventPublisher类不存在
            } catch (Exception e) {
                // 计算执行时间
                long executionTimeMs = System.currentTimeMillis() - startTime;
                
                // 提取实际异常
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                Exception ex = cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
                
                // 执行异常处理
                lifecycle.onException(context, method.getName(), ex, executionTimeMs);
                
                // 移除事件发布，因为ExtensionEventPublisher类不存在
                
                // 尝试降级处理
                try {
                    return lifecycle.onFallback(context, method.getName(), args, cause);
                } catch (Exception fallbackEx) {
                    throw cause; // 如果降级也失败，抛出原始异常
                }
            }
            
            return result;
        }
        
        /**
         * 获取扩展点实现对应的生命周期处理器
         */
        private ExtensionLifecycle getLifecycleForExtension(Object extension) {
            if (extension == null) {
                return defaultLifecycle != null ? defaultLifecycle : new ExtensionLifecycle() {
                    @Override public void initialize() {} 
                    @Override public void beforeInvoke(BizContext<?> context, String methodName, Object[] args) {} 
                    @Override public void afterInvoke(BizContext<?> context, String methodName, Object result, long executionTimeMs) {} 
                    @Override public void onException(BizContext<?> context, String methodName, Exception exception, long executionTimeMs) {} 
                    @Override public void destroy() {} 
                };
            }
            
            return lifecycleCache.computeIfAbsent(extension, obj -> {
                if (obj instanceof ExtensionLifecycle) {
                    return (ExtensionLifecycle) obj;
                }
                return defaultLifecycle != null ? defaultLifecycle : new ExtensionLifecycle() {
                    @Override public void initialize() {} 
                    @Override public void beforeInvoke(BizContext<?> context, String methodName, Object[] args) {} 
                    @Override public void afterInvoke(BizContext<?> context, String methodName, Object result, long executionTimeMs) {} 
                    @Override public void onException(BizContext<?> context, String methodName, Exception exception, long executionTimeMs) {} 
                    @Override public void destroy() {} 
                };
            });
        }
        
        /**
         * 从方法参数中提取业务上下文
         */
        private BizContext<?> getBizContextFromArgs(Object[] args) {
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof BizContext) {
                        return (BizContext<?>) arg;
                    }
                }
            }
            return null;
        }

        // 移除重复的方法定义，使用上面的完整实现版本
    }
    
/**
     * 清除生命周期缓存
     */
    public void clearLifecycleCache() {
        lifecycleCache.clear();
    }
    
    /**
     * 设置默认生命周期处理器
     */
    public void setDefaultLifecycle(ExtensionLifecycle defaultLifecycle) {
        this.defaultLifecycle = defaultLifecycle;
        // 清除缓存以便重新应用新的默认生命周期
        clearLifecycleCache();
    }

    /**
     * 工厂Bean实现，用于在Spring容器中注册扩展点代理
     */
    public static class ExtPointFactoryBean<T> implements FactoryBean<T> {
        
        private final Class<T> extPointInterface;
        private final ExtPointProxyFactory proxyFactory;
        
        public ExtPointFactoryBean(Class<T> extPointInterface, ExtPointProxyFactory proxyFactory) {
            this.extPointInterface = extPointInterface;
            this.proxyFactory = proxyFactory;
        }

        @Override
        public T getObject() throws Exception {
            return proxyFactory.createProxy(extPointInterface);
        }

        @Override
        public Class<?> getObjectType() {
            return extPointInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    /**
     * 清理代理缓存
     */
    public void clearCache() {
        proxyCache.clear();
        clearLifecycleCache();
    }
}