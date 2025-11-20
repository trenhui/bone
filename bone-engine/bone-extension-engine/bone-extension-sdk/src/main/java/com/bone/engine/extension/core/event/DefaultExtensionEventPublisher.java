package com.bone.engine.extension.core.event;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.config.ExtensionAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.bone.engine.extension.core.router.RouteStatsCollector;

import java.util.EnumSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 基于Spring的默认扩展点事件发布器实现
 * <p>
 * 集成Spring的事件机制，支持同步和异步事件发布，可配置的事件过滤
 * 提供完整的事件生命周期管理和监控能力
 *
 * @author renhui.trh
 * @since 1.0.0
 */
@Component
public class DefaultExtensionEventPublisher implements ExtensionEventPublisher, InitializingBean {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultExtensionEventPublisher.class);
    
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AsyncTaskExecutor taskExecutor;
    
    // 事件发布器总开关
    private volatile boolean enabled = true;
    
    // 启用的事件类型集合
    private final Set<ExtensionEvent.EventType> enabledEventTypes = new CopyOnWriteArraySet<>(EnumSet.allOf(ExtensionEvent.EventType.class));
    
    // 是否异步发布事件
    private boolean asyncPublish = true; // 默认启用异步发布
    
    // 异步任务执行器名称限定符
    private static final String EXTENSION_EVENT_EXECUTOR_BEAN_NAME = ExtensionAsyncConfig.EXTENSION_EVENT_EXECUTOR_BEAN_NAME;
    
    // 失败事件回退队列
    private final ConcurrentLinkedQueue<ExtensionEvent<?>> fallbackEventQueue = new ConcurrentLinkedQueue<>();
    private ExecutorService fallbackExecutor;
    
    // 注入路由统计收集器
    private final RouteStatsCollector routeStatsCollector;
    
    /**
     * 构造函数
     * 
     * @param applicationEventPublisher Spring事件发布器
     */
    @Autowired
    public DefaultExtensionEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        // 创建默认的事件统计收集器实现
        this(applicationEventPublisher, null, null);
    }

    /**
     * 构造函数，支持异步发布
     * 
     * @param applicationEventPublisher Spring事件发布器
     * @param taskExecutor 异步任务执行器
     */
    @Autowired(required = false)
    public DefaultExtensionEventPublisher(ApplicationEventPublisher applicationEventPublisher, 
                                         @Qualifier(ExtensionAsyncConfig.EXTENSION_EVENT_EXECUTOR_BEAN_NAME) AsyncTaskExecutor taskExecutor) {
        // 创建默认的事件统计收集器实现
        this(applicationEventPublisher, taskExecutor, null);
    }
    
    /**
     * 构造函数，支持完整注入
     * 
     * @param applicationEventPublisher Spring事件发布器
     * @param taskExecutor 异步任务执行器
     * @param routeStatsCollector 路由统计收集器
     */
    @Autowired(required = false)
    public DefaultExtensionEventPublisher(ApplicationEventPublisher applicationEventPublisher, 
                                         AsyncTaskExecutor taskExecutor,
                                         RouteStatsCollector routeStatsCollector) {
        Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher must not be null");
        this.applicationEventPublisher = applicationEventPublisher;
        this.taskExecutor = taskExecutor;
        this.routeStatsCollector = routeStatsCollector;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化时检查异步执行器是否可用
        if (taskExecutor != null) {
            log.info("ExtensionEventPublisher initialized with async capability. Task executor: {}", 
                    taskExecutor.getClass().getSimpleName());
            
            // 打印线程池配置信息
            if (taskExecutor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor executor = (ThreadPoolExecutor) taskExecutor;
                log.info("Async task executor configuration - Core pool size: {}, Max pool size: {}, Queue capacity: {}",
                        executor.getCorePoolSize(), executor.getMaximumPoolSize(), 
                        executor.getQueue().size());
            }
            
            // 初始化失败回退处理线程
            initializeFallbackExecutor();
        } else {
            log.warn("ExtensionEventPublisher initialized in sync mode because AsyncTaskExecutor is not available");
            this.asyncPublish = false; // 没有异步执行器时强制使用同步模式
        }
    }
    
    /**
     * 初始化失败事件回退处理线程
     */
    private void initializeFallbackExecutor() {
        fallbackExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "extension-event-fallback-thread");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动后台任务处理回退队列
        fallbackExecutor.submit(this::processFallbackQueue);
    }
    
    /**
     * 处理失败事件回退队列
     */
    private void processFallbackQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ExtensionEvent<?> event = fallbackEventQueue.poll();
                if (event != null) {
                    log.info("Processing fallback event: {}", event.getEventType());
                    try {
                        doPublishEvent(event);
                        log.debug("Successfully published fallback event: {}", event.getEventType());
                    } catch (Exception e) {
                        log.error("Failed to publish fallback event: {}", event.getEventType(), e);
                        // 重新放入队列，但有退避策略
                        Thread.sleep(1000); // 简单的退避策略
                        fallbackEventQueue.offer(event);
                    }
                } else {
                    // 队列为空，短暂休眠避免CPU空转
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Fallback event processing thread interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in fallback event processor", e);
                try {
                    // 发生异常时短暂休眠
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    @Override
    public void publishEvent(ExtensionEvent<?> event) {
        if (!enabled || event == null) {
            return;
        }
        
        // 检查事件类型是否启用
        if (!enabledEventTypes.contains(event.getEventType())) {
            if (log.isTraceEnabled()) {
                log.trace("Event type {} is disabled, skipping publication", event.getEventType());
            }
            return;
        }
        
        boolean isAsync = asyncPublish && taskExecutor != null;
        
        try {
            if (isAsync) {
                // 异步发布事件
                asyncPublishEvent(event);
            } else {
                // 同步发布事件
                doPublishEvent(event);
            }
        } catch (Exception e) {
            // 事件发布失败不应影响主流程，但尝试放入回退队列
            log.error("Failed to publish extension event: {}", event.getEventType(), e);
            addToFallbackQueue(event);
        }
    }
    
    /**
     * 异步发布事件，使用更健壮的异步处理机制
     * @param event 要发布的事件
     */
    private void asyncPublishEvent(ExtensionEvent<?> event) {
        try {
            // 使用CompletableFuture包装异步任务，提供更好的异常处理和任务编排能力
            CompletableFuture.runAsync(() -> {
                try {
                    doPublishEvent(event);
                } catch (Exception e) {
                    log.error("Error in async event publishing for type: {}", event.getEventType(), e);
                    // 将失败的事件添加到回退队列
                    addToFallbackQueue(event);
                }
            }, taskExecutor);
        } catch (TaskRejectedException e) {
            log.warn("Async task rejected, event queue may be full. Switching to fallback mode for event: {}", 
                    event.getEventType(), e);
            // 任务被拒绝时添加到回退队列
            addToFallbackQueue(event);
        } catch (Exception e) {
            log.error("Failed to submit async event task: {}", event.getEventType(), e);
            addToFallbackQueue(event);
        }
    }
    
    /**
     * 将事件添加到回退队列
     * @param event 要添加的事件
     */
    private void addToFallbackQueue(ExtensionEvent<?> event) {
        if (fallbackEventQueue.size() > 1000) { // 限制队列大小，防止内存溢出
            log.warn("Fallback queue is full, discarding event: {}", event.getEventType());
            return;
        }
        
        fallbackEventQueue.offer(event);
        log.debug("Added event to fallback queue: {}, queue size: {}", 
                event.getEventType(), fallbackEventQueue.size());
    }
    
    /**
     * 实际发布事件的方法
     */
    private void doPublishEvent(ExtensionEvent<?> event) {
        if (log.isDebugEnabled()) {
            log.debug("Publishing extension event: {}", event);
        }
        applicationEventPublisher.publishEvent(event);
    }
    
    @Override
    public void publishBeforeRegister(Object source, String extensionPointName, String extensionImplName) {
        ExtensionEvent<?> event = ExtensionEvent.builder(source)
                .eventType(ExtensionEvent.EventType.BEFORE_REGISTER)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName)
                .build();
        publishEvent(event);
    }
    
    @Override
    public void publishAfterRegister(Object source, String extensionPointName, String extensionImplName) {
        ExtensionEvent<?> event = ExtensionEvent.builder(source)
                .eventType(ExtensionEvent.EventType.AFTER_REGISTER)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishBeforeInvoke(Object source, String extensionPointName, 
                                      String extensionImplName, BizContext<T> bizContext) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.BEFORE_INVOKE)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName)
                .bizContext(bizContext)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishAfterInvokeSuccess(Object source, String extensionPointName, 
                                            String extensionImplName, BizContext<T> bizContext,
                                            Object result, long executionTimeMs) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.AFTER_INVOKE_SUCCESS)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName)
                .bizContext(bizContext)
                .result(result)
                .executionTimeMs(executionTimeMs)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishAfterInvokeFailure(Object source, String extensionPointName, 
                                            String extensionImplName, BizContext<T> bizContext,
                                            Throwable error, long executionTimeMs) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.AFTER_INVOKE_FAILURE)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName)
                .bizContext(bizContext)
                .error(error)
                .executionTimeMs(executionTimeMs)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishExtensionNotFound(Object source, String extensionPointName, BizContext<T> bizContext) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.EXTENSION_NOT_FOUND)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishRoutingDecision(Object source, String extensionPointName, 
                                        String selectedImpl, BizContext<T> bizContext) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.ROUTING_DECISION)
                .extensionPointName(extensionPointName)
                .extensionImplName(selectedImpl)
                .bizContext(bizContext)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishCacheHit(Object source, String extensionPointName, BizContext<T> bizContext) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.CACHE_HIT)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishCacheMiss(Object source, String extensionPointName, BizContext<T> bizContext) {
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.CACHE_MISS)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .build();
        publishEvent(event);
    }
    
    @Override
    public void publishConfigurationChanged(Object source, String extensionPointName, String changeType) {
        ExtensionEvent<?> event = ExtensionEvent.builder(source)
                .eventType(ExtensionEvent.EventType.CONFIGURATION_CHANGED)
                .extensionPointName(extensionPointName)
                .build();
        publishEvent(event);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Extension event publisher {}", enabled ? "enabled" : "disabled");
    }
    
    @Override
    public boolean isEventTypeEnabled(ExtensionEvent.EventType eventType) {
        return eventType != null && enabledEventTypes.contains(eventType);
    }
    
    @Override
    public void setEventTypeEnabled(ExtensionEvent.EventType eventType, boolean enabled) {
        if (eventType == null) {
            return;
        }
        
        if (enabled) {
            enabledEventTypes.add(eventType);
        } else {
            enabledEventTypes.remove(eventType);
        }
        
        log.debug("Event type {} {}", eventType, enabled ? "enabled" : "disabled");
    }
    
    @Override
    public void publishVersionRegister(Object source, String extensionPointName, String version, String extensionImplName) {
        // 由于ExtensionEvent.Builder没有attribute方法，我们使用extensionImplName字段存储版本信息
        ExtensionEvent<?> event = ExtensionEvent.builder(source)
                .eventType(ExtensionEvent.EventType.VERSION_REGISTER)
                .extensionPointName(extensionPointName)
                .extensionImplName(extensionImplName + "[version=" + version + "]")
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishVersionSwitch(Object source, String extensionPointName, String oldVersion, 
                                       String newVersion, BizContext<T> bizContext) {
        // 使用extensionImplName字段存储版本切换信息
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.VERSION_SWITCH)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .extensionImplName(oldVersion + "->" + newVersion)
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishVersionCompatibilityCheck(Object source, String extensionPointName, 
                                                 String targetVersion, boolean isCompatible, 
                                                 BizContext<T> bizContext) {
        // 使用extensionImplName字段存储兼容性检查结果
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.VERSION_COMPATIBILITY_CHECK)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .extensionImplName(targetVersion + "[compatible=" + isCompatible + "]")
                .build();
        publishEvent(event);
    }
    
    @Override
    public <T> void publishDeprecatedVersionUsed(Object source, String extensionPointName, 
                                              String deprecatedVersion, String recommendedVersion, 
                                              BizContext<T> bizContext) {
        // 使用extensionImplName字段存储废弃版本信息
        ExtensionEvent<T> event = ExtensionEvent.<T>builder(source)
                .eventType(ExtensionEvent.EventType.DEPRECATED_VERSION_USED)
                .extensionPointName(extensionPointName)
                .bizContext(bizContext)
                .extensionImplName(deprecatedVersion + "(recommended: " + recommendedVersion + ")")
                .build();
        publishEvent(event);
    }
    
    /**
     * 设置是否异步发布事件
     */
    public void setAsyncPublish(boolean asyncPublish) {
        if (asyncPublish && taskExecutor == null) {
            log.warn("Cannot enable async publish: AsyncTaskExecutor is not available");
            this.asyncPublish = false;
        } else {
            this.asyncPublish = asyncPublish;
            log.info("Extension event publishing mode: {}", asyncPublish ? "async" : "sync");
        }
    }
    
    /**
     * 获取事件统计摘要信息
     * @return 统计摘要字符串
     */
    public String getEventStats() {
        // 从RouteStatsCollector获取路由统计信息（如果可用）
        String routeStatsInfo = "Route stats not available";
        if (routeStatsCollector != null) {
            Map<String, Map<String, Long>> routeStats = routeStatsCollector.getRouteStats();
            routeStatsInfo = "Routes: " + routeStats.size();
        }
        
        return String.format(
            "Event Stats - %s, Fallback Queue Size: %d",
            routeStatsInfo, fallbackEventQueue.size()
        );
    }
    
    /**
     * 获取事件发布统计信息
     * @return 统计信息映射
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 使用RouteStatsCollector获取统计信息（如果可用）
        if (routeStatsCollector != null) {
            // 获取路由统计信息
            Map<String, Map<String, Long>> routeStats = routeStatsCollector.getRouteStats();
            stats.put("routeStats", routeStats);
        }
        
        // 基本事件队列统计
        stats.put("fallbackQueueSize", fallbackEventQueue.size());
        
        return stats;
    }
    
    /**
     * 重置事件统计计数
     */
    public void resetEventStats() {
        // 重置RouteStatsCollector（如果可用）
        if (routeStatsCollector != null) {
            routeStatsCollector.resetAllStats();
        }
        log.info("Event statistics reset");
    }
    
    /**
     * 启用所有事件类型
     */
    public void enableAllEventTypes() {
        enabledEventTypes.addAll(EnumSet.allOf(ExtensionEvent.EventType.class));
        log.info("All event types enabled");
    }
    
    /**
     * 禁用所有事件类型
     */
    public void disableAllEventTypes() {
        enabledEventTypes.clear();
        log.info("All event types disabled");
    }
    
    /**
     * 启用指定的事件类型集合
     */
    public void enableEventTypes(Set<ExtensionEvent.EventType> eventTypes) {
        if (eventTypes != null) {
            enabledEventTypes.addAll(eventTypes);
            log.info("Enabled event types: {}", eventTypes);
        }
    }
    
    /**
     * 获取当前启用的事件类型集合
     */
    public Set<ExtensionEvent.EventType> getEnabledEventTypes() {
        return new CopyOnWriteArraySet<>(enabledEventTypes);
    }
}