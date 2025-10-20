package org.bone.engine.metadata.service;

import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.model.FieldMetadata;
import org.bone.engine.metadata.model.RelationshipMetadata;
import org.bone.engine.metadata.repository.MetadataRepository;
import org.bone.engine.metadata.validator.MetadataValidator.ValidationResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 元数据服务接口 - 提供元数据管理的核心业务逻辑
 * 
 * @author Bone Engine Team
 */
public interface MetadataService {
    
    /**
     * 创建实体元数据
     * 
     * @param entityMetadata 实体元数据
     * @return 创建的实体元数据
     */
    EntityMetadata createEntityMetadata(EntityMetadata entityMetadata);
    
    /**
     * 批量创建实体元数据
     * 
     * @param entityMetadataList 实体元数据列表
     * @return 创建结果映射
     */
    Map<String, ValidationResult> batchCreateEntityMetadata(List<EntityMetadata> entityMetadataList);
    
    /**
     * 获取实体元数据
     * 
     * @param apiName 实体API名称
     * @return 实体元数据
     */
    EntityMetadata getEntityMetadata(String apiName);
    
    /**
     * 获取实体元数据（可选）
     * 
     * @param apiName 实体API名称
     * @return 实体元数据可选值
     */
    Optional<EntityMetadata> findEntityMetadata(String apiName);
    
    /**
     * 查询所有实体元数据
     * 
     * @return 实体元数据列表
     */
    List<EntityMetadata> getAllEntityMetadata();
    
    /**
     * 根据条件查询实体元数据
     * 
     * @param criteria 查询条件
     * @return 实体元数据列表
     */
    List<EntityMetadata> queryEntityMetadata(MetadataRepository.MetadataCriteria criteria);
    
    /**
     * 根据业务域查询实体元数据
     * 
     * @param domain 业务域
     * @return 实体元数据列表
     */
    List<EntityMetadata> getEntityMetadataByDomain(String domain);
    
    /**
     * 根据实体类型查询实体元数据
     * 
     * @param entityType 实体类型
     * @return 实体元数据列表
     */
    List<EntityMetadata> getEntityMetadataByType(String entityType);
    
    /**
     * 更新实体元数据
     * 
     * @param apiName 实体API名称
     * @param entityMetadata 更新后的实体元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata updateEntityMetadata(String apiName, EntityMetadata entityMetadata);
    
    /**
     * 部分更新实体元数据
     * 
     * @param apiName 实体API名称
     * @param updates 更新字段映射
     * @return 更新后的实体元数据
     */
    EntityMetadata partialUpdateEntityMetadata(String apiName, Map<String, Object> updates);
    
    /**
     * 删除实体元数据
     * 
     * @param apiName 实体API名称
     * @return 是否删除成功
     */
    boolean deleteEntityMetadata(String apiName);
    
    /**
     * 批量删除实体元数据
     * 
     * @param apiNames 实体API名称列表
     * @return 删除的数量
     */
    int batchDeleteEntityMetadata(List<String> apiNames);
    
    /**
     * 检查实体元数据是否存在
     * 
     * @param apiName 实体API名称
     * @return 是否存在
     */
    boolean existsEntityMetadata(String apiName);
    
    /**
     * 添加字段到实体
     * 
     * @param apiName 实体API名称
     * @param fieldMetadata 字段元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata addField(String apiName, FieldMetadata fieldMetadata);
    
    /**
     * 批量添加字段到实体
     * 
     * @param apiName 实体API名称
     * @param fieldMetadataList 字段元数据列表
     * @return 更新后的实体元数据
     */
    EntityMetadata batchAddFields(String apiName, List<FieldMetadata> fieldMetadataList);
    
    /**
     * 更新字段元数据
     * 
     * @param apiName 实体API名称
     * @param fieldName 字段名称
     * @param fieldMetadata 更新后的字段元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata updateField(String apiName, String fieldName, FieldMetadata fieldMetadata);
    
    /**
     * 删除字段
     * 
     * @param apiName 实体API名称
     * @param fieldName 字段名称
     * @return 更新后的实体元数据
     */
    EntityMetadata deleteField(String apiName, String fieldName);
    
    /**
     * 添加关系到实体
     * 
     * @param apiName 实体API名称
     * @param relationshipMetadata 关系元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata addRelationship(String apiName, RelationshipMetadata relationshipMetadata);
    
    /**
     * 更新关系元数据
     * 
     * @param apiName 实体API名称
     * @param relationshipName 关系名称
     * @param relationshipMetadata 更新后的关系元数据
     * @return 更新后的实体元数据
     */
    EntityMetadata updateRelationship(String apiName, String relationshipName, RelationshipMetadata relationshipMetadata);
    
    /**
     * 删除关系
     * 
     * @param apiName 实体API名称
     * @param relationshipName 关系名称
     * @return 更新后的实体元数据
     */
    EntityMetadata deleteRelationship(String apiName, String relationshipName);
    
    /**
     * 获取实体的所有依赖关系
     * 
     * @param apiName 实体API名称
     * @return 依赖关系列表
     */
    List<EntityMetadata> getDependencies(String apiName);
    
    /**
     * 获取依赖于指定实体的所有实体
     * 
     * @param apiName 实体API名称
     * @return 被依赖实体列表
     */
    List<EntityMetadata> getDependents(String apiName);
    
    /**
     * 验证实体元数据
     * 
     * @param entityMetadata 实体元数据
     * @return 验证结果
     */
    ValidationResult validateEntityMetadata(EntityMetadata entityMetadata);
    
    /**
     * 验证字段元数据
     * 
     * @param fieldMetadata 字段元数据
     * @return 验证结果
     */
    ValidationResult validateFieldMetadata(FieldMetadata fieldMetadata);
    
    /**
     * 验证关系元数据
     * 
     * @param relationshipMetadata 关系元数据
     * @return 验证结果
     */
    ValidationResult validateRelationshipMetadata(RelationshipMetadata relationshipMetadata);
    
    /**
     * 导出实体元数据
     * 
     * @param apiName 实体API名称
     * @return JSON字符串
     */
    String exportEntityMetadata(String apiName);
    
    /**
     * 导出多个实体元数据
     * 
     * @param apiNames 实体API名称列表
     * @return JSON字符串列表
     */
    List<String> exportEntityMetadataList(List<String> apiNames);
    
    /**
     * 导出所有实体元数据
     * 
     * @return JSON字符串列表
     */
    List<String> exportAllEntityMetadata();
    
    /**
     * 导入实体元数据
     * 
     * @param json JSON字符串
     * @return 导入的实体元数据
     */
    EntityMetadata importEntityMetadata(String json);
    
    /**
     * 批量导入实体元数据
     * 
     * @param jsonList JSON字符串列表
     * @return 导入结果映射
     */
    Map<String, ValidationResult> batchImportEntityMetadata(List<String> jsonList);
    
    /**
     * 复制实体元数据
     * 
     * @param sourceApiName 源实体API名称
     * @param newApiName 新实体API名称
     * @param newLabel 新实体标签
     * @return 新创建的实体元数据
     */
    EntityMetadata copyEntityMetadata(String sourceApiName, String newApiName, String newLabel);
    
    /**
     * 比较两个实体元数据的差异
     * 
     * @param apiName1 第一个实体API名称
     * @param apiName2 第二个实体API名称
     * @return 差异信息映射
     */
    Map<String, Object> compareEntityMetadata(String apiName1, String apiName2);
    
    /**
     * 生成实体元数据的版本快照
     * 
     * @param apiName 实体API名称
     * @return 版本号
     */
    String createVersionSnapshot(String apiName);
    
    /**
     * 回滚到指定版本
     * 
     * @param apiName 实体API名称
     * @param version 版本号
     * @return 更新后的实体元数据
     */
    EntityMetadata rollbackToVersion(String apiName, String version);
    
    /**
     * 获取实体元数据的所有版本
     * 
     * @param apiName 实体API名称
     * @return 版本列表
     */
    List<Map<String, Object>> getEntityVersions(String apiName);
    
    /**
     * 刷新元数据缓存
     */
    void refreshMetadataCache();
    
    /**
     * 清除指定实体的元数据缓存
     * 
     * @param apiName 实体API名称
     */
    void clearEntityCache(String apiName);
    
    /**
     * 统计元数据信息
     * 
     * @return 统计信息映射
     */
    Map<String, Object> getMetadataStatistics();
    
    /**
     * 检查元数据一致性
     * 
     * @return 一致性检查结果
     */
    MetadataRepository.ConsistencyCheckResult checkMetadataConsistency();
    
    /**
     * 修复元数据不一致问题
     * 
     * @return 修复结果
     */
    MetadataRepository.ConsistencyCheckResult repairMetadataConsistency();
    
    /**
     * 生成实体元数据的文档
     * 
     * @param apiName 实体API名称
     * @return 文档内容
     */
    String generateEntityDocumentation(String apiName);
    
    /**
     * 生成所有实体元数据的文档
     * 
     * @return 文档内容映射
     */
    Map<String, String> generateAllEntityDocumentation();
    
    /**
     * 获取实体的推荐字段
     * 
     * @param apiName 实体API名称
     * @return 推荐字段列表
     */
    List<FieldMetadata> getRecommendedFields(String apiName);
    
    /**
     * 获取实体的推荐关系
     * 
     * @param apiName 实体API名称
     * @return 推荐关系列表
     */
    List<RelationshipMetadata> getRecommendedRelationships(String apiName);
    
    /**
     * 分析实体间的关系图
     * 
     * @return 关系图数据
     */
    Map<String, Object> analyzeRelationshipGraph();
    
    /**
     * 优化实体元数据结构
     * 
     * @param apiName 实体API名称
     * @return 优化建议
     */
    List<String> optimizeEntityStructure(String apiName);
    
    /**
     * 检测实体元数据的潜在问题
     * 
     * @param apiName 实体API名称
     * @return 问题列表
     */
    List<String> detectPotentialIssues(String apiName);
    
    /**
     * 根据业务规则验证实体元数据
     * 
     * @param apiName 实体API名称
     * @param businessRules 业务规则列表
     * @return 验证结果
     */
    Map<String, ValidationResult> validateByBusinessRules(String apiName, List<String> businessRules);
}