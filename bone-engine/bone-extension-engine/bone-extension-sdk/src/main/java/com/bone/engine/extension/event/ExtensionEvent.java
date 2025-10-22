package com.bone.engine.extension.event;

import com.bone.engine.extension.context.BizContext;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 扩展点事件类，用于在扩展点生命周期的关键阶段触发事件通知
 * <p>
 * 支持观察者模式，允许系统各组件监听和响应扩展点相关事件，实现松耦合的架构设计
 * 事件包含扩展点执行上下文、执行状态、时间戳等关键信息
 *
 * @author renhui.trh
 * @since 1.0.0
 */
public class ExtensionEvent<T> extends ApplicationEvent {
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /** 扩展点注册前 */
        BEFORE_REGISTER,
        /** 扩展点注册完成 */
        AFTER_REGISTER,
        /** 扩展点调用前 */
        BEFORE_INVOKE,
        /** 扩展点调用成功 */
        AFTER_INVOKE_SUCCESS,
        /** 扩展点调用失败 */
        AFTER_INVOKE_FAILURE,
        /** 扩展点未找到 */
        EXTENSION_NOT_FOUND,
        /** 扩展点路由决策 */
        ROUTING_DECISION,
        /** 扩展点缓存命中 */
        CACHE_HIT,
        /** 扩展点缓存未命中 */
        CACHE_MISS,
        /** 扩展点配置变更 */
        CONFIGURATION_CHANGED,
        /** 扩展点版本注册 */
        VERSION_REGISTER,
        /** 扩展点版本切换 */
        VERSION_SWITCH,
        /** 扩展点版本兼容性检查 */
        VERSION_COMPATIBILITY_CHECK,
        /** 使用了废弃版本的扩展点 */
        DEPRECATED_VERSION_USED
    }
    
    /** 事件ID，用于唯一标识每个事件 */
    private final String eventId;
    
    /** 事件类型 */
    private final EventType eventType;
    
    /** 扩展点接口类名 */
    private final String extensionPointName;
    
    /** 扩展点实现类名 */
    private final String extensionImplName;
    
    /** 业务上下文 */
    private final BizContext<T> bizContext;
    
    /** 事件发生时间戳 */
    private final long timestamp;
    
    /** 事件发生本地时间 */
    private final LocalDateTime eventTime;
    
    /** 执行结果（如果有） */
    private final Object result;
    
    /** 异常信息（如果有） */
    private final Throwable error;
    
    /** 扩展点执行耗时（毫秒） */
    private final Long executionTimeMs;
    
    /**
     * 私有构造函数，使用Builder创建实例
     */
    private ExtensionEvent(Object source, EventType eventType, String extensionPointName, 
                         String extensionImplName, BizContext<T> bizContext,
                         Object result, Throwable error, Long executionTimeMs) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.extensionPointName = extensionPointName;
        this.extensionImplName = extensionImplName;
        this.bizContext = bizContext;
        this.timestamp = System.currentTimeMillis(); // 与ApplicationEvent保持一致
        this.eventTime = LocalDateTime.now(); // 保留LocalDateTime用于业务逻辑
        this.result = result;
        this.error = error;
        this.executionTimeMs = executionTimeMs;
    }
    
    /**
     * 创建Builder实例
     * 
     * @param source 事件源
     * @param <T> 业务上下文数据类型
     * @return Builder实例
     */
    public static <T> Builder<T> builder(Object source) {
        return new Builder<>(source);
    }
    
    /**
     * Builder模式实现，用于创建ExtensionEvent实例
     */
    public static class Builder<T> {
        private final Object source;
        private EventType eventType;
        private String extensionPointName;
        private String extensionImplName;
        private BizContext<T> bizContext;
        private Object result;
        private Throwable error;
        private Long executionTimeMs;
        
        private Builder(Object source) {
            this.source = source;
        }
        
        public Builder<T> eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder<T> extensionPointName(String extensionPointName) {
            this.extensionPointName = extensionPointName;
            return this;
        }
        
        public Builder<T> extensionImplName(String extensionImplName) {
            this.extensionImplName = extensionImplName;
            return this;
        }
        
        public Builder<T> bizContext(BizContext<T> bizContext) {
            this.bizContext = bizContext;
            return this;
        }
        
        public Builder<T> result(Object result) {
            this.result = result;
            return this;
        }
        
        public Builder<T> error(Throwable error) {
            this.error = error;
            return this;
        }
        
        public Builder<T> executionTimeMs(Long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }
        
        public ExtensionEvent<T> build() {
            validate();
            return new ExtensionEvent<>(source, eventType, extensionPointName, 
                                      extensionImplName, bizContext, result, 
                                      error, executionTimeMs);
        }
        
        private void validate() {
            Objects.requireNonNull(source, "Event source cannot be null");
            Objects.requireNonNull(eventType, "Event type cannot be null");
            
            // 对于调用相关事件，扩展点名称不能为空
            if ((eventType == EventType.BEFORE_INVOKE || 
                 eventType == EventType.AFTER_INVOKE_SUCCESS || 
                 eventType == EventType.AFTER_INVOKE_FAILURE ||
                 eventType == EventType.EXTENSION_NOT_FOUND) &&
                extensionPointName == null) {
                throw new IllegalArgumentException("Extension point name cannot be null for invocation events");
            }
        }
    }
    
    // Getter方法
    public String getEventId() {
        return eventId;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public String getExtensionPointName() {
        return extensionPointName;
    }
    
    public String getExtensionImplName() {
        return extensionImplName;
    }
    
    public BizContext<T> getBizContext() {
        return bizContext;
    }
    
    // 删除重写的getTimestamp()方法，因为ApplicationEvent中的该方法是final的
    
    /**
     * 获取事件发生的本地时间
     */
    public LocalDateTime getEventTime() {
        return eventTime;
    }
    
    public Object getResult() {
        return result;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    /**
     * 检查是否为调用类型事件
     */
    public boolean isInvocationEvent() {
        return eventType == EventType.BEFORE_INVOKE ||
               eventType == EventType.AFTER_INVOKE_SUCCESS ||
               eventType == EventType.AFTER_INVOKE_FAILURE;
    }
    
    /**
     * 检查是否为注册类型事件
     */
    public boolean isRegistrationEvent() {
        return eventType == EventType.BEFORE_REGISTER ||
               eventType == EventType.AFTER_REGISTER;
    }
    
    /**
     * 检查是否为错误事件
     */
    public boolean isErrorEvent() {
        return eventType == EventType.AFTER_INVOKE_FAILURE ||
               eventType == EventType.EXTENSION_NOT_FOUND;
    }
    
    /**
     * 检查是否为缓存相关事件
     */
    public boolean isCacheEvent() {
        return eventType == EventType.CACHE_HIT ||
               eventType == EventType.CACHE_MISS;
    }
    
    @Override
    public String toString() {
        return "ExtensionEvent{" +
                "eventId='" + eventId + "'" +
                ", eventType=" + eventType +
                ", extensionPointName='" + extensionPointName + "'" +
                ", extensionImplName='" + extensionImplName + "'" +
                ", timestamp=" + timestamp +
                ", executionTimeMs=" + executionTimeMs +
                ", hasError=" + (error != null) +
                "}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionEvent<?> that = (ExtensionEvent<?>) o;
        return Objects.equals(eventId, that.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}