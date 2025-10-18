package com.bone.smartmeta.processor;

import com.bone.smartmeta.metadata.EntityMetadata;
import com.bone.smartmeta.metadata.FieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 默认元数据处理器
 * <p>
 * 提供基本的元数据验证和增强功能，如字段名称唯一性检查、默认值设置等
 * </p>
 *
 * @author SmartMeta Team
 */
@Component
@Order(Ordered.MEDIUM_PRECEDENCE)
public class DefaultMetadataProcessor implements MetadataProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultMetadataProcessor.class);

    @Override
    public EntityMetadata process(EntityMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        log.debug("开始默认处理实体: {}", metadata.getEntityName());

        // 确保实体ID存在
        if (metadata.getId() == null) {
            metadata.setId(UUID.randomUUID().toString());
            log.debug("为实体 {} 生成新ID: {}", metadata.getEntityName(), metadata.getId());
        }

        // 验证并增强字段元数据
        validateAndEnhanceFieldMetadata(metadata);

        // 确保版本信息存在
        if (metadata.getVersion() == null) {
            metadata.setVersion(1L);
        }

        // 确保创建时间存在
        if (metadata.getCreatedTime() == null) {
            metadata.setCreatedTime(System.currentTimeMillis());
        }

        // 更新最后修改时间
        metadata.setLastModifiedTime(System.currentTimeMillis());

        log.debug("完成默认处理实体: {}", metadata.getEntityName());
        return metadata;
    }

    @Override
    public boolean supports(EntityMetadata metadata) {
        // 默认处理器支持所有实体元数据
        return metadata != null;
    }

    /**
     * 验证并增强字段元数据
     * 
     * @param metadata 实体元数据
     */
    private void validateAndEnhanceFieldMetadata(EntityMetadata metadata) {
        if (metadata.getFields() == null || metadata.getFields().isEmpty()) {
            return;
        }

        // 检查字段名称唯一性
        Set<String> fieldNames = new HashSet<>();
        Set<String> duplicateFields = new HashSet<>();

        for (FieldMetadata field : metadata.getFields()) {
            if (field != null && field.getFieldName() != null) {
                if (!fieldNames.add(field.getFieldName())) {
                    duplicateFields.add(field.getFieldName());
                }

                // 增强字段元数据
                enhanceFieldMetadata(field);
            }
        }

        // 记录重复字段警告
        if (!duplicateFields.isEmpty()) {
            log.warn("实体 {} 中存在重复字段名称: {}", metadata.getEntityName(), duplicateFields);
        }
    }

    /**
     * 增强单个字段元数据
     * 
     * @param field 字段元数据
     */
    private void enhanceFieldMetadata(FieldMetadata field) {
        // 确保字段类型存在
        if (field.getFieldType() == null) {
            field.setFieldType("String"); // 默认类型
            log.debug("为字段 {} 设置默认类型: String", field.getFieldName());
        }

        // 设置默认字段长度
        if (field.getLength() == null && "String".equals(field.getFieldType())) {
            field.setLength(255); // 字符串默认长度
        }

        // 确保必填字段有默认值
        if (Boolean.TRUE.equals(field.getRequired()) && field.getDefaultValue() == null) {
            // 根据字段类型设置默认值
            switch (field.getFieldType()) {
                case "String":
                    field.setDefaultValue("");
                    break;
                case "Integer":
                case "Long":
                case "Double":
                case "Float":
                    field.setDefaultValue("0");
                    break;
                case "Boolean":
                    field.setDefaultValue("false");
                    break;
                default:
                    // 其他类型暂不设置默认值
                    break;
            }
        }

        // 自动生成字段描述（如果不存在）
        if (field.getDescription() == null && field.getFieldName() != null) {
            field.setDescription(convertCamelToChinese(field.getFieldName()));
        }
    }

    /**
     * 将驼峰命名转换为中文描述
     * 
     * @param camelName 驼峰命名
     * @return 中文描述
     */
    private String convertCamelToChinese(String camelName) {
        // 简单的驼峰转中文逻辑，实际项目中可以使用更复杂的规则或字典映射
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelName.length(); i++) {
            char c = camelName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(" ");
            }
            result.append(c);
        }
        return result.toString();
    }
}