package com.bone.metadata.sdk.support.function;


import java.io.Serializable;
import java.util.function.Function;

/**
 * lambda表达式可序列化
 *
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
