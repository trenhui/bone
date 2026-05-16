package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 计算字段元数据类 支持复杂的动态计算表达式和依赖管理 */
@Getter
@Setter
public class CalculatedFieldMetadata extends SmartFieldMetadata {

  // 计算表达式
  private String calculationExpression;

  // 计算依赖字段列表
  private List<String> calculationDependencies = new ArrayList<>();

  // 计算语言（如Groovy、SpEL、JavaScript等）
  private String calculationLanguage = "SpEL";

  // 计算结果是否可缓存
  private boolean cacheable = true;

  // 缓存有效期（毫秒）
  private long cacheTtl = 300000; // 默认5分钟

  // 计算频率类型（实时、批量、定时）
  private CalculationFrequency calculationFrequency = CalculationFrequency.REALTIME;

  // 计算条件
  private String calculationCondition;

  // 结果数据类型
  private String resultDataType;

  /** 计算频率枚举 */
  public enum CalculationFrequency {
    // 实时计算
    REALTIME,
    // 批量计算
    BATCH,
    // 定时计算
    SCHEDULED
  }

  /** 添加计算依赖字段 */
  public void addCalculationDependency(String fieldApiName) {
    if (calculationDependencies == null) {
      calculationDependencies = new ArrayList<>();
    }
    if (!calculationDependencies.contains(fieldApiName)) {
      calculationDependencies.add(fieldApiName);
    }
  }

  /** 检查是否有计算依赖 */
  public boolean hasDependencies() {
    return calculationDependencies != null && !calculationDependencies.isEmpty();
  }

  /** 获取计算表达式 */
  @Override
  public String getCalculationExpression() {
    return calculationExpression;
  }

  /** 设置计算表达式 */
  @Override
  public void setCalculationExpression(String calculationExpression) {
    this.calculationExpression = calculationExpression;
    // 使用setter方法设置为虚拟字段
    setVirtual(true);
  }

  /** 获取计算依赖字段列表 */
  public List<String> getCalculationDependencies() {
    return calculationDependencies;
  }

  /** 设置计算依赖字段列表 */
  public void setCalculationDependencies(List<String> calculationDependencies) {
    this.calculationDependencies = calculationDependencies;
  }

  /** 设置结果数据类型 */
  public void setResultDataType(String resultDataType) {
    this.resultDataType = resultDataType;
    // SmartFieldMetadata没有setFormulaReturnType方法，使用setType方法替代
    super.setType(resultDataType);
  }

  /** 获取结果数据类型 */
  public String getResultDataType() {
    return resultDataType;
  }
}
