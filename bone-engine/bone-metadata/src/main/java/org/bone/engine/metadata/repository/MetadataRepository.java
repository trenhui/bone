package org.bone.engine.metadata.repository;

import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.validator.MetadataValidator.ValidationResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 元数据存储库接口 - 提供元数据的CRUD操作和查询功能
 * 
 * @author Bone Engine Team
 */
public interface MetadataRepository {
    
    /**
     * 保存实体元数据
     * 
     * @param entityMetadata 实体元数据
     * @return 保存后的实体元数据
     */
    EntityMetadata save(EntityMetadata entityMetadata);
    
    /**
     * 批量保存实体元数据
     * 
     * @param entityMetadataList 实体元数据列表
     * @return 保存结果映射，键为实体API名称，值为验证结果
     */
    Map<String, ValidationResult> saveAll(List<EntityMetadata> entityMetadataList);
    
    /**
     * 根据API名称获取实体元数据
     * 
     * @param apiName 实体API名称
     * @return 实体元数据，不存在则返回空
     */
    Optional<EntityMetadata> findByApiName(String apiName);
    
    /**
     * 根据API名称获取实体元数据，如果不存在则抛出异常
     * 
     * @param apiName 实体API名称
     * @return 实体元数据
     */
    EntityMetadata getByApiName(String apiName);
    
    /**
     * 查询所有实体元数据
     * 
     * @return 实体元数据列表
     */
    List<EntityMetadata> findAll();
    
    /**
     * 根据条件查询实体元数据
     * 
     * @param criteria 查询条件
     * @return 实体元数据列表
     */
    List<EntityMetadata> findByCriteria(MetadataCriteria criteria);
    
    /**
     * 根据业务域查询实体元数据
     * 
     * @param domain 业务域
     * @return 实体元数据列表
     */
    List<EntityMetadata> findByDomain(String domain);
    
    /**
     * 根据实体类型查询实体元数据
     * 
     * @param entityType 实体类型
     * @return 实体元数据列表
     */
    List<EntityMetadata> findByEntityType(String entityType);
    
    /**
     * 查询包含指定字段类型的实体元数据
     * 
     * @param fieldType 字段类型
     * @return 实体元数据列表
     */
    List<EntityMetadata> findByFieldType(String fieldType);
    
    /**
     * 查询与指定实体有关系的实体元数据
     * 
     * @param targetEntityApiName 目标实体API名称
     * @return 实体元数据列表
     */
    List<EntityMetadata> findByRelatedEntity(String targetEntityApiName);
    
    /**
     * 更新实体元数据
     * 
     * @param entityMetadata 实体元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata update(EntityMetadata entityMetadata);
    
    /**
     * 根据API名称删除实体元数据
     * 
     * @param apiName 实体API名称
     * @return 是否删除成功
     */
    boolean deleteByApiName(String apiName);
    
    /**
     * 批量删除实体元数据
     * 
     * @param apiNames 实体API名称列表
     * @return 删除的数量
     */
    int deleteByApiNames(List<String> apiNames);
    
    /**
     * 检查实体元数据是否存在
     * 
     * @param apiName 实体API名称
     * @return 是否存在
     */
    boolean existsByApiName(String apiName);
    
    /**
     * 统计实体元数据数量
     * 
     * @return 实体元数据数量
     */
    long count();
    
    /**
     * 根据业务域统计实体元数据数量
     * 
     * @param domain 业务域
     * @return 实体元数据数量
     */
    long countByDomain(String domain);
    
    /**
     * 刷新元数据缓存
     */
    void refreshCache();
    
    /**
     * 清除指定实体的元数据缓存
     * 
     * @param apiName 实体API名称
     */
    void clearCache(String apiName);
    
    /**
     * 导出实体元数据为JSON格式
     * 
     * @param apiName 实体API名称
     * @return JSON字符串
     */
    String exportToJson(String apiName);
    
    /**
     * 导出所有实体元数据为JSON格式
     * 
     * @return JSON字符串数组
     */
    List<String> exportAllToJson();
    
    /**
     * 从JSON格式导入实体元数据
     * 
     * @param json JSON字符串
     * @return 导入的实体元数据
     */
    EntityMetadata importFromJson(String json);
    
    /**
     * 从JSON格式批量导入实体元数据
     * 
     * @param jsonList JSON字符串列表
     * @return 导入结果映射
     */
    Map<String, ValidationResult> importAllFromJson(List<String> jsonList);
    
    /**
     * 检查元数据一致性
     * 
     * @return 一致性检查结果
     */
    ConsistencyCheckResult checkConsistency();
    
    /**
     * 修复元数据不一致问题
     * 
     * @return 修复结果
     */
    ConsistencyCheckResult repairConsistency();
    
    // ========== 内部类定义 ==========
    
    /**
     * 元数据查询条件类
     */
    interface MetadataCriteria {
        /**
         * 设置API名称模糊查询
         */
        MetadataCriteria apiNameLike(String apiName);
        
        /**
         * 设置业务域
         */
        MetadataCriteria domain(String domain);
        
        /**
         * 设置实体类型
         */
        MetadataCriteria entityType(String entityType);
        
        /**
         * 设置包含指定字段
         */
        MetadataCriteria containsField(String fieldName);
        
        /**
         * 设置包含指定字段类型
         */
        MetadataCriteria containsFieldType(String fieldType);
        
        /**
         * 设置包含指定关系
         */
        MetadataCriteria containsRelationship(String relationshipName);
        
        /**
         * 设置包含指定目标实体关系
         */
        MetadataCriteria containsTargetEntity(String targetEntityApiName);
        
        /**
         * 设置版本范围
         */
        MetadataCriteria versionBetween(String minVersion, String maxVersion);
        
        /**
         * 设置启用AI增强
         */
        MetadataCriteria aiEnhancementEnabled(boolean enabled);
        
        /**
         * 设置启用知识图谱
         */
        MetadataCriteria knowledgeGraphEnabled(boolean enabled);
        
        /**
         * 设置启用MCP接口
         */
        MetadataCriteria mcpInterfaceEnabled(boolean enabled);
        
        /**
         * 设置分页参数
         */
        MetadataCriteria page(int page, int size);
        
        /**
         * 设置排序
         */
        MetadataCriteria sortBy(String field, SortDirection direction);
    }
    
    /**
     * 排序方向枚举
     */
    enum SortDirection {
        ASC,
        DESC
    }
    
    /**
     * 一致性检查结果类
     */
    interface ConsistencyCheckResult {
        /**
         * 是否一致
         */
        boolean isConsistent();
        
        /**
         * 获取不一致项列表
         */
        List<Inconsistency> getInconsistencies();
        
        /**
         * 获取修复的不一致项数量
         */
        int getFixedCount();
        
        /**
         * 获取未修复的不一致项数量
         */
        int getUnfixedCount();
    }
    
    /**
     * 不一致项类
     */
    interface Inconsistency {
        /**
         * 获取不一致类型
         */
        InconsistencyType getType();
        
        /**
         * 获取实体API名称
         */
        String getEntityApiName();
        
        /**
         * 获取不一致描述
         */
        String getDescription();
        
        /**
         * 是否已修复
         */
        boolean isFixed();
        
        /**
         * 获取修复消息
         */
        String getFixMessage();
    }
    
    /**
     * 不一致类型枚举
     */
    enum InconsistencyType {
        MISSING_PRIMARY_KEY,
        DUPLICATE_API_NAME,
        RELATIONSHIP_TARGET_NOT_FOUND,
        FIELD_REFERENCE_NOT_FOUND,
        INVALID_VERSION_FORMAT,
        INVALID_API_NAME_FORMAT,
        INVALID_FIELD_NAME_FORMAT,
        MISSING_REQUIRED_FIELD,
        CIRCULAR_RELATIONSHIP,
        INCONSISTENT_FIELD_MAPPING
    }
    
    /**
     * 创建默认的元数据查询条件
     * 
     * @return 元数据查询条件
     */
    static MetadataCriteria createCriteria() {
        return new DefaultMetadataCriteria();
    }
    
    /**
     * 默认元数据查询条件实现
     */
    class DefaultMetadataCriteria implements MetadataCriteria {
        private String apiNameLike;
        private String domain;
        private String entityType;
        private String containsField;
        private String containsFieldType;
        private String containsRelationship;
        private String containsTargetEntity;
        private String minVersion;
        private String maxVersion;
        private Boolean aiEnhancementEnabled;
        private Boolean knowledgeGraphEnabled;
        private Boolean mcpInterfaceEnabled;
        private Integer page;
        private Integer size;
        private String sortField;
        private SortDirection sortDirection;
        
        @Override
        public MetadataCriteria apiNameLike(String apiName) {
            this.apiNameLike = apiName;
            return this;
        }
        
        @Override
        public MetadataCriteria domain(String domain) {
            this.domain = domain;
            return this;
        }
        
        @Override
        public MetadataCriteria entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }
        
        @Override
        public MetadataCriteria containsField(String fieldName) {
            this.containsField = fieldName;
            return this;
        }
        
        @Override
        public MetadataCriteria containsFieldType(String fieldType) {
            this.containsFieldType = fieldType;
            return this;
        }
        
        @Override
        public MetadataCriteria containsRelationship(String relationshipName) {
            this.containsRelationship = relationshipName;
            return this;
        }
        
        @Override
        public MetadataCriteria containsTargetEntity(String targetEntityApiName) {
            this.containsTargetEntity = targetEntityApiName;
            return this;
        }
        
        @Override
        public MetadataCriteria versionBetween(String minVersion, String maxVersion) {
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
            return this;
        }
        
        @Override
        public MetadataCriteria aiEnhancementEnabled(boolean enabled) {
            this.aiEnhancementEnabled = enabled;
            return this;
        }
        
        @Override
        public MetadataCriteria knowledgeGraphEnabled(boolean enabled) {
            this.knowledgeGraphEnabled = enabled;
            return this;
        }
        
        @Override
        public MetadataCriteria mcpInterfaceEnabled(boolean enabled) {
            this.mcpInterfaceEnabled = enabled;
            return this;
        }
        
        @Override
        public MetadataCriteria page(int page, int size) {
            this.page = page;
            this.size = size;
            return this;
        }
        
        @Override
        public MetadataCriteria sortBy(String field, SortDirection direction) {
            this.sortField = field;
            this.sortDirection = direction;
            return this;
        }
        
        // Getters
        public String getApiNameLike() {
            return apiNameLike;
        }
        
        public String getDomain() {
            return domain;
        }
        
        public String getEntityType() {
            return entityType;
        }
        
        public String getContainsField() {
            return containsField;
        }
        
        public String getContainsFieldType() {
            return containsFieldType;
        }
        
        public String getContainsRelationship() {
            return containsRelationship;
        }
        
        public String getContainsTargetEntity() {
            return containsTargetEntity;
        }
        
        public String getMinVersion() {
            return minVersion;
        }
        
        public String getMaxVersion() {
            return maxVersion;
        }
        
        public Boolean getAiEnhancementEnabled() {
            return aiEnhancementEnabled;
        }
        
        public Boolean getKnowledgeGraphEnabled() {
            return knowledgeGraphEnabled;
        }
        
        public Boolean getMcpInterfaceEnabled() {
            return mcpInterfaceEnabled;
        }
        
        public Integer getPage() {
            return page;
        }
        
        public Integer getSize() {
            return size;
        }
        
        public String getSortField() {
            return sortField;
        }
        
        public SortDirection getSortDirection() {
            return sortDirection;
        }
    }
    
    /**
     * 默认一致性检查结果实现
     */
    class DefaultConsistencyCheckResult implements ConsistencyCheckResult {
        private boolean consistent;
        private List<Inconsistency> inconsistencies;
        private int fixedCount;
        private int unfixedCount;
        
        public DefaultConsistencyCheckResult(boolean consistent, List<Inconsistency> inconsistencies) {
            this.consistent = consistent;
            this.inconsistencies = inconsistencies;
            calculateCounts();
        }
        
        private void calculateCounts() {
            if (inconsistencies != null) {
                fixedCount = 0;
                unfixedCount = 0;
                for (Inconsistency inconsistency : inconsistencies) {
                    if (inconsistency.isFixed()) {
                        fixedCount++;
                    } else {
                        unfixedCount++;
                    }
                }
            }
        }
        
        @Override
        public boolean isConsistent() {
            return consistent;
        }
        
        @Override
        public List<Inconsistency> getInconsistencies() {
            return inconsistencies;
        }
        
        @Override
        public int getFixedCount() {
            return fixedCount;
        }
        
        @Override
        public int getUnfixedCount() {
            return unfixedCount;
        }
        
        public void setConsistent(boolean consistent) {
            this.consistent = consistent;
        }
        
        public void setInconsistencies(List<Inconsistency> inconsistencies) {
            this.inconsistencies = inconsistencies;
            calculateCounts();
        }
    }
    
    /**
     * 默认不一致项实现
     */
    class DefaultInconsistency implements Inconsistency {
        private InconsistencyType type;
        private String entityApiName;
        private String description;
        private boolean fixed;
        private String fixMessage;
        
        public DefaultInconsistency(InconsistencyType type, String entityApiName, String description) {
            this.type = type;
            this.entityApiName = entityApiName;
            this.description = description;
            this.fixed = false;
        }
        
        @Override
        public InconsistencyType getType() {
            return type;
        }
        
        @Override
        public String getEntityApiName() {
            return entityApiName;
        }
        
        @Override
        public String getDescription() {
            return description;
        }
        
        @Override
        public boolean isFixed() {
            return fixed;
        }
        
        @Override
        public String getFixMessage() {
            return fixMessage;
        }
        
        public void setFixed(boolean fixed) {
            this.fixed = fixed;
        }
        
        public void setFixMessage(String fixMessage) {
            this.fixMessage = fixMessage;
        }
    }
}