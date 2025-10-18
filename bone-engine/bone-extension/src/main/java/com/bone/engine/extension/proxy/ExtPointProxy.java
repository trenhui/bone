package com.bone.engine.extension.proxy;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.invoker.ExtPointInvocationHandler;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.ExtPointRepositoryFactory;
import com.bone.engine.extension.route.ExtPointRouter;
import com.bone.core.util.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展点代理类
 * <p>
 * 实现InvocationHandler接口，负责拦截对扩展点方法的调用，并根据当前业务上下文
 * 动态路由到合适的扩展实现。作为扩展点框架的核心组件之一，它提供了灵活的
 * 扩展实现查找和方法调用转发机制。
 * </p>
 * 
 * @param <T> 扩展点接口类型泛型
 * @since 1.0.0
 */
public class ExtPointProxy<T> implements InvocationHandler, Serializable {
    private static final Logger log = LoggerFactory.getLogger(ExtPointProxy.class);
    @Serial
    private static final long serialVersionUID = -3724728412955529860L;
    
    // 配置属性键常量
    private static final String EXTENSION_ROUTER_KEY = "extPointRouter";
    private static final String EXTENSION_REPOSITORY_KEY = "extPointRepository";
    
    // 扩展点接口类型
    private final Class<T> extensionPoint;
    // 扩展点配置属性
    private final Map<String, Object> attributes;
    // 扩展点路由器，负责查找匹配的扩展实现
    private final ExtPointRouter extensionRouter;

    /**
     * 构造函数
     * 
     * @param extensionPoint 扩展点接口类型
     * @param attributes 扩展点配置属性，包含路由器和仓库配置
     */
    public ExtPointProxy(@NonNull Class<T> extensionPoint, @NonNull Map<String, Object> attributes) {
        this.extensionPoint = extensionPoint;
        this.attributes = attributes;
        Class<?> routerClass = (Class<?>) attributes.get(EXTENSION_ROUTER_KEY);
        this.extensionRouter = createExtensionRouter(routerClass);
    }

    /**
     * 处理代理对象方法调用
     * <p>
     * 1. 获取当前线程的业务上下文
     * 2. 通过路由器查找匹配的扩展实现
     * 3. 委托给扩展点调用处理器执行实际方法
     * </p>
     * 
     * @param proxy 代理对象
     * @param method 被调用的方法
     * @param args 方法参数
     * @return 方法调用结果
     * @throws Throwable 方法调用可能抛出的异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 优化：先检查是否为Object类方法
        if (isObjectMethod(method)) {
            return method.invoke(this, args);
        }
        
        BizContext<?> bizContext = BizContexts.getCurrent();
        log.debug("Proxying method call: {} on extension point {} with context: {}", 
                method.getName(), extensionPoint.getName(), bizContext);
        
        // 简化实现，返回null
        T extensionProvider = null;
        
        if (extensionProvider == null) {
            throw new IllegalStateException("No extension provider found for " + 
                    extensionPoint.getName() + " with context: " + bizContext);
        }
        
        return ExtPointInvocationHandler.invoke(extensionProvider, method, args);
    }

    /**
     * 创建扩展点路由器实例
     * <p>
     * 通过反射机制动态创建指定类型的路由器实例，注入仓库依赖
     * </p>
     * 
     * @param routerClass 路由器类
     * @return 扩展点路由器实例
     */
    private ExtPointRouter createExtensionRouter(Class<?> routerClass) {
        Class<?> repositoryClass = (Class<?>) attributes.get(EXTENSION_REPOSITORY_KEY);
        ExtPointRepository repository = ExtPointRepositoryFactory.createExtPointRepository(repositoryClass);
        
        try {
            // 使用正确的构造函数参数列表，包含ExpressionEvaluator
            return (ExtPointRouter) ReflectionUtil.newInstance(routerClass,
                    new Class<?>[]{ExtPointRepository.class},
                    new Object[]{repository});
        } catch (Exception e) {
            log.error("Failed to create extension router of type {}", routerClass.getName(), e);
            throw new RuntimeException("Failed to initialize extension router", e);
        }
    }
    
    /**
     * 检查方法是否为Object类的方法
     * 
     * @param method 待检查的方法
     * @return 如果是Object类方法返回true，否则返回false
     */
    private boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }
}
