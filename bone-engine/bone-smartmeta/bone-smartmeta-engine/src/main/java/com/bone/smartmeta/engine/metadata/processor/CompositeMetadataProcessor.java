package com.bone.smartmeta.engine.metadata.processor;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldLevelSecurityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.metadata.IndexMetadata;
import com.bone.smartmeta.engine.metadata.RecordTypeMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import com.bone.smartmeta.engine.metadata.AiMetadata;
import com.bone.smartmeta.engine.metadata.AgentMetadata;
import com.bone.smartmeta.engine.metadata.BusinessRuleMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 复合元数据处理器，整合多种来源的元数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeMetadataProcessor {

    private final List<MetadataProcessor> metadataProcessors;
    
    @Value("${bone.smartmeta.metadata.merge-strategy:yaml-overrides-annotation}")
    private String mergeStrategy;

    /**
     * 处理所有来源的元数据并合并
     */
    public List<EntityMetadata> processAllMetadata() {
        // 存储所有来源的元数据，按实体API名称分组
        Map<String, List<EntityMetadata>> entityMetadataGroups = new HashMap<>();
        
        // 处理YAML元数据
        processMetadataSource("yaml", "classpath*:com/bone/smartmeta/packages/**/objects/*.yaml", entityMetadataGroups);
        
        // 处理Groovy元数据
        processMetadataSource("groovy", "classpath*:com/bone/smartmeta/packages/**/groovy/*.groovy", entityMetadataGroups);
        
        // 处理注解元数据
        processMetadataSource("annotation", "", entityMetadataGroups);
        
        // 合并同一实体的不同来源元数据
        List<EntityMetadata> mergedMetadata = new ArrayList<>();
        for (Map.Entry<String, List<EntityMetadata>> entry : entityMetadataGroups.entrySet()) {
            String entityApiName = entry.getKey();
            List<EntityMetadata> metadataList = entry.getValue();
            
            EntityMetadata merged = mergeEntityMetadata(metadataList);
            mergedMetadata.add(merged);
            log.info("合并实体元数据: {}，来源数: {}", entityApiName, metadataList.size());
        }
        
        return mergedMetadata;
    }
    
    /**
     * 处理特定来源的元数据
     */
    private void processMetadataSource(String sourceType, String locationPattern, 
                                      Map<String, List<EntityMetadata>> entityMetadataGroups) {
        // 找到对应的处理器
        MetadataProcessor processor = metadataProcessors.stream()
                .filter(p -> p.getSourceType().equals(sourceType))
                .findFirst()
                .orElse(null);
        
        if (processor == null) {
            log.warn("未找到元数据处理器: {}", sourceType);
            return;
        }
        
        try {
            log.info("开始处理{}元数据，位置模式: {}", sourceType, locationPattern);
            Object result = processor.processMetadata(locationPattern);
            
            // 确保返回值是预期的List类型
            List<EntityMetadata> metadataList = new ArrayList<>();
            if (result instanceof List) {
                metadataList = (List<EntityMetadata>) result;
            }
            
            // 按实体API名称分组
            for (EntityMetadata metadata : metadataList) {
                String entityApiName = metadata.getApiName();
                entityMetadataGroups.computeIfAbsent(entityApiName, k -> new ArrayList<>()).add(metadata);
            }
            
            log.info("完成处理{}元数据，实体数: {}", sourceType, metadataList.size());
        } catch (Exception e) {
            log.error("处理{}元数据失败", sourceType, e);
        }
    }
    
    /**
     * 合并同一实体的多个元数据
     */
    private EntityMetadata mergeEntityMetadata(List<EntityMetadata> metadataList) {
        if (metadataList.isEmpty()) {
            return null;
        }
        
        // 根据合并策略排序元数据，优先级高的在后面
        List<EntityMetadata> sortedMetadata = sortMetadataByPriority(metadataList);
        
        // 以第一个元数据为基础进行合并
        EntityMetadata merged = copyEntityMetadata(sortedMetadata.get(0));
        
        // 合并其他元数据
        for (int i = 1; i < sortedMetadata.size(); i++) {
            EntityMetadata current = sortedMetadata.get(i);
            mergeInto(merged, current);
        }
        
        return merged;
    }
    
    /**
     * 根据合并策略对元数据排序
     */
    private List<EntityMetadata> sortMetadataByPriority(List<EntityMetadata> metadataList) {
        // 创建元数据副本以避免修改原始列表
        List<EntityMetadata> sorted = new ArrayList<>(metadataList);
        
        // 根据来源类型和合并策略排序
        sorted.sort((m1, m2) -> {
            String source1 = getSourceType(m1);
            String source2 = getSourceType(m2);
            
            // 根据合并策略确定优先级
            if ("yaml-overrides-annotation".equals(mergeStrategy)) {
                // YAML > Groovy > 注解
                List<String> priorityOrder = List.of("annotation", "groovy", "yaml");
                return Integer.compare(
                        priorityOrder.indexOf(source1), 
                        priorityOrder.indexOf(source2)
                );
            } else if ("groovy-overrides-yaml".equals(mergeStrategy)) {
                // Groovy > YAML > 注解
                List<String> priorityOrder = List.of("annotation", "yaml", "groovy");
                return Integer.compare(
                        priorityOrder.indexOf(source1), 
                        priorityOrder.indexOf(source2)
                );
            } else {
                // 默认策略：YAML > Groovy > 注解
                List<String> priorityOrder = List.of("annotation", "groovy", "yaml");
                return Integer.compare(
                        priorityOrder.indexOf(source1), 
                        priorityOrder.indexOf(source2)
                );
            }
        });
        
        return sorted;
    }
    
    /**
     * 获取元数据的来源类型
     */
    private String getSourceType(EntityMetadata metadata) {
        // 简单判断来源类型的逻辑
        Class<?> entityClass = metadata.getEntityClass();
        if (entityClass != null) {
            String className = entityClass.getName();
            if (className.contains(".groovy.")) {
                return "groovy";
            } else if (entityClass.isAnnotationPresent(com.bone.smartmeta.engine.annotation.SmartEntity.class)) {
                return "annotation";
            }
        }
        return "yaml";
    }
    
    /**
     * 复制实体元数据
     */
    private EntityMetadata copyEntityMetadata(EntityMetadata source) {
        EntityMetadata copy = new EntityMetadata();
        
        // 复制基本属性
        copy.setApiName(source.getApiName());
        copy.setLabel(source.getLabel());
        copy.setPluralLabel(source.getPluralLabel());
        copy.setTableName(source.getTableName());
        copy.setDomain(source.getDomain());
        copy.setDescription(source.getDescription());
        copy.setOwnershipModel(source.getOwnershipModel());
        copy.setEntityClass(source.getEntityClass());
        copy.setVersion(source.getVersion());
        copy.setPackageName(source.getPackageName());
        copy.setQueryCacheTtl(source.getQueryCacheTtl());
        copy.setCacheable(source.isCacheable());
        copy.setHistoryTrackingEnabled(source.isHistoryTrackingEnabled());
        copy.setTrackedFields(new ArrayList<>(source.getTrackedFields()));
        
        // 复制记录类型
        source.getRecordTypes().forEach(rt -> {
            copy.getRecordTypes().add(copyRecordTypeMetadata(rt));
        });
        
        // 复制字段
        source.getFields().forEach(field -> {
            copy.addField(copyFieldMetadata(field));
        });
        
        // 复制验证规则
        source.getValidationRules().forEach(rule -> {
            copy.getValidationRules().add(copyValidationRuleMetadata(rule));
        });
        
        // 复制字段级安全
        source.getFieldLevelSecurity().forEach(fls -> {
            copy.getFieldLevelSecurity().add(copyFieldLevelSecurityMetadata(fls));
        });
        
        // 复制索引
        source.getIndexes().forEach(index -> {
            copy.getIndexes().add(copyIndexMetadata(index));
        });
        
        // 复制AI配置
        copy.setAiMetadata(copyAiMetadata(source.getAiMetadata()));
        
        return copy;
    }
    
    /**
     * 将源元数据合并到目标元数据中
     */
    private void mergeInto(EntityMetadata target, EntityMetadata source) {
        log.debug("合并元数据: {} <- {}", target.getApiName(), getSourceType(source));
        
        // 合并基本属性（只覆盖非空值）
        if (source.getLabel() != null) target.setLabel(source.getLabel());
        if (source.getPluralLabel() != null) target.setPluralLabel(source.getPluralLabel());
        if (source.getTableName() != null) target.setTableName(source.getTableName());
        if (source.getDomain() != null) target.setDomain(source.getDomain());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
        if (source.getOwnershipModel() != null) target.setOwnershipModel(source.getOwnershipModel());
        if (source.getEntityClass() != null) target.setEntityClass(source.getEntityClass());
        if (source.getVersion() != null) target.setVersion(source.getVersion());
        if (source.getPackageName() != null) target.setPackageName(source.getPackageName());
        target.setQueryCacheTtl(source.getQueryCacheTtl());
        target.setCacheable(source.isCacheable());
        target.setHistoryTrackingEnabled(source.isHistoryTrackingEnabled());
        target.getTrackedFields().addAll(source.getTrackedFields());
        
        // 合并记录类型
        mergeRecordTypes(target, source);
        
        // 合并字段
        mergeFields(target, source);
        
        // 合并验证规则
        mergeValidationRules(target, source);
        
        // 合并字段级安全
        mergeFieldLevelSecurity(target, source);
        
        // 合并索引
        mergeIndexes(target, source);
        
        // 合并AI配置
        mergeAiMetadata(target, source);
    }
    
    /**
     * 合并记录类型
     */
    private void mergeRecordTypes(EntityMetadata target, EntityMetadata source) {
        for (RecordTypeMetadata sourceRt : source.getRecordTypes()) {
            // 查找是否已存在相同API名称的记录类型
            Optional<RecordTypeMetadata> existingRt = target.getRecordTypes().stream()
                    .filter(rt -> rt.getApiName().equals(sourceRt.getApiName()))
                    .findFirst();
            
            if (existingRt.isPresent()) {
                // 更新现有记录类型
                RecordTypeMetadata targetRt = existingRt.get();
                if (sourceRt.getLabel() != null) targetRt.setLabel(sourceRt.getLabel());
                targetRt.setDefault(sourceRt.isDefault());
            } else {
                // 添加新的记录类型
                target.getRecordTypes().add(copyRecordTypeMetadata(sourceRt));
            }
        }
    }
    
    /**
     * 合并字段
     */
    private void mergeFields(EntityMetadata target, EntityMetadata source) {
        for (FieldMetadata sourceField : source.getFields()) {
            // 查找是否已存在相同API名称的字段
            FieldMetadata targetField = target.getField(sourceField.getApiName());
            
            if (targetField != null) {
                // 更新现有字段
                mergeFieldMetadata(targetField, sourceField);
            } else {
                // 添加新的字段
                target.addField(copyFieldMetadata(sourceField));
            }
        }
    }
    
    /**
     * 合并两个字段元数据
     */
    private void mergeFieldMetadata(FieldMetadata target, FieldMetadata source) {
        if (source.getLabel() != null) target.setLabel(source.getLabel());
        if (source.getDescription() != null) target.setDescription(source.getDescription());
        if (source.getType() != null) target.setType(source.getType());
        target.setRequired(source.isRequired());
        target.setUnique(source.isUnique());
        target.setLength(source.getLength());
        target.setPrecision(source.getPrecision());
        target.setScale(source.getScale());
        if (source.getDefaultValue() != null) target.setDefaultValue(source.getDefaultValue());
        if (source.getPattern() != null) target.setPattern(source.getPattern());
        if (source.getReferenceTo() != null) target.setReferenceTo(source.getReferenceTo());
        target.setPicklistValues(new ArrayList<>(source.getPicklistValues()));
        target.setIndexed(source.isIndexed());
        target.setPrimaryKey(source.isPrimaryKey());
        target.setSystemField(source.isSystemField());
        if (source.getColumnName() != null) target.setColumnName(source.getColumnName());
        target.setEncrypted(source.isEncrypted());
        if (source.getEncryptionAlgorithm() != null) target.setEncryptionAlgorithm(source.getEncryptionAlgorithm());
        target.setSearchable(source.isSearchable());
        target.setSortable(source.isSortable());
        if (source.getFormulaExpression() != null) target.setFormulaExpression(source.getFormulaExpression());
        if (source.getFormulaReturnType() != null) target.setFormulaReturnType(source.getFormulaReturnType());
        if (source.getRecalculation() != null) target.setRecalculation(source.getRecalculation());
        
        // 合并业务规则
        for (BusinessRuleMetadata sourceRule : source.getBusinessRules()) {
            boolean found = false;
            for (BusinessRuleMetadata targetRule : target.getBusinessRules()) {
                if (targetRule.getName().equals(sourceRule.getName())) {
                    // 更新现有规则
                    targetRule.setExpression(sourceRule.getExpression());
                    targetRule.setErrorMessage(sourceRule.getErrorMessage());
                    targetRule.setSeverity(sourceRule.getSeverity());
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 添加新规则
                target.getBusinessRules().add(copyBusinessRuleMetadata(sourceRule));
            }
        }
    }
    
    // 复制元数据辅助方法
    private RecordTypeMetadata copyRecordTypeMetadata(RecordTypeMetadata source) {
        RecordTypeMetadata copy = new RecordTypeMetadata();
        copy.setApiName(source.getApiName());
        copy.setLabel(source.getLabel());
        copy.setDefault(source.isDefault());
        return copy;
    }
    
    private FieldMetadata copyFieldMetadata(FieldMetadata source) {
        FieldMetadata copy = new FieldMetadata();
        copy.setApiName(source.getApiName());
        copy.setLabel(source.getLabel());
        copy.setType(source.getType());
        copy.setDescription(source.getDescription());
        copy.setRequired(source.isRequired());
        copy.setUnique(source.isUnique());
        copy.setLength(source.getLength());
        copy.setPrecision(source.getPrecision());
        copy.setScale(source.getScale());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setPattern(source.getPattern());
        copy.setReferenceTo(source.getReferenceTo());
        copy.setPicklistValues(new ArrayList<>(source.getPicklistValues()));
        copy.setIndexed(source.isIndexed());
        copy.setPrimaryKey(source.isPrimaryKey());
        copy.setSystemField(source.isSystemField());
        copy.setFieldName(source.getFieldName());
        copy.setColumnName(source.getColumnName());
        copy.setEncrypted(source.isEncrypted());
        copy.setEncryptionAlgorithm(source.getEncryptionAlgorithm());
        copy.setSearchable(source.isSearchable());
        copy.setSortable(source.isSortable());
        copy.setFormulaExpression(source.getFormulaExpression());
        copy.setFormulaReturnType(source.getFormulaReturnType());
        copy.setRecalculation(source.getRecalculation());
        
        source.getBusinessRules().forEach(rule -> {
            copy.getBusinessRules().add(copyBusinessRuleMetadata(rule));
        });
        
        return copy;
    }
    
    private BusinessRuleMetadata copyBusinessRuleMetadata(BusinessRuleMetadata source) {
        BusinessRuleMetadata copy = new BusinessRuleMetadata();
        copy.setName(source.getName());
        copy.setExpression(source.getExpression());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setSeverity(source.getSeverity());
        copy.setFieldName(source.getFieldName());
        return copy;
    }
    
    private ValidationRuleMetadata copyValidationRuleMetadata(ValidationRuleMetadata source) {
        ValidationRuleMetadata copy = new ValidationRuleMetadata();
        copy.setName(source.getName());
        copy.setExpression(source.getExpression());
        copy.setErrorMessage(source.getErrorMessage());
        return copy;
    }
    
    private FieldLevelSecurityMetadata copyFieldLevelSecurityMetadata(FieldLevelSecurityMetadata source) {
        FieldLevelSecurityMetadata copy = new FieldLevelSecurityMetadata();
        copy.setProfile(source.getProfile());
        copy.setReadableFields(new ArrayList<>(source.getReadableFields()));
        copy.setEditableFields(new ArrayList<>(source.getEditableFields()));
        return copy;
    }
    
    private IndexMetadata copyIndexMetadata(IndexMetadata source) {
        IndexMetadata copy = new IndexMetadata();
        copy.setName(source.getName());
        copy.setFields(new ArrayList<>(source.getFields()));
        copy.setUnique(source.isUnique());
        return copy;
    }
    
    private AiMetadata copyAiMetadata(AiMetadata source) {
        AiMetadata copy = new AiMetadata();
        copy.setSuggestions(new ArrayList<>(source.getSuggestions()));
        
        source.getAgenticAI().forEach(agent -> {
            copy.getAgenticAI().add(copyAgentMetadata(agent));
        });
        
        return copy;
    }
    
    private AgentMetadata copyAgentMetadata(AgentMetadata source) {
        AgentMetadata copy = new AgentMetadata();
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setTrigger(source.getTrigger());
        copy.setAction(source.getAction());
        return copy;
    }
    
    /**
     * 合并验证规则
     */
    private void mergeValidationRules(EntityMetadata target, EntityMetadata source) {
        for (ValidationRuleMetadata sourceRule : source.getValidationRules()) {
            // 查找是否已存在相同名称的验证规则
            Optional<ValidationRuleMetadata> existingRule = target.getValidationRules().stream()
                    .filter(rule -> rule.getName().equals(sourceRule.getName()))
                    .findFirst();
            
            if (existingRule.isPresent()) {
                // 更新现有规则
                ValidationRuleMetadata targetRule = existingRule.get();
                targetRule.setExpression(sourceRule.getExpression());
                targetRule.setErrorMessage(sourceRule.getErrorMessage());
            } else {
                // 添加新的规则
                target.getValidationRules().add(copyValidationRuleMetadata(sourceRule));
            }
        }
    }
    
    /**
     * 合并字段级安全
     */
    private void mergeFieldLevelSecurity(EntityMetadata target, EntityMetadata source) {
        for (FieldLevelSecurityMetadata sourceFls : source.getFieldLevelSecurity()) {
            // 查找是否已存在相同配置文件的安全设置
            Optional<FieldLevelSecurityMetadata> existingFls = target.getFieldLevelSecurity().stream()
                    .filter(fls -> fls.getProfile().equals(sourceFls.getProfile()))
                    .findFirst();
            
            if (existingFls.isPresent()) {
                // 更新现有安全设置
                FieldLevelSecurityMetadata targetFls = existingFls.get();
                targetFls.getReadableFields().addAll(sourceFls.getReadableFields());
                targetFls.getEditableFields().addAll(sourceFls.getEditableFields());
                
                // 去重
                targetFls.setReadableFields(targetFls.getReadableFields().stream()
                        .distinct()
                        .collect(Collectors.toList()));
                
                targetFls.setEditableFields(targetFls.getEditableFields().stream()
                        .distinct()
                        .collect(Collectors.toList()));
            } else {
                // 添加新的安全设置
                target.getFieldLevelSecurity().add(copyFieldLevelSecurityMetadata(sourceFls));
            }
        }
    }
    
    /**
     * 合并索引
     */
    private void mergeIndexes(EntityMetadata target, EntityMetadata source) {
        for (IndexMetadata sourceIndex : source.getIndexes()) {
            // 查找是否已存在相同名称的索引
            Optional<IndexMetadata> existingIndex = target.getIndexes().stream()
                    .filter(index -> index.getName().equals(sourceIndex.getName()))
                    .findFirst();
            
            if (existingIndex.isPresent()) {
                // 更新现有索引
                IndexMetadata targetIndex = existingIndex.get();
                targetIndex.setFields(new ArrayList<>(sourceIndex.getFields()));
                targetIndex.setUnique(sourceIndex.isUnique());
            } else {
                // 添加新的索引
                target.getIndexes().add(copyIndexMetadata(sourceIndex));
            }
        }
    }
    
    /**
     * 合并AI配置
     */
    private void mergeAiMetadata(EntityMetadata target, EntityMetadata source) {
        AiMetadata targetAi = target.getAiMetadata();
        AiMetadata sourceAi = source.getAiMetadata();
        
        // 合并建议
        targetAi.getSuggestions().addAll(sourceAi.getSuggestions());
        targetAi.setSuggestions(targetAi.getSuggestions().stream()
                .distinct()
                .collect(Collectors.toList()));
        
        // 合并AI代理
        for (AgentMetadata sourceAgent : sourceAi.getAgenticAI()) {
            boolean found = false;
            for (AgentMetadata targetAgent : targetAi.getAgenticAI()) {
                if (targetAgent.getName().equals(sourceAgent.getName())) {
                    // 更新现有代理
                    targetAgent.setType(sourceAgent.getType());
                    targetAgent.setTrigger(sourceAgent.getTrigger());
                    targetAgent.setAction(sourceAgent.getAction());
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 添加新代理
                targetAi.getAgenticAI().add(copyAgentMetadata(sourceAgent));
            }
        }
    }
}
