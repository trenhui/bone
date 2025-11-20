package com.bone.engine.extension.support.context;

import org.springframework.lang.Nullable;

/**
 * 上下文管理器接口，支持try-with-resources模式和链式调用
 */
public interface ExtensionScope extends AutoCloseable {
    /**
     * 获取当前业务上下文
     *
     * @return 当前业务上下文
     */
    BizContext<?> getCurrent();

    /**
     * 设置业务上下文属性，支持链式调用
     *
     * @param key   属性键名
     * @param value 属性值
     * @return 当前上下文管理器实例
     */
    ExtensionScope withAttribute(String key, @Nullable Object value);

    @Override
    void close();
}