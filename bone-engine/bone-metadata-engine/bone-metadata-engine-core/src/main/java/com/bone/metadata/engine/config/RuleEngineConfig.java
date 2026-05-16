package com.bone.metadata.engine.config;

import com.bone.metadata.engine.BusinessRuleEngine;
import com.bone.metadata.engine.DefaultBusinessRuleEngine;
import com.bone.metadata.engine.ExpressionEngine;
import com.bone.metadata.engine.RuleContext;
import com.bone.metadata.engine.UnifiedRuleEngine;
import com.bone.metadata.engine.rule.BusinessRuleRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 规则引擎配置类 统一管理规则引擎的依赖注入配置 */
@Configuration
public class RuleEngineConfig {

  /** 主业务规则引擎Bean */
  @Bean
  public BusinessRuleEngine businessRuleEngine(
      BusinessRuleRegistry businessRuleRegistry, ExpressionEngine expressionEngine) {
    return new DefaultBusinessRuleEngine(businessRuleRegistry, expressionEngine);
  }

  /** 统一规则引擎适配器 将BusinessRuleEngine适配为UnifiedRuleEngine接口 */
  @Bean
  public UnifiedRuleEngine<Object> unifiedRuleEngine(BusinessRuleEngine businessRuleEngine) {
    return new UnifiedRuleEngineAdapter(businessRuleEngine);
  }

  /** 统一规则引擎适配器实现 */
  private static class UnifiedRuleEngineAdapter implements UnifiedRuleEngine<Object> {
    private final BusinessRuleEngine delegate;

    public UnifiedRuleEngineAdapter(BusinessRuleEngine delegate) {
      this.delegate = delegate;
    }

    @Override
    public com.bone.metadata.engine.validation.ValidationResult validate(
        Object entity, RuleContext context) {
      // 适配器实现，将Object转换为DynamicSmartEntity
      if (entity instanceof com.bone.metadata.engine.model.DynamicSmartEntity) {
        com.bone.metadata.engine.validation.ValidationResult result =
            com.bone.metadata.engine.validation.ValidationResult.success();
        delegate.executeValidationRules(
            (com.bone.metadata.engine.model.DynamicSmartEntity) entity, result);
        return result;
      }
      return com.bone.metadata.engine.validation.ValidationResult.failure("不支持的实体类型");
    }

    @Override
    public com.bone.metadata.engine.validation.ValidationResult executeRules(
        Object entity, String ruleType, RuleContext context) {
      // 根据规则类型执行相应的规则
      if ("highValueOrder".equals(ruleType)) {
        return delegate.evaluateHighValueOrderRule((java.util.Map<String, Object>) entity);
      } else if ("orderPriority".equals(ruleType)) {
        return delegate.evaluateOrderPriorityRule((java.util.Map<String, Object>) entity);
      }
      return com.bone.metadata.engine.validation.ValidationResult.failure("不支持的规则类型: " + ruleType);
    }

    @Override
    public java.util.List<com.bone.metadata.engine.model.BusinessRuleMetadata> getApplicableRules(
        Object entity, String eventType) {
      if (entity instanceof com.bone.metadata.engine.model.DynamicSmartEntity) {
        return delegate.getRulesForEvent(
            ((com.bone.metadata.engine.model.DynamicSmartEntity) entity).getEntityApiName(),
            eventType);
      }
      return java.util.Collections.emptyList();
    }

    @Override
    public boolean validateRule(com.bone.metadata.engine.model.BusinessRuleMetadata rule) {
      return delegate.validateRule(rule);
    }

    @Override
    public java.util.List<String> getRuleDependencies(
        com.bone.metadata.engine.model.BusinessRuleMetadata rule) {
      return delegate.getRuleDependencies(rule);
    }

    @Override
    public void executeValidationRules(
        com.bone.metadata.engine.model.DynamicSmartEntity entity,
        com.bone.metadata.engine.validation.ValidationResult result) {
      delegate.executeValidationRules(entity, result);
    }

    @Override
    public void executeActionRules(
        com.bone.metadata.engine.model.DynamicSmartEntity entity, String eventType) {
      delegate.executeActionRules(entity, eventType);
    }

    @Override
    public Object executeRule(
        com.bone.metadata.engine.model.DynamicSmartEntity entity,
        com.bone.metadata.engine.model.BusinessRuleMetadata rule,
        java.util.Map<String, Object> context) {
      return delegate.executeRule(entity, rule, context);
    }

    @Override
    public com.bone.metadata.engine.validation.ValidationResult evaluateHighValueOrderRule(
        java.util.Map<String, Object> orderData) {
      return delegate.evaluateHighValueOrderRule(orderData);
    }

    @Override
    public com.bone.metadata.engine.validation.ValidationResult evaluateOrderPriorityRule(
        java.util.Map<String, Object> orderData) {
      return delegate.evaluateOrderPriorityRule(orderData);
    }
  }
}
