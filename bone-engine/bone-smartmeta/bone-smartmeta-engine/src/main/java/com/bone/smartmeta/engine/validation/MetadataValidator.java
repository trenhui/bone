package com.bone.smartmeta.engine.validation;

import com.bone.smartmeta.engine.model.EntityMetadata;

/**
 * 元数据验证器接口
 * 用于验证元数据的合法性
 */
public interface MetadataValidator {
    
    /**
     * 验证元数据
     * @param metadata 元数据对象
     * @return 是否验证通过
     */
    boolean validate(EntityMetadata metadata);
}