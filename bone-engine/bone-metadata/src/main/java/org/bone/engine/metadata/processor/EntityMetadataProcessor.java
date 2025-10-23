package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.EntityMetadata;

/**
 * 实体元数据处理器接口
 */
public interface EntityMetadataProcessor {
    /**
     * 处理实体元数据
     */
    void process(EntityMetadata metadata);
    
    /**
     * 验证实体元数据
     */
    boolean validate(EntityMetadata metadata);
}