package com.bone.engine.extension.core.proxy;

import com.bone.core.util.ReflectionUtil;
import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.core.invoker.ExtPointInvocationHandler;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import com.bone.engine.extension.support.repository.ExtensionRepositoryFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 扩展点代理类
 * ExtensionPointProxy
 *
 * @param <T>
 * @author renhui.trh 2023-10-30
 */
@Slf4j
public class ExtensionPointProxy<T> implements InvocationHandler, Serializable {
    @Serial
    private static final long serialVersionUID = -3724728412955529860L;
    private static final String EXT_POINT_ROUTER = "extensionPointRouter";
    private static final String EXT_POINT_REPOSITORY = "extensionRepository";
    private final Class<T> extensionPoint;
    private final Map<String, Object> attrs;
    private final ExtensionPointRouter extensionPointRouter;



    public ExtensionPointProxy(Class<T> extensionPoint, Map<String, Object> attrs) {
        this.extensionPoint = extensionPoint;
        this.attrs = attrs;
        Class<?> extPointRouterClazz = (Class<?>) attrs.get(EXT_POINT_ROUTER);
        this.extensionPointRouter = getExtensionPointRouter(extPointRouterClazz);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        BizContext bizContext = ExtensionContextManager.getCurrentContext();
        Object extension = extensionPointRouter.route(extensionPoint, bizContext);
        return ExtPointInvocationHandler.invoke(extension, method, args);
    }

    private ExtensionPointRouter getExtensionPointRouter(Class<?> extPointRouterClazz) {
        Class<?> extensionRepositoryClazz = (Class<?>) attrs.get(EXT_POINT_REPOSITORY);
        ExtensionRepository extensionRepository = ExtensionRepositoryFactory.create(extensionRepositoryClazz);
        return (ExtensionPointRouter) ReflectionUtil.newInstance(extPointRouterClazz,
                new Class<?>[]{ExtensionRepository.class},
                new Object[]{extensionRepository});
    }
}
