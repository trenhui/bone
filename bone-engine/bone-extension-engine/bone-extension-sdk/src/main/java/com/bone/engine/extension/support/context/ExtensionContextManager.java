package com.bone.engine.extension.support.context;

import com.bone.core.threadlocal.TransmittableThreadLocal;
import com.bone.engine.extension.support.extractor.BizParamExtractor;
import com.bone.engine.extension.support.extractor.ReflectionBizParamExtractor;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * 业务上下文服务类，提供静态方法管理业务上下文
 *
 * <p>基于TransmittableThreadLocal实现线程安全的上下文管理，支持try-with-resources模式 提供流畅的API设计，支持在上下文创建时直接链式设置属性
 *
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExtensionContextManager {
  private static final Logger log = LoggerFactory.getLogger(ExtensionContextManager.class);
  // 使用TransmittableThreadLocal确保线程池环境下的上下文传递
  private static final TransmittableThreadLocal<BizContext<?>> CONTEXT_HOLDER =
      new TransmittableThreadLocal<>();

  public static void setCurrentContext(BizContext<?> context) {
    Objects.requireNonNull(context, "Context cannot be null");
    CONTEXT_HOLDER.set(context);
    log.debug("Business context updated | requestId={}", context.getRequestId());
  }

  /**
   * 创建一个新的业务上下文并返回上下文管理器
   *
   * <p>支持try-with-resources模式，自动管理上下文的生命周期
   *
   * @return 上下文管理器，支持链式调用设置属性
   */
  public static ExtensionScope withContext() {
    // 使用 builder() 方法创建构建器，然后构建 BizContext<Void>
    BizContext<Void> context =
        BizContext.<Void>builder().bizCode("DEFAULT").scenario("GENERAL").build();
    return with(context);
  }

  /**
   * 使用指定的业务上下文创建上下文管理器
   *
   * @param context 业务上下文
   * @return 上下文管理器
   */
  public static ExtensionScope with(BizContext<?> context) {
    Objects.requireNonNull(context, "Business context must not be null");

    // 保存旧上下文用于恢复
    BizContext<?> oldContext = CONTEXT_HOLDER.get();
    // 设置新上下文
    CONTEXT_HOLDER.set(context);
    log.debug("Business context created and set");
    // 返回支持链式调用的上下文管理器
    return new DefaultExtensionScope(context, oldContext);
  }

  /**
   * 使用租户编码创建上下文
   *
   * @param tenantCode 租户编码
   * @return 上下文管理器
   */
  public static ExtensionScope withTenant(String tenantCode) {
    BizContext<Void> context =
        BizContext.<Void>builder().tenant(tenantCode).scenario("GENERAL").build();
    return with(context);
  }

  /**
   * 使用业务编码创建上下文
   *
   * @param bizCode 业务编码
   * @return 上下文管理器
   */
  public static ExtensionScope withBusiness(String bizCode) {
    BizContext<Void> context =
        BizContext.<Void>builder().bizCode(bizCode).scenario("GENERAL").build();
    return with(context);
  }

  /**
   * 使用租户和业务编码创建上下文
   *
   * @param tenantCode 租户编码
   * @param bizCode 业务编码
   * @return 上下文管理器
   */
  public static ExtensionScope with(String tenantCode, String bizCode) {
    BizContext<Void> context =
        BizContext.<Void>builder().tenant(tenantCode).bizCode(bizCode).build();
    return with(context);
  }

  /**
   * 获取当前上下文（如果存在）
   *
   * @return 当前上下文，可能为null
   */
  public static BizContext<?> getCurrent() {
    return CONTEXT_HOLDER.get();
  }

  /**
   * 获取当前线程的业务上下文 兼容旧版本API
   *
   * @return 当前业务上下文，如果不存在则返回null
   */
  @Nullable
  public static BizContext<?> getCurrentContext() {
    return getCurrent();
  }

  /**
   * 获取当前线程的业务上下文，如果不存在则抛出异常
   *
   * @return 当前业务上下文
   * @throws IllegalStateException 如果上下文不存在
   */
  public static BizContext<?> getRequiredCurrentContext() {
    BizContext<?> context = getCurrentContext();
    if (context == null) {
      throw new IllegalStateException("No business context found in current thread");
    }
    return context;
  }

  /** 清除当前线程的业务上下文 */
  public static void clearContext() {
    CONTEXT_HOLDER.remove();
    log.debug("Business context cleared");
  }

  /**
   * 检查当前线程是否有业务上下文
   *
   * @return 是否有业务上下文
   */
  public static boolean hasContext() {
    return CONTEXT_HOLDER.get() != null;
  }

  /**
   * 从数据对象构建上下文
   *
   * @param data 数据对象
   * @return 构建的上下文
   */
  public static <T> BizContext<T> fromData(T data) {
    Objects.requireNonNull(data, "Data object must not be null");

    // 使用反射提取器，这里需要类型安全的调用
    BizParamExtractor extractor = new ReflectionBizParamExtractor();

    // 使用类型安全的提取方法
    String tenantCode = extractTenantCode(extractor, data);
    String bizCode = extractBizCode(extractor, data);
    String useCase = extractUseCase(extractor, data);
    String scenario = extractScenario(extractor, data);

    // 创建上下文
    return BizContext.<T>builder()
        .tenant(tenantCode)
        .bizCode(bizCode)
        .useCase(useCase)
        .scenario(scenario)
        .data(data)
        .build();
  }

  /** 类型安全的租户编码提取 */
  @SuppressWarnings("unchecked")
  private static <T> String extractTenantCode(BizParamExtractor extractor, T data) {
    try {
      // 这里需要类型安全的调用，但由于BizParamExtractor可能是泛型接口
      // 我们可以先尝试使用反射或者将extractor转型为BizParamExtractor<T>
      // 这里假设extractor可以处理任何类型
      return extractor.getTenantCode(data);
    } catch (Exception e) {
      log.warn("Failed to extract tenant code from data: {}", data.getClass().getName(), e);
      return null;
    }
  }

  /** 类型安全的业务编码提取 */
  @SuppressWarnings("unchecked")
  private static <T> String extractBizCode(BizParamExtractor extractor, T data) {
    try {
      return extractor.getBizCode(data);
    } catch (Exception e) {
      log.warn("Failed to extract biz code from data: {}", data.getClass().getName(), e);
      return null;
    }
  }

  /** 类型安全的用例提取 */
  @SuppressWarnings("unchecked")
  private static <T> String extractUseCase(BizParamExtractor extractor, T data) {
    try {
      return extractor.getUseCase(data);
    } catch (Exception e) {
      log.warn("Failed to extract use case from data: {}", data.getClass().getName(), e);
      return null;
    }
  }

  /** 类型安全的场景提取 */
  @SuppressWarnings("unchecked")
  private static <T> String extractScenario(BizParamExtractor extractor, T data) {
    try {
      return extractor.getScenario(data);
    } catch (Exception e) {
      log.warn("Failed to extract scenario from data: {}", data.getClass().getName(), e);
      return null;
    }
  }

  /**
   * 获取当前HTTP请求
   *
   * @return 当前HTTP请求，如果不存在则返回null
   */
  @Nullable
  public static Object getRequest() {
    // 这个方法使用反射来获取Request，因为HttpServletRequest是可选的依赖
    try {
      Class<?> requestAttributesClass =
          Class.forName("org.springframework.web.context.request.RequestAttributes");
      Class<?> requestContextHolderClass =
          Class.forName("org.springframework.web.context.request.RequestContextHolder");
      Class<?> servletRequestAttributesClass =
          Class.forName("org.springframework.web.context.request.ServletRequestAttributes");

      Object requestAttributes =
          requestContextHolderClass.getMethod("getRequestAttributes").invoke(null);
      if (requestAttributes != null
          && servletRequestAttributesClass.isInstance(requestAttributes)) {
        return servletRequestAttributesClass.getMethod("getRequest").invoke(requestAttributes);
      }
    } catch (Exception e) {
      log.debug("Web environment not available, cannot get HttpServletRequest", e);
    }
    return null;
  }

  /** 默认的上下文管理器实现，支持链式调用设置属性 */
  private static class DefaultExtensionScope implements ExtensionScope {
    private final BizContext<?> context;
    private final BizContext<?> oldContext;

    /**
     * 构造函数
     *
     * @param context 新的业务上下文
     * @param oldContext 旧的业务上下文（用于恢复）
     */
    public DefaultExtensionScope(BizContext<?> context, BizContext<?> oldContext) {
      this.context = context;
      this.oldContext = oldContext;
    }

    /**
     * 获取当前业务上下文
     *
     * @return 当前业务上下文
     */
    @Override
    public BizContext<?> getCurrent() {
      return context;
    }

    /**
     * 设置业务上下文属性，支持链式调用
     *
     * @param key 属性键名
     * @param value 属性值
     * @return 当前上下文管理器实例
     */
    @Override
    public ExtensionScope withAttribute(String key, @Nullable Object value) {
      context.setAttribute(key, value);
      return this;
    }

    /**
     * 设置维度，支持链式调用
     *
     * @param key 维度键名
     * @param value 维度值
     * @return 当前上下文管理器实例
     */
    public ExtensionScope withDimension(String key, @Nullable String value) {
      context.setDimension(key, value);
      return this;
    }

    /**
     * 设置参数，支持链式调用
     *
     * @param key 参数键名
     * @param value 参数值
     * @return 当前上下文管理器实例
     */
    public ExtensionScope withParam(String key, @Nullable Object value) {
      context.setParam(key, value);
      return this;
    }

    /**
     * 关闭上下文管理器，清理资源
     *
     * <p>恢复旧上下文（如果有）或清除当前上下文
     */
    @Override
    public void close() {
      CONTEXT_HOLDER.remove();
      // 恢复旧上下文（如果有）
      if (oldContext != null) {
        CONTEXT_HOLDER.set(oldContext);
        log.debug("Restored previous business context");
      } else {
        log.debug("Business context cleared");
      }
    }
  }
}
