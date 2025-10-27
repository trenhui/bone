package org.bone.engine.metadata.util;

import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.model.FieldMetadata;
import org.bone.engine.metadata.model.RelationshipMetadata;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据工具类 - 提供通用的元数据处理方法
 * 支持验证、转换、规范化等元数据操作
 * 
 * @author Bone Engine Team
 */
public class MetadataUtils {

    // ================ API名称正则表达式 ================
    private static final Pattern API_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,49}$");
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9_]{1,49}$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    // ================ 验证方法 ================
    
    /**
     * 验证实体元数据的有效性
     */
    public static List<String> validateEntityMetadata(EntityMetadata metadata) {
        List<String> errors = new ArrayList<>();
        
        if (metadata == null) {
            errors.add("实体元数据不能为空");
            return errors;
        }
        
        // 验证API名称
        if (!validateApiName(metadata.getApiName())) {
            errors.add("实体API名称格式不正确，必须以字母开头，只能包含字母、数字和下划线，长度为2-50个字符");
        }
        
        // 验证必填字段
        if (StringUtils.isEmpty(metadata.getLabel())) {
            errors.add("实体标签不能为空");
        }
        
        if (StringUtils.isEmpty(metadata.getDomain())) {
            errors.add("实体业务域不能为空");
        }
        
        if (StringUtils.isEmpty(metadata.getEntityType())) {
            errors.add("实体类型不能为空");
        }
        
        if (CollectionUtils.isEmpty(metadata.getFields())) {
            errors.add("实体字段不能为空");
        }
        
        if (!validateVersion(metadata.getVersion())) {
            errors.add("实体版本格式不正确，必须符合语义化版本规范 x.y.z");
        }
        
        // 验证字段元数据
        if (metadata.getFields() != null) {
            for (Map.Entry<String, FieldMetadata> entry : metadata.getFields().entrySet()) {
                String fieldName = entry.getKey();
                FieldMetadata field = entry.getValue();
                
                // 验证字段名称一致性
                if (!fieldName.equals(field.getName())) {
                    errors.add("字段名称不一致: " + fieldName + " vs " + field.getName());
                }
                
                List<String> fieldErrors = validateFieldMetadata(field);
                if (!fieldErrors.isEmpty()) {
                    errors.addAll(fieldErrors.stream()
                            .map(e -> "字段 " + fieldName + ": " + e)
                            .collect(Collectors.toList()));
                }
            }
        }
        
        // 验证关系元数据
        if (metadata.getRelationships() != null) {
            for (RelationshipMetadata relationship : metadata.getRelationships()) {
                List<String> relErrors = validateRelationshipMetadata(relationship);
                if (!relErrors.isEmpty()) {
                    errors.addAll(relErrors.stream()
                            .map(e -> "关系 " + relationship.getName() + ": " + e)
                            .collect(Collectors.toList()));
                }
            }
        }
        
        // 验证索引元数据
        if (metadata.getIndexes() != null) {
            for (Map<String, Object> index : metadata.getIndexes()) {
                List<String> indexErrors = validateIndexMetadata(index, metadata.getFields().keySet());
                if (!indexErrors.isEmpty()) {
                    String indexName = index.get("name") != null ? index.get("name").toString() : "未知索引";
                    errors.addAll(indexErrors.stream()
                            .map(e -> "索引 " + indexName + ": " + e)
                            .collect(Collectors.toList()));
                }
            }
        }
        
        return errors;
    }
    
    /**
     * 验证字段元数据
     */
    public static List<String> validateFieldMetadata(FieldMetadata field) {
        List<String> errors = new ArrayList<>();
        
        if (field == null) {
            errors.add("字段元数据不能为空");
            return errors;
        }
        
        // 验证字段名称
        if (!validateFieldName(field.getName())) {
            errors.add("字段名称格式不正确，必须以小写字母开头，只能包含字母、数字和下划线，长度为2-50个字符");
        }
        
        // 验证必填字段
        if (StringUtils.isEmpty(field.getLabel())) {
            errors.add("字段标签不能为空");
        }
        
        if (StringUtils.isEmpty(field.getType())) {
            errors.add("字段类型不能为空");
        }
        
        // 验证字段类型
        if (!isValidFieldType(field.getType())) {
            errors.add("不支持的字段类型: " + field.getType());
        }
        
        // 验证最大长度
        if (field.getMaxLength() != null && field.getMaxLength() <= 0) {
            errors.add("字段最大长度必须大于0");
        }
        
        // 验证数值范围
        if (field.getMinValue() != null && field.getMaxValue() != null && 
            field.getMinValue() > field.getMaxValue()) {
            errors.add("字段最小值不能大于最大值");
        }
        
        // 验证计算字段
        if (field.getCalculated() && StringUtils.isEmpty(field.getCalculationExpression())) {
            errors.add("计算字段必须指定计算表达式");
        }
        
        return errors;
    }
    
    /**
     * 验证关系元数据
     */
    public static List<String> validateRelationshipMetadata(RelationshipMetadata relationship) {
        List<String> errors = new ArrayList<>();
        
        if (relationship == null) {
            errors.add("关系元数据不能为空");
            return errors;
        }
        
        // 验证关系名称
        if (StringUtils.isEmpty(relationship.getName())) {
            errors.add("关系名称不能为空");
        }
        
        // 验证必填字段
        if (StringUtils.isEmpty(relationship.getType())) {
            errors.add("关系类型不能为空");
        }
        
        if (StringUtils.isEmpty(relationship.getTargetEntity())) {
            errors.add("目标实体不能为空");
        }
        
        // 验证关系类型
        if (!isValidRelationshipType(relationship.getType())) {
            errors.add("不支持的关系类型: " + relationship.getType());
        }
        
        return errors;
    }
    
    /**
     * 验证索引元数据
     */
    private static List<String> validateIndexMetadata(Map<String, Object> index, Set<String> availableFields) {
        List<String> errors = new ArrayList<>();
        
        // 验证索引名称
        if (index.get("name") == null || StringUtils.isEmpty(index.get("name").toString())) {
            errors.add("索引名称不能为空");
        }
        
        // 验证索引字段
        List<Map<String, Object>> fields = index.get("fields") instanceof List ? 
                                           (List<Map<String, Object>>) index.get("fields") : 
                                           new ArrayList<>();
        
        if (fields.isEmpty()) {
            errors.add("索引字段不能为空");
        } else {
            // 验证索引字段存在性
            for (Map<String, Object> field : fields) {
                if (field == null || field.get("name") == null || 
                    StringUtils.isEmpty(field.get("name").toString())) {
                    errors.add("索引字段名称不能为空");
                } else if (availableFields != null && 
                          !availableFields.contains(field.get("name").toString())) {
                    errors.add("索引引用了不存在的字段: " + field.get("name"));
                }
            }
        }
        
        // 验证索引类型
        String indexType = index.get("type") != null ? index.get("type").toString() : null;
        if (!isValidIndexType(indexType)) {
            errors.add("不支持的索引类型: " + indexType);
        }
        
        return errors;
    }
    
    // ================ 格式验证方法 ================
    
    /**
     * 验证API名称格式
     */
    public static boolean validateApiName(String apiName) {
        return apiName != null && API_NAME_PATTERN.matcher(apiName).matches();
    }
    
    /**
     * 验证字段名称格式
     */
    public static boolean validateFieldName(String fieldName) {
        return fieldName != null && FIELD_NAME_PATTERN.matcher(fieldName).matches();
    }
    
    /**
     * 验证版本格式
     */
    public static boolean validateVersion(String version) {
        return version != null && VERSION_PATTERN.matcher(version).matches();
    }
    
    /**
     * 验证字段类型是否有效
     */
    public static boolean isValidFieldType(String type) {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "TEXT", "PICKLIST", "LOOKUP", "CURRENCY", "DATE", "NUMBER",
            "BOOLEAN", "DATETIME", "REFERENCE", "EMAIL", "PHONE", "URL",
            "FILE", "IMAGE", "ARRAY", "OBJECT", "JSON", "HTML"
        ));
        return validTypes.contains(type);
    }
    
    /**
     * 验证关系类型是否有效
     */
    public static boolean isValidRelationshipType(String type) {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY"
        ));
        return validTypes.contains(type);
    }
    
    /**
     * 验证索引类型是否有效
     */
    public static boolean isValidIndexType(String type) {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "BTREE", "HASH", "FULLTEXT", "GIST", "GIN", "SPATIAL"
        ));
        return validTypes.contains(type);
    }
    
    // ================ 转换方法 ================
    
    /**
     * 将实体元数据转换为Map格式
     */
    public static Map<String, Object> entityMetadataToMap(EntityMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("apiName", metadata.getApiName());
        result.put("label", metadata.getLabel());
        result.put("labels", metadata.getLabels());
        result.put("description", metadata.getDescription());
        result.put("domain", metadata.getDomain());
        result.put("entityType", metadata.getEntityType());
        result.put("version", metadata.getVersion());
        result.put("parentEntity", metadata.getParentEntity());
        
        // 转换字段
        if (metadata.getFields() != null) {
            Map<String, Map<String, Object>> fieldsMap = new HashMap<>();
            for (Map.Entry<String, FieldMetadata> entry : metadata.getFields().entrySet()) {
                fieldsMap.put(entry.getKey(), fieldMetadataToMap(entry.getValue()));
            }
            result.put("fields", fieldsMap);
        }
        
        // 其他属性转换...
        
        return result;
    }
    
    /**
     * 将字段元数据转换为Map格式
     */
    public static Map<String, Object> fieldMetadataToMap(FieldMetadata field) {
        if (field == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("name", field.getName());
        result.put("label", field.getLabel());
        result.put("labels", field.getLabels());
        result.put("description", field.getDescription());
        result.put("type", field.getType());
        result.put("required", field.getRequired());
        result.put("defaultValue", field.getDefaultValue());
        result.put("defaultExpression", field.getDefaultExpression());
        result.put("maxLength", field.getMaxLength());
        result.put("minValue", field.getMinValue());
        result.put("maxValue", field.getMaxValue());
        result.put("calculated", field.getCalculated());
        result.put("calculationExpression", field.getCalculationExpression());
        result.put("encrypted", field.getEncrypted());
        result.put("unstructured", field.getUnstructured());
        
        // 其他属性转换...
        
        return result;
    }
    
    // ================ 辅助方法 ================
    
    /**
     * 合并两个实体元数据
     */
    public static EntityMetadata mergeEntityMetadata(EntityMetadata base, EntityMetadata override) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        
        // 创建合并后的元数据
        EntityMetadata merged = new EntityMetadata();
        
        // 合并基本属性
        merged.setApiName(override.getApiName() != null ? override.getApiName() : base.getApiName());
        merged.setLabel(override.getLabel() != null ? override.getLabel() : base.getLabel());
        merged.setDescription(override.getDescription() != null ? override.getDescription() : base.getDescription());
        merged.setDomain(override.getDomain() != null ? override.getDomain() : base.getDomain());
        merged.setEntityType(override.getEntityType() != null ? override.getEntityType() : base.getEntityType());
        merged.setVersion(override.getVersion() != null ? override.getVersion() : base.getVersion());
        merged.setParentEntity(override.getParentEntity() != null ? override.getParentEntity() : base.getParentEntity());
        
        // 合并字段（覆盖模式）
        Map<String, FieldMetadata> mergedFields = new HashMap<>();
        if (base.getFields() != null) {
            mergedFields.putAll(base.getFields());
        }
        if (override.getFields() != null) {
            mergedFields.putAll(override.getFields());
        }
        merged.setFields(mergedFields);
        
        // 合并其他属性...
        
        return merged;
    }
    
    /**
     * 获取实体的主键字段
     */
    public static FieldMetadata getPrimaryKeyField(EntityMetadata metadata) {
        if (metadata == null || metadata.getFields() == null) {
            return null;
        }
        
        // 查找ID字段作为主键
        for (FieldMetadata field : metadata.getFields().values()) {
            if ("id".equals(field.getName())) {
                return field;
            }
        }
        
        return null;
    }
    
    /**
     * 获取实体的必填字段列表
     */
    public static List<FieldMetadata> getRequiredFields(EntityMetadata metadata) {
        List<FieldMetadata> requiredFields = new ArrayList<>();
        
        if (metadata != null && metadata.getFields() != null) {
            for (FieldMetadata field : metadata.getFields().values()) {
                if (field.getRequired() != null && field.getRequired()) {
                    requiredFields.add(field);
                }
            }
        }
        
        return requiredFields;
    }
    
    /**
     * 生成默认的实体元数据
     */
    public static EntityMetadata createDefaultEntityMetadata(String apiName, String label, String domain) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setApiName(apiName);
        metadata.setLabel(label);
        metadata.setDomain(domain);
        metadata.setEntityType("STANDARD");
        metadata.setVersion("1.0.0");
        
        // 添加默认ID字段
        Map<String, FieldMetadata> fields = new HashMap<>();
        FieldMetadata idField = new FieldMetadata();
        idField.setName("id");
        idField.setLabel("ID");
        idField.setType("TEXT");
        idField.setRequired(true);
        fields.put("id", idField);
        metadata.setFields(fields);
        
        return metadata;
    }
    
    // ================ 内部工具类 ================
    
    /**
     * 索引字段内部类
     */
    public static class IndexField {
        private String name;
        private String direction;
        
        public IndexField(String name) {
            this.name = name;
            this.direction = "ASC";
        }
        
        public IndexField(String name, String direction) {
            this.name = name;
            this.direction = direction;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDirection() {
            return direction;
        }
    }
}