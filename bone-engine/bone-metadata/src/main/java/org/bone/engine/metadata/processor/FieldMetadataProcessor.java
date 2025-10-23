package org.bone.engine.metadata.processor;

import org.bone.engine.metadata.model.FieldMetadata;

/**
 * 字段元数据处理器接口
 */
public interface FieldMetadataProcessor {
    /**
     * 处理字段元数据
     */
    void process(FieldMetadata metadata);
    
    /**
     * 验证字段元数据
     */
    boolean validate(FieldMetadata metadata);
}