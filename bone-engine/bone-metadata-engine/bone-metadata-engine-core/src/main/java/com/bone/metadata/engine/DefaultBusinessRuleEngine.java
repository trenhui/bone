package com.bone.metadata.engine;

import com.bone.metadata.engine.model.BusinessRuleMetadata;
import com.bone.metadata.engine.model.DynamicSmartEntity;
import com.bone.metadata.engine.rule.BusinessRuleRegistry;
import com.bone.metadata.engine.validation.ValidationResult;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 默认业务规则引擎实现 实现通用业务规则引擎接口，提供规则验证和执行功能 */
@Component
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);

  // 注入依赖
  private final BusinessRuleRegistry businessRuleRegistry;
  private final ExpressionEngine expressionEngine;

  // 规则缓存，提高性能
  private final Map<String, BusinessRuleMetadata> ruleCache = new ConcurrentHashMap<>();

  @Autowired
  public DefaultBusinessRuleEngine(
      BusinessRuleRegistry businessRuleRegistry, ExpressionEngine expressionEngine) {
    this.businessRuleRegistry = businessRuleRegistry;
    this.expressionEngine = expressionEngine;
    LOGGER.info("BusinessRuleEngine initialized successfully");
  }

  @Override
  public void executeValidationRules(DynamicSmartEntity entity, ValidationResult result) {
    try {
      String entityApiName = entity.getEntityApiName();
      // 获取适用于该实体的验证规则
      List<BusinessRuleMetadata> rules = businessRuleRegistry.getRulesByEntity(entityApiName);

      for (BusinessRuleMetadata rule : rules) {
        if (rule.isActive()) { // 移除对getType()方法的依赖

          // 构建上下文
          Map<String, Object> context = entityToMap(entity);

          // 评估规则条件
          if (evaluateCondition(rule.getCondition(), context)) {
            // 执行验证逻辑
            try {
              Object validationResult = expressionEngine.eval(rule.getExpression(), context);
              if (validationResult instanceof Boolean && !(Boolean) validationResult) {
                result.addError(
                    rule.getName(),
                    rule.getErrorMessage() != null
                        ? rule.getErrorMessage()
                        : "Validation rule failed: " + rule.getName());
              }
            } catch (Exception e) {
              LOGGER.error("Failed to execute validation rule: {}", rule.getName(), e);
              result.addError(rule.getName(), "Error executing rule: " + e.getMessage());
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to execute validation rules", e);
      result.addError("RuleExecutionError", "Error executing validation rules: " + e.getMessage());
    }
  }

  @Override
  public void executeActionRules(DynamicSmartEntity entity, String eventType) {
    try {
      String entityApiName = entity.getEntityApiName();
      // 获取适用于该实体和事件的规则
      List<BusinessRuleMetadata> rules = businessRuleRegistry.getRulesByEntity(entityApiName);

      for (BusinessRuleMetadata rule : rules) {
        if (rule.isActive()) { // 移除对getType()方法的依赖
          // 构建上下文
          Map<String, Object> context = entityToMap(entity);
          context.put("eventType", eventType);

          // 评估规则条件
          if (evaluateCondition(rule.getCondition(), context)) {
            // 执行动作
            try {
              expressionEngine.eval(rule.getExpression(), context);
            } catch (Exception e) {
              LOGGER.error("Failed to execute action rule: {}", rule.getName(), e);
              // 动作规则失败不影响主流程，仅记录日志
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to execute action rules for event: {}", eventType, e);
    }
  }

  @Override
  public Object executeRule(
      DynamicSmartEntity entity, BusinessRuleMetadata rule, Map<String, Object> context) {
    if (rule == null || !rule.isActive()) {
      return null;
    }

    try {
      // 确保上下文中包含实体数据
      Map<String, Object> executionContext = new HashMap<>();
      if (context != null) {
        executionContext.putAll(context);
      }
      executionContext.putAll(entityToMap(entity));

      // 评估规则条件
      if (evaluateCondition(rule.getCondition(), executionContext)) {
        // 执行规则表达式
        return expressionEngine.eval(rule.getExpression(), executionContext);
      }
      return null;
    } catch (Exception e) {
      LOGGER.error("Failed to execute rule: {}", rule.getName(), e);
      return null;
    }
  }

  @Override
  public boolean validateRule(BusinessRuleMetadata rule) {
    if (rule == null) {
      return false;
    }

    try {
      // 验证规则表达式语法
      Map<String, Object> testContext = new HashMap<>();
      testContext.put("test", "value");

      // 尝试编译表达式但不执行
      if (rule.getExpression() != null && !rule.getExpression().trim().isEmpty()) {
        try {
          expressionEngine.eval(rule.getExpression(), testContext);
        } catch (Exception e) {
          LOGGER.error("Rule expression validation failed: {}", rule.getName(), e);
          return false;
        }
      }

      // 验证条件表达式语法
      if (rule.getCondition() != null && !rule.getCondition().trim().isEmpty()) {
        try {
          expressionEngine.eval(rule.getCondition(), testContext);
        } catch (Exception e) {
          LOGGER.error("Rule condition validation failed: {}", rule.getName(), e);
          return false;
        }
      }

      return true;
    } catch (Exception e) {
      LOGGER.error("Failed to validate rule: {}", rule.getName(), e);
      return false;
    }
  }

  @Override
  public List<String> getRuleDependencies(BusinessRuleMetadata rule) {
    if (rule == null) {
      return Collections.emptyList();
    }

    List<String> dependencies = new ArrayList<>();

    // 分析表达式中的字段引用
    analyzeExpressionDependencies(rule.getExpression(), dependencies);
    analyzeExpressionDependencies(rule.getCondition(), dependencies);

    return dependencies;
  }

  @Override
  public List<BusinessRuleMetadata> getRulesForEvent(String entityApiName, String eventType) {
    try {
      // 从业务规则注册表获取事件规则
      return businessRuleRegistry.getRulesByEntity(entityApiName);
    } catch (Exception e) {
      LOGGER.error("Failed to get rules for event: {} on entity: {}", eventType, entityApiName, e);
      return Collections.emptyList();
    }
  }

  @Override
  public ValidationResult evaluateHighValueOrderRule(Map<String, Object> orderData) {
    ValidationResult result = ValidationResult.success();

    try {
      // 假设订单金额阈值为10000
      Number amount = (Number) orderData.get("totalAmount");
      if (amount != null && amount.doubleValue() > 10000) {
        // 高价值订单需要额外验证
        if (!orderData.containsKey("approvalStatus")
            || !"APPROVED".equals(orderData.get("approvalStatus"))) {
          result.addError("HighValueOrder", "High value orders require approval");
        }

        // 检查是否有风险标记
        if (Boolean.TRUE.equals(orderData.get("hasRiskFlag"))) {
          result.addWarning("RiskyOrder", "Order has risk flag, please review");
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to evaluate high value order rule", e);
      result.addError("RuleEvaluationError", "Error evaluating high value order rule");
    }

    return result;
  }

  @Override
  public ValidationResult evaluateOrderPriorityRule(Map<String, Object> orderData) {
    ValidationResult result = ValidationResult.success();

    try {
      // 基于多个因素评估订单优先级
      Integer priority = (Integer) orderData.get("priority");
      String customerTier = (String) orderData.get("customerTier");
      String orderType = (String) orderData.get("orderType");

      if (priority != null) {
        // 检查优先级是否合理
        if (priority < 1 || priority > 5) {
          result.addError("InvalidPriority", "Priority must be between 1 and 5");
        }

        // 高优先级客户应该有更高的订单优先级
        if ("VIP".equals(customerTier) && priority > 3) {
          result.addWarning("PriorityMismatch", "VIP customer orders should have higher priority");
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to evaluate order priority rule", e);
      result.addError("RuleEvaluationError", "Error evaluating order priority rule");
    }

    return result;
  }

  @Override
  public ValidationResult executeRules(Object entity, String ruleType) {
    ValidationResult result = ValidationResult.success();

    try {
      // 这里简化实现，实际应根据实体类型和规则类型获取相应规则
      LOGGER.debug(
          "Executing rules of type: {} for entity type: {}", ruleType, entity.getClass().getName());

      // 可以扩展为支持不同类型的实体
      if (entity instanceof DynamicSmartEntity) {
        if ("VALIDATION".equalsIgnoreCase(ruleType)) {
          executeValidationRules((DynamicSmartEntity) entity, result);
        }
      } else if (entity instanceof Map) {
        // 对于Map类型实体的特殊处理
        @SuppressWarnings("unchecked")
        Map<String, Object> entityData = (Map<String, Object>) entity;

        // 根据规则类型执行不同的规则
        if ("HIGH_VALUE_ORDER".equalsIgnoreCase(ruleType)) {
          ValidationResult orderResult = evaluateHighValueOrderRule(entityData);
          result.merge(orderResult);
        } else if ("ORDER_PRIORITY".equalsIgnoreCase(ruleType)) {
          ValidationResult priorityResult = evaluateOrderPriorityRule(entityData);
          result.merge(priorityResult);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to execute rules of type: {}", ruleType, e);
      result.addError("RuleExecutionError", "Error executing rules: " + e.getMessage());
    }

    return result;
  }

  /** 将DynamicSmartEntity转换为Map */
  private Map<String, Object> entityToMap(DynamicSmartEntity entity) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", entity.getId());
    map.put("apiName", entity.getEntityApiName());
    map.putAll(entity.getAllFields());
    return map;
  }

  /** 评估规则条件 */
  private boolean evaluateCondition(String condition, Map<String, Object> context) {
    if (condition == null || condition.trim().isEmpty()) {
      return true; // 空条件默认为真
    }

    try {
      // 使用表达式引擎评估条件
      Object result = expressionEngine.eval(condition, context);
      if (result instanceof Boolean) {
        return (Boolean) result;
      }
      // 如果结果不是布尔值，尝试转换
      return Boolean.parseBoolean(result.toString());
    } catch (Exception e) {
      LOGGER.error("Failed to evaluate condition: {}", condition, e);
      return false;
    }
  }

  /** 分析表达式中的字段依赖 */
  private void analyzeExpressionDependencies(String expression, List<String> dependencies) {
    if (expression == null || expression.trim().isEmpty()) {
      return;
    }

    // 简化的依赖分析
    String[] potentialFields =
        expression.split("[\\s\\+\\-\\*\\/\\(\\)\\{\\}\\[\\],;:\\=\\!\\<\\>\\&\\|\\\\\\'\"]+");
    for (String field : potentialFields) {
      // 过滤掉关键字和数字
      if (!field.isEmpty()
          && !field.matches("^\\d+")
          && !field.equals("true")
          && !field.equals("false")
          && !field.equals("null")) {
        dependencies.add(field);
      }
    }
  }
}
