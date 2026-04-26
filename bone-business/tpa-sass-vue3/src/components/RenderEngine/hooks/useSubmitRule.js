import { getValueInData } from "../utils/dataUtil";
import { createRuleFactory } from "../modules/submitRule/utils/factoryGenerator";
import { getRuleVerifyTypeShortLabel } from "@/enums/rule/RuleVerifyTypeEnum";
import { ValueTypeEnum } from "@/enums";
import { convertPercentageForCalculation } from "../utils/dataFormatUtils";

/**
 * 获取比较值，处理固定值和动态值的不同情况
 * @param {Number} valueType - 值类型
 * @param {*} value - 值或字段引用
 * @param {Object} dataManager - 数据管理器
 * @param {Object} componentManager - 组件管理器
 * @returns {*} 处理后的比较值
 */
const getComparedValueWithPercentage = (
  valueType,
  value,
  dataManager,
  componentManager
) => {
  if (valueType === ValueTypeEnum.fixed) {
    // 固定值：直接返回，不需要转换
    return value;
  } else {
    // 动态值（字段引用）：需要根据字段类型判断是否转换
    const fieldValue = getValueInData(value, dataManager);
    // 如果引用的字段是百分比类型，需要转换为小数形式与functionResult保持一致
    return convertPercentageForCalculation(
      fieldValue,
      value.id,
      componentManager
    );
  }
};

export const useSubmitRule = (rules, dataManager, componentManager) => {
  const messages = [];
  const success = rules.every((rule) => {
    const factory = createRuleFactory(rule.functionType);
    const fieldValues = rule.fieldList.map((field) => {
      const value = getValueInData(field, dataManager);
      return convertPercentageForCalculation(value, field.id, componentManager);
    });

    try {
      const functionResult = factory.execute(rule.functionName, fieldValues);
      const compareResult = factory.compare(
        functionResult,
        rule.operator,
        getComparedValueWithPercentage(
          rule.valueType,
          rule.value,
          dataManager,
          componentManager
        )
      );
      if (!compareResult) {
        const prefix = `【${getRuleVerifyTypeShortLabel(rule.verifyType)}】`;
        const errorMessage = rule.errorPrompt
          ? `${prefix}${rule.errorPrompt}`
          : `${prefix}校验失败，请检查提交规则是否满足`;
        messages.push(errorMessage);
      }
      return compareResult;
    } catch (error) {
      console.log(error);
      const prefix = `【${getRuleVerifyTypeShortLabel(rule.verifyType)}】`;
      const errorMessage = rule.errorPrompt
        ? `${prefix}${rule.errorPrompt}`
        : `${prefix}校验失败，请检查提交规则是否满足`;
      messages.push(errorMessage);
      return false;
    }
  });

  return { success, messages };
};
