package com.bone.smartmeta.engine.metadata;

/**
 * 元数据变更监听器接口
 */
public interface MetadataChangeListener {
    
    /**
     * 处理元数据变更事件
     * @param entityApiName 实体API名称
     * @param changeType 变更类型
     */
    void onMetadataChanged(String entityApiName, String changeType);
    
    /**
     * 获取优先级
     * @return 优先级值，越小优先级越高
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 判断是否支持特定的元数据变更
     * @param entityApiName 实体API名称
     * @param changeType 变更类型
     * @return 是否支持
     */
    default boolean supports(String entityApiName, String changeType) {
        return true;
    }
}