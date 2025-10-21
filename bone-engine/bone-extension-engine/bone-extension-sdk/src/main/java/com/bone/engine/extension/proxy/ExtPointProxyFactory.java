package com.bone.engine.extension.proxy;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.context.BizContextHolder;
import com.bone.engine.extension.config.ExtensionConfigManager;
import com.bone.engine.extension.config.ExtensionEventPublisher;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    
    @Autowired(required = false)
    private ExtensionEventPublisher eventPublisher;
    
    @Autowired
    private ExtensionConfigManager configManager;

    // 代理实例缓存
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();
    
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

        public ExtPointInvocationHandler(Class<?> extPointInterface) {
            this.extPointInterface = extPointInterface;
            ExtPoint annotation = AnnotationUtils.getAnnotation(extPointInterface, ExtPoint.class);
            this.extPointName = annotation.name();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理Object类的方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            
            // 获取当前业务上下文
            BizContext<?> context = getBizContextFromArgs(args);
            
            // 如果参数中没有上下文，尝试从ThreadLocal获取
            if (context == null) {
                context = BizContextHolder.getCurrentContext();
            }
            
            // 如果仍然没有上下文，创建默认上下文
            if (context == null) {
                context = new BizContext.Builder().build();
            }
            
            // 检查扩展点是否启用
            if (!configManager.isExtPointEnabled(extPointName)) {
                throw new IllegalStateException("Extension point is disabled: " + extPointName);
            }
            
            // 选择合适的扩展点实现
            Object targetImpl = extPointRouter.route(extPointInterface, context);
            if (targetImpl == null) {
                throw new IllegalStateException("No suitable extension implementation found for: " + extPointName);
            }
            
            // 发布路由事件
            if (eventPublisher != null) {
                eventPublisher.publishRouteEvent(extPointInterface, targetImpl, context);
            }
            
            // 发布执行前事件
            if (eventPublisher != null) {
                eventPublisher.publishBeforeEvent(extPointInterface, targetImpl, context);
            }
            
            Object result;
            try {
                // 调用实际实现
                result = method.invoke(targetImpl, args);
                
                // 发布执行后事件
                if (eventPublisher != null) {
                    eventPublisher.publishAfterEvent(extPointInterface, targetImpl, context, result);
                }
            } catch (Exception e) {
                // 发布异常事件
                if (eventPublisher != null) {
                    eventPublisher.publishExceptionEvent(extPointInterface, targetImpl, context, e);
                }
                throw e.getCause() != null ? e.getCause() : e;
            }
            
            return result;
        }
        
        /**
         * 从方法参数中提取业务上下文
         */
        private BizContext<?> getBizContextFromArgs(Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }
            
            for (Object arg : args) {
                if (arg instanceof BizContext) {
                    return (BizContext<?>) arg;
                }
            }
            
            return null;
        }
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
    }
}