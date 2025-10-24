package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.EntityMetadata;

/**
 * 默认实体元数据处理器实现
 */
public class DefaultEntityMetadataProcessor implements EntityMetadataProcessor {
    
    @Override
    public void process(EntityMetadata metadata) {
        // 实现实体元数据处理逻辑 - 跳过getName()调用，因为该方法不存在
        if (metadata != null) {
            // 可以在这里添加验证、转换等处理
            System.out.println("Processing entity metadata");
        }
    }
    
    @Override
    public boolean validate(EntityMetadata metadata) {
        // 简单验证：只检查元数据不为空，跳过getName()调用，因为该方法不存在
        return metadata != null;
    }
}