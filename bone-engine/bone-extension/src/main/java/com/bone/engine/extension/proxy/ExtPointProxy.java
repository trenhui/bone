package com.bone.engine.extension.proxy;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContextUtils;
import com.bone.engine.extension.invoker.ExtPointInvocationHandler;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.ExtPointRepositoryFactory;
import com.bone.engine.extension.route.ExtPointRouter;
import com.bone.core.util.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 扩展点代理类
 * ExtPointProxy
 *
 * @param <T>
 * @author renhui.trh 2023-10-30
 */
@Slf4j
public class ExtPointProxy<T> implements InvocationHandler, Serializable {
    @Serial
    private static final long serialVersionUID = -3724728412955529860L;
    private static final String EXT_POINT_ROUTER = "extPointRouter";
    private static final String EXT_POINT_REPOSITORY = "extPointRepository";
    private final Class<T> extPoint;
    private final Map<String, Object> attrs;
    private final ExtPointRouter extPointRouter;


    public ExtPointProxy(Class<T> extPoint, Map<String, Object> attrs) {
        this.extPoint = extPoint;
        this.attrs = attrs;
        Class<?> extPointRouterClazz = (Class<?>) attrs.get(EXT_POINT_ROUTER);
        this.extPointRouter = getExtPointRouter(extPointRouterClazz);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        BizContext bizContext = BizContextUtils.getCurrentContext();
        Object extProvider = extPointRouter.locateExtProvider(extPoint, bizContext);
        return ExtPointInvocationHandler.invoke(extProvider, method, args);
    }

    private ExtPointRouter getExtPointRouter(Class<?> extPointRouterClazz) {
        Class<?> extPointRepository = (Class<?>) attrs.get(EXT_POINT_REPOSITORY);
        ExtPointRepository extPointRepo = ExtPointRepositoryFactory.createExtPointRepository(extPointRepository);
        return (ExtPointRouter) ReflectionUtil.newInstance(extPointRouterClazz,
                new Class<?>[]{ExtPointRepository.class},
                new Object[]{extPointRepo});
    }
}
