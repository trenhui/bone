package com.bone.smartmeta.engine.processor;

import java.util.Map;

/**
 * 元数据处理器接口
 * 负责元数据的验证、分析、转换和增强处理
 */
public interface MetadataProcessor {
    
    /**
     * 验证元数据的有效性
     * 
     * @param metadata 元数据对象
     * @return 验证结果，包含是否有效和错误信息
     */
    ValidationResult validateMetadata(Object metadata);
    
    /**
     * 分析元数据变更的影响
     * 
     * @param oldEntityName 旧实体名称
     * @param newMetadata 新的元数据
     * @return 影响分析结果
     */
    Object analyzeImpact(String oldEntityName, Object newMetadata);
    
    /**
     * 转换元数据格式
     * 
     * @param sourceMetadata 源元数据
     * @param targetType 目标类型
     * @return 转换后的元数据
     */
    Object transformMetadata(Object sourceMetadata, String targetType);
    
    /**
     * 增强元数据，添加默认字段和验证规则
     * 
     * @param metadata 元数据对象
     * @return 增强后的元数据
     */
    Object enrichMetadata(Object metadata);
    
    /**
     * 标准化元数据格式
     * 
     * @param metadata 元数据对象
     * @return 标准化后的元数据
     */
    Object normalizeMetadata(Object metadata);
    
    /**
     * 合并多个元数据
     * 
     * @param baseMetadata 基础元数据
     * @param overlayMetadata 覆盖元数据
     * @return 合并后的元数据
     */
    Object mergeMetadata(Object baseMetadata, Object overlayMetadata);
    
    /**
     * 提取元数据的关键信息
     * 
     * @param metadata 元数据对象
     * @return 关键信息映射
     */
    Map<String, Object> extractMetadataInfo(Object metadata);
    
    /**
     * 验证结果类
     */
    interface ValidationResult {
        boolean isValid();
        String getErrorMessage();
        Map<String, Object> getDetails();
    }
}