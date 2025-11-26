package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// com.bone.extension.api.spi.ExtensionPointExecutor
public interface ExtensionPointExecutor {
    <T> T execute(Object implementation, BizContext<?> context, Method method, Object[] args) throws Throwable;

    default <T> CompletableFuture<T> executeAsync(Object impl, BizContext<?> ctx, Method method,
                                                  Object[] args, Class<T> returnType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return returnType.cast(execute(impl, ctx, method, args));
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        });
    }
}