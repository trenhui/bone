package com.bone.smartmeta.engine.event;

/**
 * 简单应用事件发布器实现
 * 提供基本的事件发布功能
 */
public class SimpleApplicationEventPublisher implements ApplicationEventPublisher {
    
    @Override
    public void publishEvent(Object event) {
        // 简单实现：记录事件发布
        System.out.println("Publishing event: " + event.getClass().getSimpleName());
        
        // 如果是MetadataChangedEvent类型，调用特定方法
        if (event instanceof MetadataChangedEvent) {
            publishEvent((MetadataChangedEvent) event);
        }
    }
    
    @Override
    public void publishEvent(MetadataChangedEvent event) {
        // 简单实现：记录元数据变更事件
        System.out.println("Publishing metadata changed event: " + 
                event.getAction() + " for entity " + 
                (event.getMetadata() != null ? event.getMetadata().getApiName() : "null") + 
                " in tenant " + event.getTenantId());
    }
}