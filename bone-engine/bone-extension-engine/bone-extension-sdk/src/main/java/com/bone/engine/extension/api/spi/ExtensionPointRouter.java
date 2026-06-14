package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 统一的扩展点路由器接口（精简版）
 *
 * <p>基于业界最佳实践精简接口，移除无用方法
 */
public interface ExtensionPointRouter {

  /**
   * 核心路由方法 - 为扩展点选择实现
   *
   * @param extPointClass 扩展点接口类
   * @param context 业务上下文
   * @return 匹配的扩展实现，找不到则返回null（由调用方决定处理方式）
   */
  @Nullable
  <T> T route(@NonNull Class<T> extPointClass, @NonNull BizContext context);

  /**
   * 预热指定扩展点 - 避免冷启动性能问题
   *
   * @param extPointClass 扩展点接口类
   */
  void warmup(@NonNull Class<?> extPointClass);

  /**
   * 清理指定扩展点缓存 - 支持热更新
   *
   * @param extPointClass 扩展点接口类
   */
  void clearCache(@NonNull Class<?> extPointClass);

  /**
   * 获取默认实现 - 契约保证，扩展点必须有默认实现
   *
   * @param extPointClass 扩展点接口类
   * @return 默认实现，找不到则抛出异常
   */
  <T> T getDefaultImplementation(@NonNull Class<T> extPointClass);
}
