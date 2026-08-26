package com.bone.metadata.engine.runtime.analysis;

import com.bone.metadata.engine.domain.model.BusinessRuleMetadata;
import com.bone.metadata.engine.domain.model.EntityMetadata;
import com.bone.metadata.engine.domain.model.FieldMetadata;
import com.bone.metadata.engine.domain.model.RelationshipMetadata;
import com.bone.metadata.engine.runtime.repository.MetadataRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 元数据影响分析器：字段、关系、依赖实体、工作流与业务规则。 */
public class MetadataImpactAnalyzer {

  private final MetadataImpactDataAccess dataAccess;

  public MetadataImpactAnalyzer(MetadataImpactDataAccess dataAccess) {
    this.dataAccess = dataAccess != null ? dataAccess : MetadataImpactDataAccess.empty();
  }

  public MetadataImpactAnalyzer(
      MetadataRepository metadataRepository, List<EntityMetadata> entityCatalog) {
    this(new RepositoryMetadataImpactDataAccess(metadataRepository, entityCatalog));
  }

  /** 分析实体元数据变更的影响 */
  public ImpactAnalysisResult analyzeEntityImpact(
      String tenantId, EntityMetadata oldMetadata, EntityMetadata newMetadata) {
    Objects.requireNonNull(oldMetadata, "oldMetadata");
    Objects.requireNonNull(newMetadata, "newMetadata");
    ImpactAnalysisResult result = new ImpactAnalysisResult();

    analyzeFieldChanges(oldMetadata, newMetadata, result);
    analyzeRelationChanges(oldMetadata, newMetadata, result);

    String entityApiName = oldMetadata.getApiName();
    if (entityApiName != null && !entityApiName.isBlank()) {
      analyzeDependentEntities(entityApiName, result);
      analyzeRelatedWorkflows(entityApiName, result);
    }

    analyzeBusinessRulesImpact(oldMetadata, newMetadata, result);
    evaluateImpactLevel(result);
    return result;
  }

  private void analyzeFieldChanges(
      EntityMetadata oldMetadata, EntityMetadata newMetadata, ImpactAnalysisResult result) {
    Map<String, FieldMetadata> oldFieldsMap = fieldsByName(oldMetadata);
    Map<String, FieldMetadata> newFieldsMap = fieldsByName(newMetadata);

    for (FieldMetadata oldField : oldFieldsMap.values()) {
      if (!newFieldsMap.containsKey(oldField.getName())) {
        result.addDeletedField(oldField.getName());
        if (oldField.isPrimaryKey()) {
          result.addCriticalImpact("删除主键字段: " + oldField.getName());
        } else if (oldField.isRequired()) {
          result.addHighImpact("删除必填字段: " + oldField.getName());
        } else {
          result.addMediumImpact("删除字段: " + oldField.getName());
        }
      }
    }

    for (FieldMetadata newField : newFieldsMap.values()) {
      FieldMetadata oldField = oldFieldsMap.get(newField.getName());
      if (oldField == null) {
        result.addAddedField(newField.getName());
        result.addLowImpact("新增字段: " + newField.getName());
        continue;
      }
      result.addModifiedField(newField.getName());
      if (!Objects.equals(oldField.getDataType(), newField.getDataType())) {
        result.addHighImpact("字段类型变更: " + newField.getName());
      }
      if (!oldField.isRequired() && newField.isRequired()) {
        result.addMediumImpact("字段变为必填: " + newField.getName());
      }
      if (oldField.isRequired() && !newField.isRequired()) {
        result.addLowImpact("字段取消必填: " + newField.getName());
      }
    }
  }

  private void analyzeRelationChanges(
      EntityMetadata oldMetadata, EntityMetadata newMetadata, ImpactAnalysisResult result) {
    Map<String, RelationshipMetadata> oldRels = relationsByApiName(oldMetadata);
    Map<String, RelationshipMetadata> newRels = relationsByApiName(newMetadata);

    for (RelationshipMetadata removed : oldRels.values()) {
      if (!newRels.containsKey(removed.getApiName())) {
        result.addHighImpact("删除关系: " + removed.getApiName());
      }
    }
    for (RelationshipMetadata added : newRels.values()) {
      if (!oldRels.containsKey(added.getApiName())) {
        result.addMediumImpact("新增关系: " + added.getApiName());
      }
    }
    for (RelationshipMetadata newRel : newRels.values()) {
      RelationshipMetadata oldRel = oldRels.get(newRel.getApiName());
      if (oldRel == null) {
        continue;
      }
      if (!Objects.equals(oldRel.getTargetEntity(), newRel.getTargetEntity())
          || !Objects.equals(oldRel.getType(), newRel.getType())) {
        result.addHighImpact("关系定义变更: " + newRel.getApiName());
      }
    }
  }

  private void analyzeDependentEntities(String entityApiName, ImpactAnalysisResult result) {
    for (EntityMetadata entity : dataAccess.findAllEntities()) {
      if (entity == null || entityApiName.equals(entity.getApiName())) {
        continue;
      }
      for (RelationshipMetadata rel : safeRelationships(entity)) {
        if (entityApiName.equals(rel.getTargetEntity())) {
          result.addAffectedEntity(entity.getApiName());
          result.addMediumImpact(
              "实体 " + entity.getApiName() + " 的关系 " + rel.getApiName() + " 引用本实体");
        }
      }
    }
  }

  private void analyzeRelatedWorkflows(String entityApiName, ImpactAnalysisResult result) {
    for (WorkflowRef workflow : dataAccess.findWorkflowsForEntity(entityApiName)) {
      result.addAffectedWorkflow(workflow.name());
      result.addMediumImpact("关联工作流: " + workflow.name());
    }
  }

  private void analyzeBusinessRulesImpact(
      EntityMetadata oldMetadata, EntityMetadata newMetadata, ImpactAnalysisResult result) {
    Set<String> oldRuleNames =
        safeBusinessRules(oldMetadata).stream()
            .map(BusinessRuleMetadata::getApiName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Set<String> newRuleNames =
        safeBusinessRules(newMetadata).stream()
            .map(BusinessRuleMetadata::getApiName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    for (String removed : oldRuleNames) {
      if (!newRuleNames.contains(removed)) {
        result.addHighImpact("删除业务规则: " + removed);
      }
    }
    for (String added : newRuleNames) {
      if (!oldRuleNames.contains(added)) {
        result.addLowImpact("新增业务规则: " + added);
      }
    }

    Set<String> removedFields = new HashSet<>(result.getDeletedFields());
    Set<String> modifiedFields = new HashSet<>(result.getModifiedFields());
    for (BusinessRuleMetadata rule : safeBusinessRules(oldMetadata)) {
      if (rule.getDependentFields() == null) {
        continue;
      }
      for (String field : rule.getDependentFields()) {
        if (removedFields.contains(field)) {
          result.addHighImpact("业务规则 " + rule.getApiName() + " 依赖已删除字段: " + field);
        } else if (modifiedFields.contains(field)) {
          result.addMediumImpact("业务规则 " + rule.getApiName() + " 依赖已修改字段: " + field);
        }
      }
    }
  }

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

  private static Map<String, FieldMetadata> fieldsByName(EntityMetadata metadata) {
    if (metadata.getFields() == null) {
      return Map.of();
    }
    return metadata.getFields().values().stream()
        .filter(f -> f.getName() != null)
        .collect(Collectors.toMap(FieldMetadata::getName, f -> f, (a, b) -> a, HashMap::new));
  }

  private static Map<String, RelationshipMetadata> relationsByApiName(EntityMetadata metadata) {
    Map<String, RelationshipMetadata> map = new HashMap<>();
    for (RelationshipMetadata rel : safeRelationships(metadata)) {
      if (rel.getApiName() != null) {
        map.put(rel.getApiName(), rel);
      }
    }
    return map;
  }

  private static List<RelationshipMetadata> safeRelationships(EntityMetadata metadata) {
    List<RelationshipMetadata> rels = metadata.getRelationships();
    return rels != null ? rels : List.of();
  }

  private static List<BusinessRuleMetadata> safeBusinessRules(EntityMetadata metadata) {
    List<BusinessRuleMetadata> rules = metadata.getBusinessRules();
    return rules != null ? rules : List.of();
  }

  /** 影响级别枚举 */
  public enum ImpactLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  /** 影响分析结果 */
  public static class ImpactAnalysisResult {
    private ImpactLevel impactLevel = ImpactLevel.LOW;
    private List<String> criticalImpacts = new java.util.ArrayList<>();
    private List<String> highImpacts = new java.util.ArrayList<>();
    private List<String> mediumImpacts = new java.util.ArrayList<>();
    private List<String> lowImpacts = new java.util.ArrayList<>();
    private List<String> affectedEntities = new java.util.ArrayList<>();
    private List<String> affectedWorkflows = new java.util.ArrayList<>();
    private List<String> deletedFields = new java.util.ArrayList<>();
    private List<String> addedFields = new java.util.ArrayList<>();
    private List<String> modifiedFields = new java.util.ArrayList<>();

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

    public boolean hasBreakingChanges() {
      return impactLevel == ImpactLevel.CRITICAL || impactLevel == ImpactLevel.HIGH;
    }

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
