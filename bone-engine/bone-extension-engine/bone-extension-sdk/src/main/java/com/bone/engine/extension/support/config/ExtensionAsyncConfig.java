package com.bone.engine.extension.support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 扩展点异步配置类，提供事件处理相关的异步执行器配置
 * <p>
 * 为扩展点框架的异步事件处理提供专用的线程池配置，确保事件处理不会影响主业务流程
 * 采用合理的线程池参数配置，避免资源耗尽风险
 * 
 * @author renhui.trh
 * @since 1.0.0
 */
@Configuration
public class ExtensionAsyncConfig {
    
    /**
     * 扩展点事件执行器Bean名称
     */
    public static final String EXTENSION_EVENT_EXECUTOR_BEAN_NAME = "extensionEventAsyncExecutor";
    
    /**
     * 创建扩展点事件专用的异步任务执行器
     * 配置合理的线程池参数，适合事件处理的特性
     * 
     * @return 配置好的异步任务执行器
     */
    @Bean(name = EXTENSION_EVENT_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor extensionEventAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数，设置为处理器数量的一半，适合事件处理场景
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() / 2);
        
        // 最大线程数，可根据系统负载情况调整
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors());
        
        // 队列容量，设置合理容量避免内存溢出
        executor.setQueueCapacity(1000);
        
        // 线程名称前缀，便于问题排查
        executor.setThreadNamePrefix("extension-event-");
        
        // 线程存活时间，空闲线程回收时间
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略，当线程池和队列都满时的处理策略
        // 使用CallerRunsPolicy，在调用者线程执行任务，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 初始化线程池
        executor.initialize();
        
        return executor;
    }
}