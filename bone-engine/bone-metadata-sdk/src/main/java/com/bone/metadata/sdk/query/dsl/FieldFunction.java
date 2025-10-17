package com.bone.metadata.sdk.query.dsl;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 字段函数式接口，用于从实体中提取字段值
 * 继承Serializable以支持序列化和缓存
 * @param <T> 实体类型
 * @param <V> 字段值类型
 */
@FunctionalInterface
public interface FieldFunction<T, V> extends Function<T, V>, Serializable {
    // 函数式接口，无额外方法
}