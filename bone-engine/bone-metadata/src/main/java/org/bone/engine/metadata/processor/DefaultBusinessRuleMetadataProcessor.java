package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.BusinessRuleMetadata;

/**
 * 默认业务规则元数据处理器实现
 */
public class DefaultBusinessRuleMetadataProcessor implements BusinessRuleMetadataProcessor {
    
    @Override
    public void process(BusinessRuleMetadata metadata) {
        // 实现业务规则元数据处理逻辑
        if (metadata != null) {
            System.out.println("Processing business rule metadata: " + metadata.getName());
        }
    }
    
    @Override
    public boolean validate(BusinessRuleMetadata metadata) {
        // 简单验证：确保元数据不为空且名称不为空
        return metadata != null && metadata.getName() != null && !metadata.getName().trim().isEmpty();
    }
}