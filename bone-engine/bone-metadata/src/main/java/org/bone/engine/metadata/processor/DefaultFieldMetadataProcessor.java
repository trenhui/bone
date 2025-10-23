package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.FieldMetadata;

/**
 * 默认字段元数据处理器实现
 */
public class DefaultFieldMetadataProcessor implements FieldMetadataProcessor {
    
    @Override
    public void process(FieldMetadata metadata) {
        // 实现字段元数据处理逻辑
        if (metadata != null) {
            System.out.println("Processing field metadata: " + metadata.getName());
        }
    }
    
    @Override
    public boolean validate(FieldMetadata metadata) {
        // 简单验证：确保元数据不为空且名称不为空
        return metadata != null && metadata.getName() != null && !metadata.getName().trim().isEmpty();
    }
}