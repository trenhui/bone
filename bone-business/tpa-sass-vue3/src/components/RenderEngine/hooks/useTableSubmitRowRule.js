import { OperatorComparator } from "@/utils/comparator";
import { aggregateRule } from "@/utils/aggregation";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
import { ValueTypeEnum } from "@/enums";
import { getRuleVerifyTypeShortLabel } from "@/enums/rule/RuleVerifyTypeEnum";
import { convertPercentageForCalculation } from "../utils/dataFormatUtils";

const getFieldValue = (field, tableRow, componentManager) => {
  const dataBinding = field.dataBinding;
  const value = getValueByJsonPath(tableRow, dataBinding);
  // 如果是百分比字段则转换为小数形式参与计算
  return convertPercentageForCalculation(value, field.id, componentManager);
};

const getComparedValue = (valueType, value, tableRow, componentManager) => {
  if (valueType === ValueTypeEnum.fixed) {
    return value;
  }
  return getFieldValue(value, tableRow, componentManager);
};

export const useTableSubmitRowRule = (rules, tableRow, componentManager) => {
  if (!Array.isArray(rules) || rules.length === 0) return true;

  const messages = [];
  const result = rules.every((rule) => {
    const { fieldList, functionName, operator, valueType, value, verifyType } =
      rule;
    const fieldValues = fieldList.map((field) =>
      getFieldValue(field, tableRow, componentManager)
    );

    try {
      const functionResult = aggregateRule(fieldValues, functionName);

      const { result: compareResult } = OperatorComparator.compare(
        functionResult,
        getComparedValue(valueType, value, tableRow, componentManager),
        operator
      );

      if (!compareResult) {
        const prefix = `【${getRuleVerifyTypeShortLabel(verifyType)}】`;
        messages.push(
          rule.errorPrompt
            ? prefix + `${rule.errorPrompt}`
            : prefix + "表格行内保存规则校验未通过，请检查！"
        );
      }
      return compareResult;
    } catch (error) {
      console.error(error);
      const prefix = `【${getRuleVerifyTypeShortLabel(verifyType)}】`;
      messages.push(prefix + error.message);
      return false;
    }
  });

  return {
    success: result,
    messages,
  };
};
