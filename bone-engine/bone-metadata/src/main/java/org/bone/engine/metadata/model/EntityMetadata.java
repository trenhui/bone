package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.*;

// 需要导入缺失的类型
// 已移除未定义类型的导入

/**
 * 实体元数据模型 - 对应Universal Metadata Protocol (UMP)规范
 * 包含实体的字段、关系、业务规则、流程、操作等完整定义
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityMetadata {

    // ================ 核心属性 ================
    
    /**
     * 全局唯一标识
     */
    private String apiName;
    
    /**
     * 显示名称
     */
    private String label;
    
    /**
     * 多语言标签
     */
    private Map<String, String> labels;
    
    /**
     * 业务描述
     */
    private String description;
    
    /**
     * 业务域
     */
    private String domain;
    
    /**
     * 实体类型
     */
    private String entityType;
    
    /**
     * 字段定义
     */
    private Map<String, FieldMetadata> fields;
    
    /**
     * 关系定义
     */
    private List<RelationshipMetadata> relationships;
    
    /**
     * 业务规则
     */
    private List<BusinessRuleMetadata> businessRules;
    
    /**
     * 流程定义
     */
    private List<ProcessMetadata> processes;
    
    /**
     * 操作定义
     */
    private List<Map<String, Object>> operations;
    
    /**
     * 索引定义
     */
    private List<Map<String, Object>> indexes;
    
    /**
     * 权限配置
     */
    private Map<String, Object> permissions;
    
    /**
     * 父实体（继承）
     */
    private String parentEntity;
    
    /**
     * 语义化版本
     */
    private String version;
    
    // ================ 扩展属性 ================
    
    /**
     * AI增强配置
     */
    private Map<String, Object> aiEnhancement;
    
    /**
     * 知识图谱配置
     */
    private Map<String, Object> kgConfig;
    
    /**
     * MCP协议接口
     */
    private MCPInterface mcpInterface;
    
    /**
     * 是否启用审计
     */
    private Boolean audited;
    
    /**
     * 是否可缓存
     */
    private Boolean cacheable;
    
    /**
     * 缓存过期时间（秒）
     */
    private Integer cacheTTL;
    
    /**
     * 主键字段名称
     */
    private String primaryKeyField;
    
    /**
     * 扩展属性
     */
    private JsonNode extensions;
    
    // ================ 审计信息 ================
    
    /**
     * 创建人
     */
    private String createdBy;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新人
     */
    private String updatedBy;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    // ================ 构造方法与辅助方法 ================
    
    public EntityMetadata() {
        this.fields = new HashMap<>();
        this.relationships = new ArrayList<>();
        this.businessRules = new ArrayList<>();
        this.processes = new ArrayList<>();
        this.operations = new ArrayList<>();
        this.indexes = new ArrayList<>();
        this.labels = new HashMap<>();
        this.permissions = new HashMap<>();
        this.aiEnhancement = new HashMap<>();
        this.kgConfig = new HashMap<>();
        this.mcpInterface = new MCPInterface();
        this.audited = Boolean.FALSE;
        this.cacheable = Boolean.TRUE;
        this.cacheTTL = 3600;
        this.primaryKeyField = "id";
    }
    
    /**
     * 添加字段定义
     */
    public EntityMetadata addField(FieldMetadata field) {
        if (this.fields == null) {
            this.fields = new HashMap<>();
        }
        this.fields.put(field.getName(), field);
        return this;
    }
    
    /**
     * 添加关系定义
     */
    public EntityMetadata addRelationship(RelationshipMetadata relationship) {
        if (this.relationships == null) {
            this.relationships = new ArrayList<>();
        }
        this.relationships.add(relationship);
        return this;
    }
    
    /**
     * 添加业务规则
     */
    public EntityMetadata addBusinessRule(BusinessRuleMetadata rule) {
        if (this.businessRules == null) {
            this.businessRules = new ArrayList<>();
        }
        this.businessRules.add(rule);
        return this;
    }
    
    /**
     * 添加流程定义
     */
    public EntityMetadata addProcess(ProcessMetadata process) {
        if (this.processes == null) {
            this.processes = new ArrayList<>();
        }
        this.processes.add(process);
        return this;
    }
    
    /**
     * 添加操作定义
     */
    public EntityMetadata addOperation(Map<String, Object> operation) {
        if (this.operations == null) {
            this.operations = new ArrayList<>();
        }
        this.operations.add(operation);
        return this;
    }
    
    /**
     * 添加索引定义
     */
    public EntityMetadata addIndex(Map<String, Object> index) {
        if (this.indexes == null) {
            this.indexes = new ArrayList<>();
        }
        this.indexes.add(index);
        return this;
    }
    
    /**
     * 获取主键字段
     */
    public FieldMetadata getPrimaryKeyFieldMetadata() {
        if (primaryKeyField == null || fields == null) {
            return null;
        }
        return fields.get(primaryKeyField);
    }
    
    /**
     * 检查是否包含指定字段
     */
    public boolean hasField(String fieldName) {
        return fields != null && fields.containsKey(fieldName);
    }
    
    /**
     * 获取所有必填字段
     */
    public List<FieldMetadata> getRequiredFields() {
        List<FieldMetadata> requiredFields = new ArrayList<>();
        if (fields != null) {
            fields.values().stream()
                  .filter(FieldMetadata::getRequired)
                  .forEach(requiredFields::add);
        }
        return requiredFields;
    }
    
    /**
     * 获取所有关联关系
     */
    public List<RelationshipMetadata> getRelationshipsByType(String relationshipType) {
        List<RelationshipMetadata> filteredRelationships = new ArrayList<>();
        if (relationships != null) {
            relationships.stream()
                         .filter(r -> r.getType().equals(relationshipType))
                         .forEach(filteredRelationships::add);
        }
        return filteredRelationships;
    }
    
    /**
     * MCP协议接口内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MCPInterface {
        private String dataIngestion;
        private String metadataTagging;
        private String interoperability;
    }
}