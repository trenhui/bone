package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.RelationshipMetadata;

/**
 * 默认关系元数据处理器实现
 */
public class DefaultRelationshipMetadataProcessor implements RelationshipMetadataProcessor {
    
    @Override
    public void process(RelationshipMetadata metadata) {
        // 实现关系元数据处理逻辑
        if (metadata != null) {
            System.out.println("Processing relationship metadata: " + metadata.getName());
        }
    }
    
    @Override
    public boolean validate(RelationshipMetadata metadata) {
        // 简单验证：确保元数据不为空且名称不为空
        return metadata != null && metadata.getName() != null && !metadata.getName().trim().isEmpty();
    }
}