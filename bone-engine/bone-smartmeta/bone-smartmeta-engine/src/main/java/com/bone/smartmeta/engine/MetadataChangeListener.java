package com.bone.smartmeta.engine;

/**
 * 元数据变更监听器接口
 * 用于接收元数据创建、更新、删除等变更事件
 */
public interface MetadataChangeListener {
    
    /**
     * 监听元数据变更
     * @param entityType 实体类型名称
     * @param changeType 变更类型字符串
     */
    void onMetadataChanged(String entityType, String changeType);
}