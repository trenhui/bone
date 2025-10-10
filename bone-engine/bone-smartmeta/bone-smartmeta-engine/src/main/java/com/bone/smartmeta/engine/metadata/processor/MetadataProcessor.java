package com.bone.smartmeta.engine.metadata.processor;

import com.bone.smartmeta.engine.metadata.EntityMetadata;

import java.util.List;

/**
 * 元数据处理器接口
 */
public interface MetadataProcessor {
    
    /**
     * 获取源类型
     */
    String getSourceType();
    
    /**
     * 处理并返回所有实体元数据
     */
    List<EntityMetadata> processAllMetadata();
    
    /**
     * 处理指定实体的元数据
     */
    EntityMetadata processEntityMetadata(String entityName);
    
    /**
     * 处理元数据
     */
    Object processMetadata(String metadata);
    
    /**
     * 刷新元数据缓存
     */
    void refreshMetadata();
}