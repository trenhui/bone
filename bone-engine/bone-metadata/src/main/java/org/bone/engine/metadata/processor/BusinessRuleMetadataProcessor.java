package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.BusinessRuleMetadata;

/**
 * 业务规则元数据处理器接口
 */
public interface BusinessRuleMetadataProcessor {
    /**
     * 处理业务规则元数据
     */
    void process(BusinessRuleMetadata metadata);
    
    /**
     * 验证业务规则元数据
     */
    boolean validate(BusinessRuleMetadata metadata);
}