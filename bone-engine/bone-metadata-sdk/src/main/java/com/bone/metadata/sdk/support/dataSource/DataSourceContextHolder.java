package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 线程安全的数据源上下文管理器，用于在不同操作之间管理数据源标识符。
 * 使用ThreadLocal<Deque<String>>实现LIFO栈结构，支持嵌套数据源切换场景，
 * 在复杂的方法调用链中维护正确的上下文传播。
 * 
 * <p>核心特性包括：
 * <ul>
 *   <li>嵌套数据源切换支持与正确清理</li>
 *   <li>同步和异步操作执行与上下文继承</li>
 *   <li>内置故障转移和负载均衡能力</li>
 *   <li>全面的异常处理和回退机制</li>
 *   <li>线程池场景下的上下文快照和恢复</li>
 * </ul>
 */
public final class DataSourceContextHolder {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceContextHolder.class);
    
    /**
     * 默认主数据源标识符
     */
    public static final String DEFAULT_DATASOURCE = "master";
    
    // 支持嵌套切换的LIFO栈结构，预分配更大容量以支持高并发场景
    private static final ThreadLocal<Deque<String>> DATA_SOURCE_HOLDER = 
            ThreadLocal.withInitial(() -> new ArrayDeque<>(32)); // 增加默认容量以减少扩容开销
    
    /**
     * 私有构造函数，防止实例化
     */
    private DataSourceContextHolder() {
        throw new AssertionError("不允许实例化DataSourceContextHolder");
    }
    
    /**
     * 设置当前线程使用的数据源
     * 
     * @param dataSource 数据源标识符
     * @return 设置的数据源标识符
     * @throws NullPointerException 如果数据源标识符为null
     */
    public static String setDataSource(String dataSource) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        // 缓存ThreadLocal获取结果以减少方法调用
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        deque.push(dataSource);
        logger.debug("设置数据源: {}, 栈深度: {}", dataSource, deque.size());
        return dataSource;
    }
    
    /**
     * 弹出并获取当前线程正在使用的数据源标识符
     * 
     * @return 当前数据源标识符
     */
    public static String clearDataSource() {
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        String dataSource = deque.poll();
        logger.debug("清理数据源: {}, 剩余栈深度: {}", dataSource, deque.size());
        if (deque.isEmpty()) {
            DATA_SOURCE_HOLDER.remove(); // 防止内存泄漏
        }
        return dataSource;
    }
    
    /**
     * 获取当前线程正在使用的数据源标识符
     * 
     * @return 当前数据源标识符，如未设置则为null
     */
    public static String getCurrentDataSource() {
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        String current = deque.isEmpty() ? null : deque.peek();
        // 降级为TRACE级别以减少日志量
        if (logger.isTraceEnabled()) {
            logger.trace("当前数据源标识符: {}", current);
        }
        return current;
    }
    
    /**
     * 检查是否存在活跃的数据源
     * 
     * @return 是否存在活跃的数据源
     */
    public static boolean hasActiveDataSource() {
        return !DATA_SOURCE_HOLDER.get().isEmpty();
    }
    
    /**
     * 检查数据源上下文是否为空
     * 
     * @return 数据源上下文是否为空
     */
    public static boolean isEmpty() {
        return DATA_SOURCE_HOLDER.get().isEmpty();
    }
    
    /**
     * 快速检查是否使用默认数据源
     * 
     * @return 是否使用默认数据源
     */
    public static boolean isUsingDefaultDataSource() {
        String current = getCurrentDataSource();
        return DEFAULT_DATASOURCE.equals(current) || current == null;
    }
    
    /**
     * 清除当前线程的所有数据源标识符
     */
    public static void clearAllDataSources() {
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        int size = deque.size();
        deque.clear();
        DATA_SOURCE_HOLDER.remove();
        logger.debug("已清除所有数据源上下文，移除了 {} 个条目", size);
    }
    
    /**
     * 获取当前数据源上下文栈深度
     * 用于调试和监控
     * 
     * @return 上下文栈深度
     */
    public static int getContextStackDepth() {
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        return deque.size();
    }
    
    /**
     * 获取当前上下文栈的线程安全副本
     * 
     * @return 上下文栈的副本
     */
    public static Deque<String> getCurrentContextStack() {
        return new ArrayDeque<>(DATA_SOURCE_HOLDER.get());
    }
    
    /**
     * 创建当前线程数据源上下文栈的深拷贝
     * 对于异步操作正确继承父线程数据源上下文至关重要
     * 
     * @return 当前数据源上下文栈的深拷贝
     */
    public static Deque<String> createContextSnapshot() {
        Deque<String> currentContext = DATA_SOURCE_HOLDER.get();
        return new ArrayDeque<>(currentContext); // 创建当前上下文的深拷贝
    }
    
    /**
     * 恢复之前保存的数据源上下文快照
     * 用于向新线程传播上下文或恢复之前的状态
     * 
     * @param snapshot 要恢复的数据源上下文快照
     */
    public static void restoreContextSnapshot(Deque<String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            DATA_SOURCE_HOLDER.remove();
            return;
        }
        
        Deque<String> deque = DATA_SOURCE_HOLDER.get();
        deque.clear();
        deque.addAll(snapshot);
        logger.debug("已恢复数据源上下文快照，深度: {}", deque.size());
    }
    
    /**
     * 安全执行操作，确保正确清理数据源上下文
     * 
     * @param dataSource 数据源标识符
     * @param action 要执行的操作
     * @throws NullPointerException 如果数据源标识符或操作对象为null
     */
    public static void executeInDataSource(String dataSource, Runnable action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        
        try {
            setDataSource(dataSource);
            action.run();
        } catch (Exception e) {
            logger.error("在数据源[{}]中执行操作失败", dataSource, e);
            throw e;
        } finally {
            // 无论成功或失败，只清理此方法设置的数据源上下文
            // 这确保了嵌套数据源调用的正确性
            clearDataSource();
        }
    }
    
    /**
     * 使用指定数据源安全执行带返回值的supplier操作
     * 确保无论执行结果如何，都能正确设置和清理上下文
     * 
     * @param <T> 操作的返回类型
     * @param dataSource 用于执行的数据源标识符
     * @param action 要执行的supplier操作
     * @return supplier操作的结果
     * @throws NullPointerException 如果数据源标识符或操作对象为null
     */
    public static <T> T executeInDataSourceWithResult(String dataSource, Supplier<T> action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        
        try {
            setDataSource(dataSource);
            return action.get();
        } catch (Exception e) {
            logger.error("在数据源[{}]中执行带返回值的操作失败", dataSource, e);
            throw e;
        } finally {
            // 无论成功或失败，只清理此方法设置的数据源上下文
            // 这确保了嵌套数据源调用的正确性和一致的异常处理
            clearDataSource();
        }
    }
    
    /**
     * 异步执行Runnable任务的内部辅助方法
     * 
     * @param dataSource 数据源标识符
     * @param executor 执行器
     * @param action 要执行的操作
     * @param withExecutor 是否使用自定义执行器
     * @return CompletableFuture实例
     */
    private static CompletableFuture<Void> doExecuteAsync(String dataSource, Executor executor, 
                                                        Runnable action, boolean withExecutor) {
        // 保存当前线程的数据源上下文快照
        Deque<String> currentContext = createContextSnapshot();
        
        if (withExecutor) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // 恢复数据源上下文快照
                    restoreContextSnapshot(currentContext);
                    // 在指定数据源上执行操作
                    executeInDataSource(dataSource, action);
                } catch (Exception e) {
                    logger.error("使用自定义执行器在数据源[{}]中异步执行操作失败", dataSource, e);
                    throw e;
                } finally {
                    // 清理当前线程的上下文
                    clearAllDataSources();
                }
            }, executor);
        } else {
            return CompletableFuture.runAsync(() -> {
                try {
                    // 恢复数据源上下文快照
                    restoreContextSnapshot(currentContext);
                    // 在指定数据源上执行操作
                    executeInDataSource(dataSource, action);
                } catch (Exception e) {
                    logger.error("在数据源[{}]中异步执行操作失败", dataSource, e);
                    throw e;
                } finally {
                    // 清理当前线程的上下文
                    clearAllDataSources();
                }
            });
        }
    }
    
    /**
     * 异步执行Supplier任务的内部辅助方法
     * 
     * @param <T> 返回类型
     * @param dataSource 数据源标识符
     * @param executor 执行器
     * @param action 要执行的操作
     * @param withExecutor 是否使用自定义执行器
     * @return 包含操作结果的CompletableFuture实例
     */
    private static <T> CompletableFuture<T> doExecuteAsyncWithResult(String dataSource, Executor executor, 
                                                                   Supplier<T> action, boolean withExecutor) {
        // 保存当前线程的数据源上下文快照
        Deque<String> currentContext = createContextSnapshot();
        
        if (withExecutor) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 恢复数据源上下文快照
                    restoreContextSnapshot(currentContext);
                    // 在指定数据源上执行操作
                    return executeInDataSourceWithResult(dataSource, action);
                } catch (Exception e) {
                    logger.error("使用自定义执行器在数据源[{}]中异步执行带返回值的操作失败", dataSource, e);
                    throw e;
                } finally {
                    // 清理当前线程的上下文
                    clearAllDataSources();
                }
            }, executor);
        } else {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 恢复数据源上下文快照
                    restoreContextSnapshot(currentContext);
                    // 在指定数据源上执行操作
                    return executeInDataSourceWithResult(dataSource, action);
                } catch (Exception e) {
                    logger.error("在数据源[{}]中异步执行带返回值的操作失败", dataSource, e);
                    throw e;
                } finally {
                    // 清理当前线程的上下文
                    clearAllDataSources();
                }
            });
        }
    }
    
    /**
     * 使用指定数据源异步执行runnable操作
     * 保留并传播父线程的数据源上下文到异步任务
     * 
     * @param dataSource 用于执行的数据源标识符
     * @param action 要异步执行的runnable操作
     * @return 用于跟踪异步操作的CompletableFuture
     * @throws NullPointerException 如果数据源标识符或操作对象为null
     */
    public static CompletableFuture<Void> executeAsyncInDataSource(String dataSource, Runnable action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        return doExecuteAsync(dataSource, null, action, false);
    }
    
    /**
     * 在指定数据源上异步执行带返回值的操作
     * 确保异步任务继承父线程的数据源上下文
     * 
     * @param <T> 返回类型
     * @param dataSource 数据源标识符
     * @param action 要执行的操作
     * @return 包含操作结果的CompletableFuture实例
     * @throws NullPointerException 如果数据源标识符或操作对象为null
     */
    public static <T> CompletableFuture<T> executeAsyncInDataSourceWithResult(String dataSource, Supplier<T> action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        return doExecuteAsyncWithResult(dataSource, null, action, false);
    }
    
    /**
     * 使用自定义线程池在指定数据源上异步执行操作
     * 
     * @param dataSource 数据源标识符
     * @param executor 自定义线程池
     * @param action 要执行的操作
     * @return CompletableFuture实例
     * @throws NullPointerException 如果数据源标识符、执行器或操作对象为null
     */
    public static CompletableFuture<Void> executeAsyncInDataSource(String dataSource, Executor executor, Runnable action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(executor, "执行器不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        return doExecuteAsync(dataSource, executor, action, true);
    }
    
    /**
     * 使用自定义线程池在指定数据源上异步执行带返回值的操作
     * 
     * @param <T> 返回类型
     * @param dataSource 数据源标识符
     * @param executor 自定义线程池
     * @param action 要执行的操作
     * @return 包含操作结果的CompletableFuture实例
     * @throws NullPointerException 如果数据源标识符、执行器或操作对象为null
     */
    public static <T> CompletableFuture<T> executeAsyncInDataSourceWithResult(String dataSource, Executor executor, Supplier<T> action) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(executor, "执行器不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        return doExecuteAsyncWithResult(dataSource, executor, action, true);
    }
    
    /**
     * 安全执行数据源切换，带有异常处理和回退支持
     * 
     * @param dataSource 数据源标识符
     * @param action 要执行的操作
     * @param fallback 回退操作
     * @throws NullPointerException 如果数据源标识符或主操作对象为null
     */
    public static void executeInDataSourceWithFallback(String dataSource, Runnable action, Runnable fallback) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(action, "操作不能为空");
        
        try {
            setDataSource(dataSource);
            action.run();
        } catch (Exception e) {
            logger.error("在数据源[{}]中执行操作失败，尝试回退", dataSource, e);
            if (fallback != null) {
                try {
                    // 清理当前数据源上下文，确保回退使用默认数据源
                    clearDataSource();
                    fallback.run();
                } catch (Exception fallbackEx) {
                    logger.error("执行回退操作失败", fallbackEx);
                    throw fallbackEx;
                }
            } else {
                throw e;
            }
        } finally {
            // 确保清理此方法设置的数据源上下文
            clearDataSource();
        }
    }
    
    /**
     * 安全执行带返回值和回退支持的数据源操作
     * 
     * @param <T> 返回类型
     * @param dataSource 数据源标识符
     * @param supplier 主操作
     * @param fallbackSupplier 回退操作
     * @return 操作结果
     * @throws NullPointerException 如果数据源标识符或主操作对象为null
     */
    public static <T> T executeInDataSourceWithResultAndFallback(String dataSource, Supplier<T> supplier, Supplier<T> fallbackSupplier) {
        Objects.requireNonNull(dataSource, "数据源不能为空");
        Objects.requireNonNull(supplier, "操作不能为空");
        
        try {
            setDataSource(dataSource);
            return supplier.get();
        } catch (Exception e) {
            logger.error("在数据源[{}]中执行带返回值的操作失败，尝试回退", dataSource, e);
            if (fallbackSupplier != null) {
                try {
                    // 清理当前数据源上下文，确保回退使用默认数据源
                    clearDataSource();
                    return fallbackSupplier.get();
                } catch (Exception fallbackEx) {
                    logger.error("执行回退操作失败", fallbackEx);
                    throw fallbackEx;
                }
            }
            throw e;
        } finally {
            // 确保清理此方法设置的数据源上下文
            clearDataSource();
        }
    }
    
    /**
     * 尝试按顺序在多个数据源上执行操作，直到成功为止
     * 实现简单的故障转移机制，当一个数据源失败时尝试下一个
     * 
     * @param <T> 操作的返回类型
     * @param action 要在数据源上执行的supplier操作
     * @param dataSources 要按顺序尝试的数据源列表
     * @return 第一个成功操作的结果
     * @throws NullPointerException 如果操作或数据源列表为null
     * @throws IllegalArgumentException 如果数据源列表为空
     * @throws RuntimeException 如果所有数据源执行尝试都失败
     */
    @SafeVarargs
    public static <T> T executeWithFailover(Supplier<T> action, String... dataSources) {
        Objects.requireNonNull(action, "操作不能为空");
        Objects.requireNonNull(dataSources, "数据源列表不能为空");
        
        if (dataSources.length == 0) {
            throw new IllegalArgumentException("至少需要提供一个数据源");
        }
        
        Exception lastException = null;
        
        for (String dataSource : dataSources) {
            try {
                logger.debug("尝试在数据源[{}]中执行故障转移操作", dataSource);
                return executeInDataSourceWithResult(dataSource, action);
            } catch (Exception e) {
                logger.warn("在数据源[{}]中执行失败，尝试下一个", dataSource, e);
                lastException = e;
            }
        }
        
        throw new RuntimeException("所有数据源执行都失败", lastException);
    }
    
    /**
     * 从提供的列表中随机选择一个数据源来执行操作
     * 使用ThreadLocalRandom实现简单的轮询负载均衡方法
     * 
     * @param <T> 操作的返回类型
     * @param action 要执行的supplier操作
     * @param dataSources 要选择的数据源池
     * @return 在选定数据源上执行的操作结果
     * @throws NullPointerException 如果操作或数据源列表为null
     * @throws IllegalArgumentException 如果数据源列表为空
     */
    @SafeVarargs
    public static <T> T executeWithRandomDataSource(Supplier<T> action, String... dataSources) {
        Objects.requireNonNull(action, "操作不能为空");
        Objects.requireNonNull(dataSources, "数据源列表不能为空");
        
        if (dataSources.length == 0) {
            throw new IllegalArgumentException("至少需要提供一个数据源");
        }
        
        int randomIndex = ThreadLocalRandom.current().nextInt(dataSources.length);
        String selectedDataSource = dataSources[randomIndex];
        logger.debug("随机选择数据源[{}]执行操作，索引: {}", selectedDataSource, randomIndex);
        return executeInDataSourceWithResult(selectedDataSource, action);
    }
}