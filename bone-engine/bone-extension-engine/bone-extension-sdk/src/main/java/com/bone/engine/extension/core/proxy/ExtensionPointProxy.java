package com.bone.engine.extension.core.proxy;

import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.core.invoker.ExtPointInvocationHandler; // 导入增强型调用处理器
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/**
 * 扩展点动态代理（终极优化版）
 *
 * <p>核心组件：负责拦截扩展点接口方法的调用，识别业务上下文， 并通过 Router 路由到具体的扩展实现进行调用转发。 * 核心优化： 1. 使用 ObjectProvider 替代
 * volatile/synchronized，保证线程安全和延迟加载。 2. 使用 cachedRouter 字段，在第一次获取后缓存结果，后续调用性能极致。
 */
@Slf4j
public class ExtensionPointProxy<T> implements InvocationHandler, Serializable {

  @Serial private static final long serialVersionUID = -3724728412955529860L;

  private final Class<T> extensionPoint;

  // 1. 核心：使用 ObjectProvider 延迟获取 Router，transient 避免序列化
  private final transient ObjectProvider<ExtensionPointRouter> routerProvider;

  // 2. 缓存：缓存已获取的 Router 实例，实现无锁 O(1) 访问
  private transient ExtensionPointRouter cachedRouter;

  public ExtensionPointProxy(Class<T> extensionPoint, ApplicationContext applicationContext) {
    this.extensionPoint = Objects.requireNonNull(extensionPoint, "扩展点接口不能为空");
    // 在构造时只获取 Provider，不会触发 Bean 初始化，避免循环依赖
    this.routerProvider = applicationContext.getBeanProvider(ExtensionPointRouter.class);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // 1. Object 方法：基于代理实例身份，避免误用 handler 的 Object 语义
    if (method.getDeclaringClass() == Object.class) {
      return switch (method.getName()) {
        case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "ExtensionPointProxy$"
            + extensionPoint.getSimpleName()
            + '@'
            + Integer.toHexString(System.identityHashCode(proxy));
        default -> method.invoke(this, args);
      };
    }

    // 2. 获取 BizContext（双保险：ThreadLocal 优先，参数其次）
    BizContext<?> bizContext = ExtensionContextManager.getCurrentContext();
    if (bizContext == null) {
      bizContext = extractBizContextFromArgs(args);
    }
    if (bizContext == null) {
      throw new IllegalStateException(
          String.format(
              "BizContext 未找到！请通过 ExtensionContextManager.setCurrentContext() 或方法参数传入 | 接口: %s | 方法: %s",
              extensionPoint.getName(), method.getName()));
    }

    // 3. 获取 Router（无锁缓存 + 延迟加载）
    ExtensionPointRouter router = getRouter();

    // 4. 路由到扩展实现
    T extension = router.route(extensionPoint, bizContext);
    if (extension == null) {
      throw new IllegalStateException(
          String.format(
              "路由失败：未找到匹配的扩展实现 | 扩展点: %s | 上下文: %s",
              extensionPoint.getName(),
              bizContext.toString()) // 使用 toString 简化，或调用 bizContext.buildSummary()
          );
    }

    // 5. 执行目标方法
    // 使用 ExtPointInvocationHandler.invoke 替代原生反射，以获得统一的性能监控和异常处理能力。
    return ExtPointInvocationHandler.invoke(extension, method, args, extensionPoint.getName());
  }

  /** 获取 Router 实例（无锁缓存逻辑） 仅在第一次调用时通过 ObjectProvider 触发 Spring 容器查找。 */
  private ExtensionPointRouter getRouter() {
    ExtensionPointRouter router = cachedRouter;
    if (router == null) {
      // 第一次查找：ObjectProvider.getIfAvailable() 是线程安全的，
      // 它会原子性地获取或创建单例 Bean。
      router = routerProvider.getIfAvailable();
      if (router == null) {
        throw new IllegalStateException("ExtensionPointRouter Bean 未找到，请检查 Spring 容器配置。");
      }
      // 缓存结果，无需担心线程安全问题，因为 router 始终是同一个单例对象
      cachedRouter = router;
    }
    return router;
  }

  /** 从方法参数中提取 BizContext（支持显式传参） */
  private BizContext<?> extractBizContextFromArgs(Object[] args) {
    if (args == null || args.length == 0) return null;
    for (Object arg : args) {
      // 检查参数是否为 BizContext 的实例
      if (arg instanceof BizContext<?> ctx) {
        return ctx;
      }
    }
    return null;
  }

  /** 序列化安全处理：防止在分布式场景下，代理对象被序列化传输后 由于 transient 字段丢失，导致无法获取 Spring Bean。 */
  private Object readResolve() {
    // 如果代理被序列化，说明它离开了创建它的 Spring 容器
    // 应该返回一个包含错误信息的对象，防止后续方法调用抛出空指针
    return new DeserializedProxyError<>(extensionPoint);
  }

  /** 反序列化后的代理错误提示，用于阻止在非 Spring 环境下使用代理 */
  private static class DeserializedProxyError<T> implements InvocationHandler, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final Class<T> extensionPoint;

    DeserializedProxyError(Class<T> extensionPoint) {
      this.extensionPoint = extensionPoint;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      throw new IllegalStateException(
          String.format(
              "扩展点代理（%s）不支持序列化后使用。请在 Spring 容器内通过 Factory 重新获取代理。", extensionPoint.getName()));
    }
  }
}
