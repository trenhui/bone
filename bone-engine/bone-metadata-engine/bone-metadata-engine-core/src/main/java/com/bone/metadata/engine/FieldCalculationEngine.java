package com.bone.metadata.engine;

import com.bone.metadata.engine.model.DynamicSmartEntity;
import com.bone.metadata.engine.model.FieldMetadata;

/** 字段计算引擎接口 负责计算实体中的计算字段值 */
public interface FieldCalculationEngine {

  /**
   * 计算指定字段的值
   *
   * @param entity 实体实例
   * @param fieldMetadata 字段元数据
   * @return 计算得到的字段值
   * @throws Exception 计算过程中发生错误时抛出
   */
  Object calculateField(DynamicSmartEntity entity, FieldMetadata fieldMetadata) throws Exception;

  /**
   * 计算实体的所有计算字段
   *
   * @param entity 实体实例
   * @throws Exception 计算过程中发生错误时抛出
   */
  void calculateAllFields(DynamicSmartEntity entity) throws Exception;

  /**
   * 检查字段表达式是否有效
   *
   * @param fieldMetadata 字段元数据
   * @return 字段表达式是否有效
   */
  boolean validateExpression(FieldMetadata fieldMetadata);

  /**
   * 获取表达式中使用的所有字段
   *
   * @param expression 计算表达式
   * @return 表达式中使用的字段名称列表
   */
  java.util.List<String> getExpressionDependencies(String expression);
}
