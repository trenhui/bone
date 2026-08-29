package com.bone.metadata.engine.runtime;

import com.bone.metadata.engine.domain.metadata.BusinessRuleMetadata;
import com.bone.metadata.engine.domain.metadata.DynamicSmartEntity;
import com.bone.metadata.engine.runtime.validation.ValidationResult;
import java.util.List;
import java.util.Map;

/** 统一规则引擎接口 提供通用的规则执行和验证功能，支持多种业务场景 */
public interface UnifiedRuleEngine<T> {

  /**
   * 验证实体
   *
   * @param entity 实体对象
   * @param context 执行上下文
   * @return 验证结果
   */
  ValidationResult validate(T entity, RuleContext context);

  /**
   * 执行指定类型的业务规则
   *
   * @param entity 实体对象
   * @param ruleType 规则类型
   * @param context 执行上下文
   * @return 规则执行结果
   */
  ValidationResult executeRules(T entity, String ruleType, RuleContext context);

  /**
   * 获取适用的规则列表
   *
   * @param entity 实体对象
   * @param eventType 事件类型
   * @return 规则元数据列表
   */
  List<BusinessRuleMetadata> getApplicableRules(T entity, String eventType);

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
}

// RuleContext类已移至单独的RuleContext.java文件中
