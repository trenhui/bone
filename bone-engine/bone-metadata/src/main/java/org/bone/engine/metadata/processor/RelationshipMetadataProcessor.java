package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.RelationshipMetadata;

/**
 * 关系元数据处理器接口
 */
public interface RelationshipMetadataProcessor {
    /**
     * 处理关系元数据
     */
    void process(RelationshipMetadata metadata);
    
    /**
     * 验证关系元数据
     */
    boolean validate(RelationshipMetadata metadata);
}