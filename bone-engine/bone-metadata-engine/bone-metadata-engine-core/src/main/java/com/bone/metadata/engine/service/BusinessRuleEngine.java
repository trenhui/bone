package com.bone.metadata.engine.service;

import com.bone.metadata.engine.model.RuleResult;
import java.util.List;
import java.util.Map;

/** 统一业务规则引擎接口 提供基于元数据的业务规则管理和执行功能 替代硬编码的业务规则验证逻辑（如validateOrderItems方法） */
public interface BusinessRuleEngine {

  /**
   * 执行实体的所有业务规则
   *
   * @param entityName 实体名称
   * @param entityData 实体数据（Map形式）
   * @return 规则执行结果
   */
  RuleResult executeRules(String entityName, Map<String, Object> entityData);

  /**
   * 执行指定的业务规则
   *
   * @param entityName 实体名称
   * @param entityData 实体数据（Map形式）
   * @param ruleNames 要执行的规则名称列表
   * @return 规则执行结果
   */
  RuleResult executeRules(
      String entityName, Map<String, Object> entityData, List<String> ruleNames);

  /**
   * 验证实体数据是否符合所有规则
   *
   * @param entityName 实体名称
   * @param entityData 实体数据（Map形式）
   * @return 是否验证通过
   */
  boolean validate(String entityName, Map<String, Object> entityData);

  /**
   * 计算实体的所有计算字段
   *
   * @param entityName 实体名称
   * @param entityData 实体数据（Map形式）
   * @return 更新后的实体数据
   */
  Map<String, Object> calculateFields(String entityName, Map<String, Object> entityData);

  /**
   * 计算指定的计算字段
   *
   * @param entityName 实体名称
   * @param entityData 实体数据（Map形式）
   * @param fieldName 要计算的字段名称
   * @return 计算结果
   */
  Object calculateField(String entityName, Map<String, Object> entityData, String fieldName);

  /**
   * 获取实体的所有业务规则
   *
   * @param entityName 实体名称
   * @return 规则定义列表
   */
  List<Map<String, Object>> getRules(String entityName);

  /**
   * 注册自定义规则
   *
   * @param ruleName 规则名称
   * @param entityName 适用的实体名称
   * @param ruleDefinition 规则定义
   */
  void registerRule(String ruleName, String entityName, Map<String, Object> ruleDefinition);

  /**
   * 评估实体是否满足指定条件
   *
   * @param entityName 实体名称
   * @param entityData 实体数据
   * @param conditionExpression 条件表达式
   * @return 评估结果
   */
  boolean evaluateCondition(
      String entityName, Map<String, Object> entityData, String conditionExpression);
}
