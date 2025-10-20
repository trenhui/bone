package com.bone.smartmeta.engine.analysis;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据影响分析器
 * 分析元数据变更对系统的潜在影响，包括依赖实体、工作流和业务规则的影响分析
 */
public class MetadataImpactAnalyzer {
    
    private final MetadataRepository metadataRepository;
    
    public MetadataImpactAnalyzer(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }
    
    /**
     * 分析实体元数据变更的影响
     */
    public ImpactAnalysisResult analyzeEntityImpact(String tenantId, EntityMetadata oldMetadata, 
                                                   EntityMetadata newMetadata) {
        ImpactAnalysisResult result = new ImpactAnalysisResult();
        
        // 分析字段变更影响
        analyzeFieldChanges(oldMetadata, newMetadata, result);
        
        // 分析关系变更影响
        analyzeRelationChanges(oldMetadata, newMetadata, result);
        
        // 分析引用该实体的其他实体
        analyzeDependentEntities(tenantId, oldMetadata.getApiName(), result);
        
        // 分析相关工作流
        analyzeRelatedWorkflows(tenantId, oldMetadata.getApiName(), result);
        
        // 分析业务规则影响
        analyzeBusinessRulesImpact(tenantId, oldMetadata, newMetadata, result);
        
        // 评估影响级别
        evaluateImpactLevel(result);
        
        return result;
    }
    
    /**
     * 分析字段变更影响
     */
    private void analyzeFieldChanges(EntityMetadata oldMetadata, EntityMetadata newMetadata, 
                                    ImpactAnalysisResult result) {
        // 分析删除的字段
        Map<String, FieldMetadata> newFieldsMap = newMetadata.getFields().stream()
                .collect(Collectors.toMap(FieldMetadata::getName, f -> f));
        
        for (FieldMetadata oldField : oldMetadata.getFields()) {
            if (!newFieldsMap.containsKey(oldField.getName())) {
                result.addDeletedField(oldField.getName());
                // 检查字段是否为主键或必填
                if (oldField.isPrimaryKey()) {
                    result.addCriticalImpact("删除主键字段: " + oldField.getName());
                }
                if (oldField.isRequired()) {
                    result.addHighImpact("删除必填字段: " + oldField.getName());
                }
            }
        }
        
        // 分析修改的字段
        Map<String, FieldMetadata> oldFieldsMap = oldMetadata.getFields().stream()
                .collect(Collectors.toMap(FieldMetadata::getName, f -> f));
        
        for (FieldMetadata newField : newMetadata.getFields()) {
            FieldMetadata oldField = oldFieldsMap.get(newField.getName());
            if (oldField != null) {
                // 检查类型变更
                if (!oldField.getType().equals(newField.getType())) {
                    result.addHighImpact("字段类型变更: " + newField.getName() + 
                                        " (" + oldField.getType() + " -> " + newField.getType() + ")");
                }
                
                // 检查必填性变更
                if (!oldField.isRequired() && newField.isRequired()) {
                    result.addMediumImpact("字段变为必填: " + newField.getName());
                }
                
                // 检查长度限制变更
                if (oldField.getMaxLength() != null && newField.getMaxLength() != null &&
                    oldField.getMaxLength() > newField.getMaxLength()) {
                    result.addMediumImpact("字段长度缩短: " + newField.getName() + 
                                          " (" + oldField.getMaxLength() + " -> " + newField.getMaxLength() + ")");
                }
            }
        }
    }
    
    /**
     * 分析关系变更影响
     */
    private void analyzeRelationChanges(EntityMetadata oldMetadata, EntityMetadata newMetadata, 
                                      ImpactAnalysisResult result) {
        // TODO: 实现关系变更分析
    }
    
    /**
     * 分析依赖该实体的其他实体
     */
    private void analyzeDependentEntities(String tenantId, String entityApiName, 
                                        ImpactAnalysisResult result) {
        // TODO: 实现依赖实体分析
    }
    
    /**
     * 分析相关工作流
     */
    private void analyzeRelatedWorkflows(String tenantId, String entityApiName, 
                                        ImpactAnalysisResult result) {
        // TODO: 实现工作流影响分析
    }
    
    /**
     * 分析业务规则影响
     */
    private void analyzeBusinessRulesImpact(String tenantId, EntityMetadata oldMetadata, 
                                           EntityMetadata newMetadata, ImpactAnalysisResult result) {
        // TODO: 实现业务规则影响分析
    }
    
    /**
     * 评估影响级别
     */
    private void evaluateImpactLevel(ImpactAnalysisResult result) {
        if (!result.getCriticalImpacts().isEmpty()) {
            result.setImpactLevel(ImpactLevel.CRITICAL);
        } else if (!result.getHighImpacts().isEmpty()) {
            result.setImpactLevel(ImpactLevel.HIGH);
        } else if (!result.getMediumImpacts().isEmpty()) {
            result.setImpactLevel(ImpactLevel.MEDIUM);
        } else {
            result.setImpactLevel(ImpactLevel.LOW);
        }
    }
    
    /**
     * 影响级别枚举
     */
    public enum ImpactLevel {
        CRITICAL, // 严重影响，可能导致系统崩溃
        HIGH,     // 高影响，需要停机更新
        MEDIUM,   // 中等影响，需要注意兼容性
        LOW       // 低影响，几乎无兼容性问题
    }
    
    /**
     * 影响分析结果
     */
    public static class ImpactAnalysisResult {
        private ImpactLevel impactLevel = ImpactLevel.LOW;
        private List<String> criticalImpacts = new ArrayList<>();
        private List<String> highImpacts = new ArrayList<>();
        private List<String> mediumImpacts = new ArrayList<>();
        private List<String> lowImpacts = new ArrayList<>();
        private List<String> affectedEntities = new ArrayList<>();
        private List<String> affectedWorkflows = new ArrayList<>();
        private List<String> deletedFields = new ArrayList<>();
        private List<String> addedFields = new ArrayList<>();
        private List<String> modifiedFields = new ArrayList<>();
        
        // Getters and setters
        public ImpactLevel getImpactLevel() {
            return impactLevel;
        }
        
        public void setImpactLevel(ImpactLevel impactLevel) {
            this.impactLevel = impactLevel;
        }
        
        public List<String> getCriticalImpacts() {
            return criticalImpacts;
        }
        
        public void addCriticalImpact(String impact) {
            this.criticalImpacts.add(impact);
        }
        
        public List<String> getHighImpacts() {
            return highImpacts;
        }
        
        public void addHighImpact(String impact) {
            this.highImpacts.add(impact);
        }
        
        public List<String> getMediumImpacts() {
            return mediumImpacts;
        }
        
        public void addMediumImpact(String impact) {
            this.mediumImpacts.add(impact);
        }
        
        public List<String> getLowImpacts() {
            return lowImpacts;
        }
        
        public void addLowImpact(String impact) {
            this.lowImpacts.add(impact);
        }
        
        public List<String> getAffectedEntities() {
            return affectedEntities;
        }
        
        public void addAffectedEntity(String entity) {
            this.affectedEntities.add(entity);
        }
        
        public List<String> getAffectedWorkflows() {
            return affectedWorkflows;
        }
        
        public void addAffectedWorkflow(String workflow) {
            this.affectedWorkflows.add(workflow);
        }
        
        public List<String> getDeletedFields() {
            return deletedFields;
        }
        
        public void addDeletedField(String field) {
            this.deletedFields.add(field);
        }
        
        public List<String> getAddedFields() {
            return addedFields;
        }
        
        public void addAddedField(String field) {
            this.addedFields.add(field);
        }
        
        public List<String> getModifiedFields() {
            return modifiedFields;
        }
        
        public void addModifiedField(String field) {
            this.modifiedFields.add(field);
        }
        
        /**
         * 判断是否有破坏性变更
         */
        public boolean hasBreakingChanges() {
            return impactLevel == ImpactLevel.CRITICAL || impactLevel == ImpactLevel.HIGH;
        }
        
        /**
         * 获取变更摘要
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("影响级别: ").append(impactLevel.name()).append("\n");
            sb.append("删除字段: ").append(deletedFields).append("\n");
            sb.append("修改字段: ").append(modifiedFields).append("\n");
            sb.append("添加字段: ").append(addedFields).append("\n");
            sb.append("影响实体: ").append(affectedEntities).append("\n");
            sb.append("影响工作流: ").append(affectedWorkflows);
            return sb.toString();
        }
    }
}