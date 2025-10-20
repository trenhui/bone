package org.bone.engine.metadata.validator;

import org.bone.engine.metadata.model.*;
import org.bone.engine.metadata.util.MetadataUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据验证器类 - 提供元数据验证功能，确保元数据符合UMP协议规范
 * 
 * @author Bone Engine Team
 */
public class MetadataValidator {
    
    // API名称正则表达式
    private static final Pattern API_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,49}$");
    
    // 字段名称正则表达式
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9_]{1,49}$");
    
    // 版本号正则表达式
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    
    /**
     * 验证实体元数据
     * 
     * @param entityMetadata 实体元数据
     * @return 验证结果
     */
    public ValidationResult validateEntityMetadata(EntityMetadata entityMetadata) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (entityMetadata.getApiName() == null || entityMetadata.getApiName().trim().isEmpty()) {
            errors.add(new ValidationError("apiName", "apiName is required"));
        } else if (!API_NAME_PATTERN.matcher(entityMetadata.getApiName()).matches()) {
            errors.add(new ValidationError("apiName", "apiName must match pattern ^[A-Za-z][A-Za-z0-9_]{1,49}$"));
        }
        
        if (entityMetadata.getLabel() == null || entityMetadata.getLabel().trim().isEmpty()) {
            errors.add(new ValidationError("label", "label is required"));
        }
        
        if (entityMetadata.getDomain() == null || entityMetadata.getDomain().trim().isEmpty()) {
            errors.add(new ValidationError("domain", "domain is required"));
        }
        
        if (entityMetadata.getEntityType() == null) {
            errors.add(new ValidationError("entityType", "entityType is required"));
        } else {
            boolean validType = false;
            for (EntityType type : EntityType.values()) {
                if (type.name().equals(entityMetadata.getEntityType().name())) {
                    validType = true;
                    break;
                }
            }
            if (!validType) {
                errors.add(new ValidationError("entityType", "entityType must be one of STANDARD, SUB_ENTITY, JOINT_ENTITY"));
            }
        }
        
        if (entityMetadata.getFields() == null || entityMetadata.getFields().isEmpty()) {
            errors.add(new ValidationError("fields", "fields is required and cannot be empty"));
        }
        
        if (entityMetadata.getVersion() == null || entityMetadata.getVersion().trim().isEmpty()) {
            errors.add(new ValidationError("version", "version is required"));
        } else if (!VERSION_PATTERN.matcher(entityMetadata.getVersion()).matches()) {
            errors.add(new ValidationError("version", "version must match pattern ^\\d+\\.\\d+\\.\\d+$"));
        }
        
        // 验证字段元数据
        if (entityMetadata.getFields() != null) {
            for (Map.Entry<String, FieldMetadata> entry : entityMetadata.getFields().entrySet()) {
                String fieldName = entry.getKey();
                FieldMetadata fieldMetadata = entry.getValue();
                
                // 验证字段名称一致性
                if (!fieldName.equals(fieldMetadata.getName())) {
                    errors.add(new ValidationError("fields", "Field name in key ('" + fieldName + "') does not match field name in value ('" + fieldMetadata.getName() + "')"));
                }
                
                // 验证字段元数据
                ValidationResult fieldResult = validateFieldMetadata(fieldMetadata);
                if (!fieldResult.isValid()) {
                    fieldResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("fields." + fieldName + "." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证关系元数据
        if (entityMetadata.getRelationships() != null) {
            for (int i = 0; i < entityMetadata.getRelationships().size(); i++) {
                RelationshipMetadata relationship = entityMetadata.getRelationships().get(i);
                ValidationResult relationshipResult = validateRelationshipMetadata(relationship);
                if (!relationshipResult.isValid()) {
                    relationshipResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("relationships[" + i + "]." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证业务规则
        if (entityMetadata.getBusinessRules() != null) {
            for (int i = 0; i < entityMetadata.getBusinessRules().size(); i++) {
                BusinessRuleMetadata rule = entityMetadata.getBusinessRules().get(i);
                ValidationResult ruleResult = validateBusinessRuleMetadata(rule);
                if (!ruleResult.isValid()) {
                    ruleResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("businessRules[" + i + "]." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证流程元数据
        if (entityMetadata.getProcesses() != null) {
            for (int i = 0; i < entityMetadata.getProcesses().size(); i++) {
                ProcessMetadata process = entityMetadata.getProcesses().get(i);
                ValidationResult processResult = validateProcessMetadata(process);
                if (!processResult.isValid()) {
                    processResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("processes[" + i + "]." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证操作元数据
        if (entityMetadata.getOperations() != null) {
            for (Map.Entry<String, OperationMetadata> entry : entityMetadata.getOperations().entrySet()) {
                String operationName = entry.getKey();
                OperationMetadata operation = entry.getValue();
                
                // 验证操作名称一致性
                if (!operationName.equals(operation.getName())) {
                    errors.add(new ValidationError("operations", "Operation name in key ('" + operationName + "') does not match operation name in value ('" + operation.getName() + "')"));
                }
                
                // 验证操作元数据
                ValidationResult operationResult = validateOperationMetadata(operation);
                if (!operationResult.isValid()) {
                    operationResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("operations." + operationName + "." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证索引元数据
        if (entityMetadata.getIndexes() != null) {
            for (int i = 0; i < entityMetadata.getIndexes().size(); i++) {
                IndexMetadata index = entityMetadata.getIndexes().get(i);
                ValidationResult indexResult = validateIndexMetadata(index);
                if (!indexResult.isValid()) {
                    indexResult.getErrors().forEach(error -> 
                        errors.add(new ValidationError("indexes[" + i + "]." + error.getField(), error.getMessage()))
                    );
                }
            }
        }
        
        // 验证AI增强配置
        if (entityMetadata.getAiEnhancement() != null) {
            ValidationResult aiResult = validateAIEnhancement(entityMetadata.getAiEnhancement());
            if (!aiResult.isValid()) {
                aiResult.getErrors().forEach(error -> 
                    errors.add(new ValidationError("aiEnhancement." + error.getField(), error.getMessage()))
                );
            }
        }
        
        // 验证知识图谱配置
        if (entityMetadata.getKgConfig() != null && entityMetadata.getKgConfig().isEnabled()) {
            ValidationResult kgResult = validateKnowledgeGraphConfig(entityMetadata.getKgConfig());
            if (!kgResult.isValid()) {
                kgResult.getErrors().forEach(error -> 
                    errors.add(new ValidationError("kgConfig." + error.getField(), error.getMessage()))
                );
            }
        }
        
        // 验证MCP接口配置
        if (entityMetadata.getMcpInterface() != null) {
            ValidationResult mcpResult = validateMCPInterface(entityMetadata.getMcpInterface());
            if (!mcpResult.isValid()) {
                mcpResult.getErrors().forEach(error -> 
                    errors.add(new ValidationError("mcpInterface." + error.getField(), error.getMessage()))
                );
            }
        }
        
        // 验证权限配置
        if (entityMetadata.getPermissions() != null) {
            ValidationResult permissionResult = validateEntityPermissionMetadata(entityMetadata.getPermissions());
            if (!permissionResult.isValid()) {
                permissionResult.getErrors().forEach(error -> 
                    errors.add(new ValidationError("permissions." + error.getField(), error.getMessage()))
                );
            }
        }
        
        // 验证是否存在主键字段
        if (!hasPrimaryKey(entityMetadata)) {
            errors.add(new ValidationError("fields", "Entity must have at least one primary key field"));
        }
        
        // 验证关系中的字段是否存在
        validateRelationshipFields(entityMetadata, errors);
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证字段元数据
     */
    public ValidationResult validateFieldMetadata(FieldMetadata fieldMetadata) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (fieldMetadata.getName() == null || fieldMetadata.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "field name is required"));
        } else if (!FIELD_NAME_PATTERN.matcher(fieldMetadata.getName()).matches()) {
            errors.add(new ValidationError("name", "field name must match pattern ^[a-z][a-zA-Z0-9_]{1,49}$"));
        }
        
        if (fieldMetadata.getLabel() == null || fieldMetadata.getLabel().trim().isEmpty()) {
            errors.add(new ValidationError("label", "field label is required"));
        }
        
        if (fieldMetadata.getType() == null) {
            errors.add(new ValidationError("type", "field type is required"));
        } else {
            boolean validType = false;
            for (FieldType type : FieldType.values()) {
                if (type.name().equals(fieldMetadata.getType().name())) {
                    validType = true;
                    break;
                }
            }
            if (!validType) {
                errors.add(new ValidationError("type", "field type must be one of TEXT, PICKLIST, LOOKUP, CURRENCY, DATE, NUMBER"));
            }
        }
        
        // 验证最大长度
        if (fieldMetadata.getMaxLength() != null && fieldMetadata.getMaxLength() <= 0) {
            errors.add(new ValidationError("maxLength", "maxLength must be greater than 0"));
        }
        
        // 验证数值范围
        if (fieldMetadata.getMinValue() != null && fieldMetadata.getMaxValue() != null && 
            fieldMetadata.getMinValue() > fieldMetadata.getMaxValue()) {
            errors.add(new ValidationError("minValue/maxValue", "minValue must be less than or equal to maxValue"));
        }
        
        // 验证计算字段
        if (fieldMetadata.isCalculated() && (fieldMetadata.getCalculationExpression() == null || fieldMetadata.getCalculationExpression().trim().isEmpty())) {
            errors.add(new ValidationError("calculationExpression", "calculationExpression is required for calculated fields"));
        }
        
        // 验证选择列表
        if (FieldType.PICKLIST.equals(fieldMetadata.getType()) && 
            (fieldMetadata.getPicklistValues() == null || fieldMetadata.getPicklistValues().isEmpty())) {
            errors.add(new ValidationError("picklistValues", "picklistValues is required for PICKLIST type fields"));
        }
        
        // 验证选择列表值的唯一性
        if (fieldMetadata.getPicklistValues() != null && fieldMetadata.getPicklistValues().size() > 1) {
            List<String> values = fieldMetadata.getPicklistValues().stream()
                .map(PicklistValue::getValue)
                .collect(Collectors.toList());
            
            for (int i = 0; i < values.size(); i++) {
                for (int j = i + 1; j < values.size(); j++) {
                    if (values.get(i).equals(values.get(j))) {
                        errors.add(new ValidationError("picklistValues", "Duplicate picklist value: " + values.get(i)));
                        break;
                    }
                }
            }
        }
        
        // 验证查找字段
        if (FieldType.LOOKUP.equals(fieldMetadata.getType()) && fieldMetadata.getLookupEntity() == null) {
            errors.add(new ValidationError("lookupEntity", "lookupEntity is required for LOOKUP type fields"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证关系元数据
     */
    public ValidationResult validateRelationshipMetadata(RelationshipMetadata relationship) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (relationship.getName() == null || relationship.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "relationship name is required"));
        }
        
        if (relationship.getType() == null) {
            errors.add(new ValidationError("type", "relationship type is required"));
        }
        
        if (relationship.getTargetEntity() == null || relationship.getTargetEntity().trim().isEmpty()) {
            errors.add(new ValidationError("targetEntity", "targetEntity is required"));
        }
        
        // 验证级联操作
        if (relationship.getCascade() != null) {
            boolean validCascade = false;
            for (CascadeType type : CascadeType.values()) {
                if (type.name().equals(relationship.getCascade().name())) {
                    validCascade = true;
                    break;
                }
            }
            if (!validCascade) {
                errors.add(new ValidationError("cascade", "cascade must be one of ALL, PERSIST, MERGE, REMOVE"));
            }
        }
        
        // 验证批量大小
        if (relationship.getBatchSize() != null && relationship.getBatchSize() <= 0) {
            errors.add(new ValidationError("batchSize", "batchSize must be greater than 0"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证业务规则元数据
     */
    public ValidationResult validateBusinessRuleMetadata(BusinessRuleMetadata rule) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (rule.getName() == null || rule.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "rule name is required"));
        }
        
        if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
            errors.add(new ValidationError("expression", "rule expression is required"));
        }
        
        if (rule.getErrorMessage() == null || rule.getErrorMessage().trim().isEmpty()) {
            errors.add(new ValidationError("errorMessage", "errorMessage is required"));
        }
        
        // 验证严重性
        if (rule.getSeverity() != null) {
            boolean validSeverity = false;
            for (RuleSeverity severity : RuleSeverity.values()) {
                if (severity.name().equals(rule.getSeverity().name())) {
                    validSeverity = true;
                    break;
                }
            }
            if (!validSeverity) {
                errors.add(new ValidationError("severity", "severity must be one of ERROR, WARNING, INFO"));
            }
        }
        
        // 验证AI风险级别
        if (rule.getAiRiskLevel() != null) {
            boolean validRiskLevel = false;
            for (RiskLevel level : RiskLevel.values()) {
                if (level.name().equals(rule.getAiRiskLevel().name())) {
                    validRiskLevel = true;
                    break;
                }
            }
            if (!validRiskLevel) {
                errors.add(new ValidationError("aiRiskLevel", "aiRiskLevel must be one of HIGH, MEDIUM, LOW"));
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证流程元数据
     */
    public ValidationResult validateProcessMetadata(ProcessMetadata process) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (process.getName() == null || process.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "process name is required"));
        }
        
        if (process.getType() == null) {
            errors.add(new ValidationError("type", "process type is required"));
        } else {
            boolean validType = false;
            for (ProcessType type : ProcessType.values()) {
                if (type.name().equals(process.getType().name())) {
                    validType = true;
                    break;
                }
            }
            if (!validType) {
                errors.add(new ValidationError("type", "process type must be one of SEQUENTIAL, PARALLEL, STATE_MACHINE"));
            }
        }
        
        // 验证流程节点
        if (process.getNodes() == null || process.getNodes().isEmpty()) {
            errors.add(new ValidationError("nodes", "process must have at least one node"));
        }
        
        // 验证流程转换
        if (process.getTransitions() == null || process.getTransitions().isEmpty()) {
            errors.add(new ValidationError("transitions", "process must have at least one transition"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证操作元数据
     */
    public ValidationResult validateOperationMetadata(OperationMetadata operation) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (operation.getName() == null || operation.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "operation name is required"));
        }
        
        if (operation.getType() == null) {
            errors.add(new ValidationError("type", "operation type is required"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证索引元数据
     */
    public ValidationResult validateIndexMetadata(IndexMetadata index) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证必填字段
        if (index.getName() == null || index.getName().trim().isEmpty()) {
            errors.add(new ValidationError("name", "index name is required"));
        }
        
        if (index.getFields() == null || index.getFields().isEmpty()) {
            errors.add(new ValidationError("fields", "index must have at least one field"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证AI增强配置
     */
    public ValidationResult validateAIEnhancement(AIEnhancement aiEnhancement) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证智能标签配置
        if (aiEnhancement.getSmartTagging() != null && aiEnhancement.getSmartTagging().isEnabled()) {
            if (aiEnhancement.getSmartTagging().getFields() == null || aiEnhancement.getSmartTagging().getFields().isEmpty()) {
                errors.add(new ValidationError("smartTagging.fields", "smartTagging fields are required when enabled"));
            }
        }
        
        // 验证自动分类配置
        if (aiEnhancement.getAutoClassification() != null && aiEnhancement.getAutoClassification().isEnabled()) {
            if (aiEnhancement.getAutoClassification().getModelName() == null) {
                errors.add(new ValidationError("autoClassification.modelName", "autoClassification modelName is required when enabled"));
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证知识图谱配置
     */
    public ValidationResult validateKnowledgeGraphConfig(KnowledgeGraphConfig kgConfig) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (kgConfig.getEntityType() == null || kgConfig.getEntityType().trim().isEmpty()) {
            errors.add(new ValidationError("entityType", "entityType is required when knowledge graph is enabled"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证MCP接口配置
     */
    public ValidationResult validateMCPInterface(MCPInterface mcpInterface) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证MCP接口配置
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证实体权限配置
     */
    public ValidationResult validateEntityPermissionMetadata(EntityPermissionMetadata permission) {
        List<ValidationError> errors = new ArrayList<>();
        
        // 验证实体权限配置
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 检查实体是否有主键字段
     */
    private boolean hasPrimaryKey(EntityMetadata entityMetadata) {
        if (entityMetadata.getFields() == null) {
            return false;
        }
        
        return entityMetadata.getFields().values().stream()
            .anyMatch(FieldMetadata::isPrimaryKey);
    }
    
    /**
     * 验证关系中的字段是否存在
     */
    private void validateRelationshipFields(EntityMetadata entityMetadata, List<ValidationError> errors) {
        if (entityMetadata.getRelationships() == null || entityMetadata.getFields() == null) {
            return;
        }
        
        for (int i = 0; i < entityMetadata.getRelationships().size(); i++) {
            RelationshipMetadata relationship = entityMetadata.getRelationships().get(i);
            
            // 验证源字段
            if (relationship.getSourceField() != null && 
                !entityMetadata.getFields().containsKey(relationship.getSourceField())) {
                errors.add(new ValidationError("relationships[" + i + "].sourceField", 
                    "sourceField '" + relationship.getSourceField() + "' does not exist in entity fields"));
            }
        }
    }
    
    // ========== 验证结果类 ==========
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;
        
        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<ValidationError> getErrors() {
            return errors;
        }
        
        public String getErrorMessages() {
            return errors.stream()
                .map(error -> error.getField() + ": " + error.getMessage())
                .collect(Collectors.joining(", "));
        }
        
        @Override
        public String toString() {
            return "ValidationResult{valid=" + valid + ", errors=" + errors.size() + "}";
        }
    }
    
    /**
     * 验证错误类
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return "{field='" + field + "', message='" + message + "'}";
        }
    }
}