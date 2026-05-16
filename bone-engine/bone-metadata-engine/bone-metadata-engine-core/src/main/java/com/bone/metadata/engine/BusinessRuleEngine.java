package com.bone.metadata.engine;

import com.bone.metadata.engine.model.BusinessRuleMetadata;
import com.bone.metadata.engine.model.DynamicSmartEntity;
import com.bone.metadata.engine.validation.ValidationResult;
import java.util.List;
import java.util.Map;

/** 通用业务规则引擎接口 负责执行业务规则的验证和操作，支持多种业务场景 */
public interface BusinessRuleEngine {

  /**
   * 执行验证规则
   *
   * @param entity 实体实例
   * @param result 验证结果容器
   */
  void executeValidationRules(DynamicSmartEntity entity, ValidationResult result);

  /**
   * 执行操作规则
   *
   * @param entity 实体实例
   * @param eventType 触发事件类型
   */
  void executeActionRules(DynamicSmartEntity entity, String eventType);

  /**
   * 执行单个规则
   *
   * @param entity 实体实例
   * @param rule 业务规则
   * @param context 执行上下文
   * @return 规则执行结果
   */
  Object executeRule(
      DynamicSmartEntity entity, BusinessRuleMetadata rule, Map<String, Object> context);

  /**
   * 验证规则表达式
   *
   * @param rule 业务规则
   * @return 是否有效
   */
  boolean validateRule(BusinessRuleMetadata rule);

  /**
   * 获取规则的依赖字段
   *
   * @param rule 业务规则
   * @return 依赖字段列表
   */
  List<String> getRuleDependencies(BusinessRuleMetadata rule);

  /**
   * 根据事件类型获取触发的规则
   *
   * @param entityApiName 实体API名称
   * @param eventType 事件类型
   * @return 规则列表
   */
  List<BusinessRuleMetadata> getRulesForEvent(String entityApiName, String eventType);

  /**
   * 评估高价值订单规则
   *
   * @param orderData 订单数据
   * @return 规则执行结果
   */
  ValidationResult evaluateHighValueOrderRule(Map<String, Object> orderData);

  /**
   * 评估订单优先级规则
   *
   * @param orderData 订单数据
   * @return 规则执行结果
   */
  ValidationResult evaluateOrderPriorityRule(Map<String, Object> orderData);

  /**
   * 执行指定类型的业务规则
   *
   * @param entity 实体对象
   * @param ruleType 规则类型
   * @return 规则执行结果
   */
  ValidationResult executeRules(Object entity, String ruleType);
}
