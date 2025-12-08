package com.bone.engine.extension.support.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 扩展点异步执行配置
 * <p>
 * 提供可配置的线程池，用于扩展点的异步执行和事件处理，
 * 支持通过配置文件调整线程池参数
 * </p>
 */
@Configuration
public class ExtensionAsyncConfig {
    
    private final ExtensionProperties extensionProperties;

    public ExtensionAsyncConfig(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
    }
    
    /**
     * 扩展点事件执行器Bean名称
     */
    public static final String EXTENSION_EVENT_EXECUTOR_BEAN_NAME = "extensionEventAsyncExecutor";
    
    /**
     * 扩展点异步执行器Bean名称
     */
    public static final String EXTENSION_ASYNC_EXECUTOR_BEAN_NAME = "extensionAsyncExecutor";
    
    /**
     * 创建扩展点事件专用的异步任务执行器
     * 配置合理的线程池参数，适合事件处理的特性
     * 
     * @return 配置好的异步任务执行器
     */
    @Bean(name = EXTENSION_EVENT_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor extensionEventAsyncExecutor() {
        ExtensionProperties.Async asyncProperties = extensionProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数，可通过配置文件调整
        executor.setCorePoolSize(asyncProperties.getEventCorePoolSize());
        
        // 最大线程数，可通过配置文件调整
        executor.setMaxPoolSize(asyncProperties.getEventMaxPoolSize());
        
        // 队列容量，可通过配置文件调整
        executor.setQueueCapacity(asyncProperties.getEventQueueCapacity());
        
        // 线程名称前缀，便于问题排查
        executor.setThreadNamePrefix("extension-event-");
        
        // 线程存活时间，可通过配置文件调整
        executor.setKeepAliveSeconds(asyncProperties.getEventKeepAliveSeconds());
        
        // 拒绝策略，当线程池和队列都满时的处理策略
        // 使用CallerRunsPolicy，在调用者线程执行任务，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 初始化线程池
        executor.initialize();
        
        return executor;
    }
    
    /**
     * 扩展点异步执行线程池
     * <p>
     * 核心配置：
     * 1. 核心线程数：默认10，可通过extension.async.core-pool-size配置
     * 2. 最大线程数：默认50，可通过extension.async.max-pool-size配置
     * 3. 队列容量：默认1000，可通过extension.async.queue-capacity配置
     * 4. 线程存活时间：默认60秒，可通过extension.async.keep-alive-seconds配置
     * 5. 线程名称前缀：extension-async-
     * 6. 拒绝策略：CallerRunsPolicy，当队列满时由调用者线程执行
     * </p>
     */
    @Bean(name = EXTENSION_ASYNC_EXECUTOR_BEAN_NAME)
    @ConditionalOnProperty(prefix = "extension.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AsyncTaskExecutor extensionAsyncExecutor() {
        ExtensionProperties.Async asyncProperties = extensionProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数，可通过配置文件调整
        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        // 最大线程数，可通过配置文件调整
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        // 队列容量，可通过配置文件调整
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        // 线程存活时间，可通过配置文件调整
        executor.setKeepAliveSeconds(asyncProperties.getKeepAliveSeconds());
        // 线程名称前缀，便于问题排查
        executor.setThreadNamePrefix("extension-async-");
        // 拒绝策略，当队列满时由调用者线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成后关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 关闭线程池时的最大等待时间
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }
}