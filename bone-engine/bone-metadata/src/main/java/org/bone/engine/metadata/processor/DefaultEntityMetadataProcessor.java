package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.EntityMetadata;

/**
 * 默认实体元数据处理器实现
 */
public class DefaultEntityMetadataProcessor implements EntityMetadataProcessor {
    
    @Override
    public void process(EntityMetadata metadata) {
        // 实现实体元数据处理逻辑
        if (metadata != null) {
            // 可以在这里添加验证、转换等处理
            System.out.println("Processing entity metadata: " + metadata.getName());
        }
    }
    
    @Override
    public boolean validate(EntityMetadata metadata) {
        // 简单验证：确保元数据不为空且名称不为空
        return metadata != null && metadata.getName() != null && !metadata.getName().trim().isEmpty();
    }
}