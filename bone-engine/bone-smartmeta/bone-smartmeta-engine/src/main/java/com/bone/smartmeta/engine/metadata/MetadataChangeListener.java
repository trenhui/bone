package com.bone.smartmeta.engine.metadata;

/**
 * 元数据变更监听器接口
 * 用于监听元数据的创建、更新和删除事件，支持热加载和动态响应
 */
public interface MetadataChangeListener {
    
    /**
     * 当元数据发生变更时触发
     * @param event 元数据变更事件
     */
    void onMetadataChanged(MetadataChangeEvent event);
    
    /**
     * 获取监听器的优先级
     * 优先级较高的监听器会先被调用
     * @return 优先级值，默认0
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 判断是否支持特定实体类型的元数据变更
     * @param entityApiName 实体API名称
     * @return 是否支持，默认支持所有实体
     */
    default boolean supports(String entityApiName) {
        return true;
    }
}