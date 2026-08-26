package com.bone.metadata.engine.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.engine.domain.metadata.BusinessRuleMetadata;
import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.SmartFieldMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P0.1 统一建模体系（model.* 合并到 metadata.*）后，验证 {@code metadata.*} 兼容方法能满足 legacy 引擎的字段访问契约。
 *
 * <p>覆盖 design-doc §1.1 FieldMetadata→SmartFieldMetadata 映射表：getApiName / isPrimaryKey / getName /
 * getDataType / getDependentFields 等，以及 EntityMetadata.getField 别名与 BusinessRuleMetadata 增强字段。
 */
class UnifiedMetadataModelTest {

  @Test
  void smartFieldMetadata_shouldMapLegacyFieldContract() {
    SmartFieldMetadata field = new SmartFieldMetadata();
    field.setApiName("orderNo");
    field.setFieldName("orderNo");
    field.setLabel("订单号");
    field.setType("string");
    field.setPrimaryKey(true);
    field.setRequired(true);
    field.setDescription("订单号字段");
    field.setCalculationDependencies(List.of("id"));

    // FieldMetadata 契约：getApiName/isPrimaryKey
    assertThat(field.getApiName()).isEqualTo("orderNo");
    assertThat(field.isPrimaryKey()).isTrue();
    assertThat(field.isRequired()).isTrue();

    // 兼容别名：getName -> fieldName / apiName；getDataType -> type
    assertThat(field.getName()).isEqualTo("orderNo");
    assertThat(field.getDataType()).isEqualTo("string");

    // 兼容别名：getDependentFields -> calculationDependencies
    assertThat(field.getDependentFields()).containsExactly("id");
  }

  @Test
  void entityMetadata_shouldMapLegacyFieldContract() {
    EntityMetadata entity = new EntityMetadata();
    entity.setApiName("order");
    entity.setName("订单");
    entity.setDomain("crm");
    entity.setEntityType("standard");

    SmartFieldMetadata field = new SmartFieldMetadata();
    field.setApiName("orderNo");
    field.setFieldName("orderNo");
    entity.setFields(List.of(field));

    // 兼容别名：getField -> getFieldByName
    assertThat(entity.getField("orderNo")).isNotNull();
    assertThat(entity.getField("orderNo").getApiName()).isEqualTo("orderNo");
    assertThat(entity.getEntityType()).isEqualTo("standard");

    // 兼容别名：getRelationships / getBusinessRules 返回空集合而非 null
    assertThat(entity.getRelationships()).isEmpty();
    assertThat(entity.getBusinessRules()).isEmpty();
  }

  @Test
  void businessRuleMetadata_shouldMapLegacyContract() {
    BusinessRuleMetadata rule = new BusinessRuleMetadata();
    rule.setId("r1");
    rule.setApiName("rule-1");
    rule.setName("校验订单金额");
    rule.setDomain("crm");
    rule.setRuleType("VALIDATION");
    rule.setActive(true);
    rule.setFieldName("amount");
    rule.addTriggerEvent("BEFORE_INSERT");

    // legacy 契约：getId/getApiName/getName/getDomain/getRuleType/isActive
    assertThat(rule.getId()).isEqualTo("r1");
    assertThat(rule.getApiName()).isEqualTo("rule-1");
    assertThat(rule.getName()).isEqualTo("校验订单金额");
    assertThat(rule.getDomain()).isEqualTo("crm");
    assertThat(rule.getRuleType()).isEqualTo("VALIDATION");
    assertThat(rule.isActive()).isTrue();

    // 兼容别名：getDependentFields / triggersEvent
    assertThat(rule.getDependentFields()).containsExactly("amount");
    assertThat(rule.triggersEvent("BEFORE_INSERT")).isTrue();
  }
}
