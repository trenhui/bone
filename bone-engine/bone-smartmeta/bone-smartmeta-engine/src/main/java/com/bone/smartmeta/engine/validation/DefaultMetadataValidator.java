package com.bone.smartmeta.engine.validation;

import com.bone.smartmeta.engine.model.EntityMetadata;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 默认元数据验证器实现
 * 提供基本的元数据验证功能
 */
public class DefaultMetadataValidator implements MetadataValidator {
    
    private static final Logger log = Logger.getLogger(DefaultMetadataValidator.class.getName());
    
    @Override
    public boolean validate(EntityMetadata metadata) {
        try {
            // 验证元数据不为空
            if (metadata == null) {
                log.log(Level.SEVERE, "Entity metadata cannot be null");
                return false;
            }
            
            // 验证实体名称
            if (metadata.getApiName() == null || metadata.getApiName().trim().isEmpty()) {
                log.log(Level.SEVERE, "Entity API name cannot be empty");
                return false;
            }
            
            // 验证字段列表（仅记录警告，不影响验证结果）
            if (metadata.getFields() == null || metadata.getFields().isEmpty()) {
                log.log(Level.WARNING, "Entity has no fields");
            }
            
            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error validating metadata: " + e.getMessage(), e);
            return false;
        }
    }
}