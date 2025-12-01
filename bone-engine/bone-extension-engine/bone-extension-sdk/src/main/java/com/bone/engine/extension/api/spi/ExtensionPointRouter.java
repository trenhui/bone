package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * 统一的扩展点路由器接口
 */
public interface ExtensionPointRouter {

    void stop();

    /**
     * 为扩展点选择实现
     */
    @Nullable
    <T> T route(@NonNull Class<T> extPointClass, @NonNull BizContext<?> context);

    /**
     * 预热所有扩展点
     */
    void warmupAll();

    /**
     * 预热指定扩展点
     */
    void warmup(@NonNull Class<?> extPointClass);

    /**
     * 获取默认实现
     */
    @Nullable
    <T> T getDefaultImplementation(@NonNull Class<T> extPointClass);
}