package com.bone.engine.extension.core.executor;

import com.bone.engine.extension.api.spi.ExtensionPointExecutor;
import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.support.context.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步扩展执行器
 * <p>
 * 用于异步执行扩展点，适用于非关键路径
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class AsyncExtensionExecutor implements ExtensionPointExecutor {

    private final ExtensionPointRouter extensionPointRouter;
    private final ExecutorService executorService;

    @Autowired
    public AsyncExtensionExecutor(ExtensionPointRouter extensionPointRouter) {
        this.extensionPointRouter = extensionPointRouter;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public <T> T execute(Object implementation, BizContext<?> context, Method method, Object[] args) throws Throwable {
        // 直接执行扩展实现的方法
        return (T) method.invoke(implementation, args);
    }

    /**
     * 异步执行扩展点
     * <p>
     * 使用CompletableFuture返回异步结果
     * </p>
     *
     * @param extensionPointClass 扩展点类
     * @param context 业务上下文
     * @param <T> 扩展点类型
     * @return CompletableFuture包装的扩展点实例
     */
    public <T> CompletableFuture<T> executeAsync(Class<T> extensionPointClass, BizContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("异步执行扩展点: {}", extensionPointClass.getName());
                return extensionPointRouter.route(extensionPointClass, context);
            } catch (Exception e) {
                log.error("异步执行扩展点失败: {}", extensionPointClass.getName(), e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    /**
     * 异步执行扩展点，不返回结果
     * <p>
     * 适用于只需要执行过程，不需要结果的场景
     * </p>
     *
     * @param extensionPointClass 扩展点类
     * @param context 业务上下文
     * @param <T> 扩展点类型
     */
    @Async
    public <T> void executeAsyncWithoutResult(Class<T> extensionPointClass, BizContext context) {
        try {
            log.debug("异步执行扩展点（无返回结果）: {}", extensionPointClass.getName());
            extensionPointRouter.route(extensionPointClass, context);
        } catch (Exception e) {
            log.error("异步执行扩展点失败: {}", extensionPointClass.getName(), e);
        }
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
