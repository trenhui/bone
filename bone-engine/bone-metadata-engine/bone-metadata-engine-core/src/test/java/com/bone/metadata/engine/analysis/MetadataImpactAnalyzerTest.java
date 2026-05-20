package com.bone.metadata.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.model.BusinessRuleMetadata;
import com.bone.metadata.engine.model.EntityMetadata;
import com.bone.metadata.engine.model.FieldMetadata;
import com.bone.metadata.engine.model.RelationshipMetadata;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetadataImpactAnalyzerTest {

  private MetadataImpactAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    EntityMetadata order = entity("Order", rel("orderItems", "OrderItem"));
    EntityMetadata orderItem = entity("OrderItem", rel("parentOrder", "Order"));

    MetadataImpactDataAccess dataAccess =
        new MetadataImpactDataAccess() {
          @Override
          public List<EntityMetadata> findAllEntities() {
            return List.of(order, orderItem);
          }

          @Override
          public List<WorkflowRef> findWorkflowsForEntity(String entityApiName) {
            if ("Order".equals(entityApiName)) {
              return List.of(new WorkflowRef("orderApproval", "Order"));
            }
            return List.of();
          }
        };
    analyzer = new MetadataImpactAnalyzer(dataAccess);
  }

  @Test
  void deletingPrimaryKeyIsCritical() {
    EntityMetadata oldMeta = entity("Account", field("id", true, false));
    EntityMetadata newMeta = entity("Account");

    MetadataImpactAnalyzer.ImpactAnalysisResult result =
        analyzer.analyzeEntityImpact("0", oldMeta, newMeta);

    assertEquals(MetadataImpactAnalyzer.ImpactLevel.CRITICAL, result.getImpactLevel());
    assertTrue(result.getCriticalImpacts().stream().anyMatch(s -> s.contains("id")));
  }

  @Test
  void detectsDependentEntityAndWorkflow() {
    EntityMetadata oldMeta = entity("Order", field("code", false, true));
    EntityMetadata newMeta = entity("Order", field("code", false, true));

    MetadataImpactAnalyzer.ImpactAnalysisResult result =
        analyzer.analyzeEntityImpact("0", oldMeta, newMeta);

    assertTrue(result.getAffectedEntities().contains("OrderItem"));
    assertTrue(result.getAffectedWorkflows().contains("orderApproval"));
  }

  @Test
  void businessRuleDependingOnDeletedFieldIsHighImpact() {
    BusinessRuleMetadata rule =
        BusinessRuleMetadata.builder().apiName("validateAmount").build();
    rule.addDependentField("amount");

    EntityMetadata oldMeta = entity("Invoice", field("amount", false, true));
    oldMeta.getBusinessRules().add(rule);
    EntityMetadata newMeta = entity("Invoice");

    MetadataImpactAnalyzer.ImpactAnalysisResult result =
        analyzer.analyzeEntityImpact("0", oldMeta, newMeta);

    assertEquals(MetadataImpactAnalyzer.ImpactLevel.HIGH, result.getImpactLevel());
    assertTrue(
        result.getHighImpacts().stream().anyMatch(s -> s.contains("validateAmount")));
  }

  private static EntityMetadata entity(String apiName, Object... parts) {
    EntityMetadata entity = new EntityMetadata();
    entity.setApiName(apiName);
    Map<String, FieldMetadata> fields = new HashMap<>();
    List<RelationshipMetadata> relationships = new java.util.ArrayList<>();
    for (Object part : parts) {
      if (part instanceof FieldMetadata f) {
        fields.put(f.getName(), f);
      } else if (part instanceof RelationshipMetadata r) {
        relationships.add(r);
      }
    }
    entity.setFields(fields);
    entity.setRelationships(relationships);
    return entity;
  }

  private static FieldMetadata field(String name, boolean primaryKey, boolean required) {
    FieldMetadata field = new FieldMetadata();
    field.setName(name);
    field.setApiName(name);
    field.setPrimaryKey(primaryKey);
    field.setRequired(required);
    return field;
  }

  private static RelationshipMetadata rel(String apiName, String targetEntity) {
    RelationshipMetadata rel = new RelationshipMetadata();
    rel.setApiName(apiName);
    rel.setTargetEntity(targetEntity);
    return rel;
  }
}
