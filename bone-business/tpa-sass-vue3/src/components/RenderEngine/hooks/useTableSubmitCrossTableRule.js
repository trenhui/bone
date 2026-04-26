import { OperatorComparator } from "@/utils/comparator";
import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import { aggregateRule } from "@/utils/aggregation/index";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
import { getRuleVerifyTypeShortLabel } from "@/enums/rule/RuleVerifyTypeEnum";
import { convertPercentageForCalculation } from "../utils/dataFormatUtils";

export const useTableSubmitCrossTableRule = (
  rules,
  tableManager,
  componentManager
) => {
  if (!Array.isArray(rules) || rules.length === 0) return { success: true };

  const compare = (rule) => {
    // 获取表格间关联关系
    const {
      currentTableId,
      targetTableId,
      targetRelationField,
      currentRelationField,
      currentTableFieldList,
      targetTableField,
      functionName,
      operator,
      verifyType,
    } = rule;

    // 获取源表格和目标表格数据
    const currentTableData = tableManager.get(currentTableId);
    const targetTableData = tableManager.get(targetTableId);

    if (
      !currentTableData ||
      !targetTableData ||
      currentTableData.length === 0 ||
      targetTableData.length === 0
    ) {
      return true;
    }

    const currentRelationBinding = currentRelationField.dataBinding;
    const currentTableListBindings = currentTableFieldList.map(
      (field) => field.dataBinding
    );

    // 根据目标表格关联字段对源表格进行分组
    const groupData = new Map();
    currentTableData.forEach((item) => {
      const groupKey = getValueByJsonPath(item, currentRelationBinding);

      // 跳过无效的关联值：null、undefined、空字符串等
      if (groupKey == null || groupKey === "" || groupKey === undefined) {
        return;
      }

      if (!groupData.has(groupKey)) {
        groupData.set(groupKey, []);
      }
      groupData.get(groupKey)?.push(item);
    });

    // 根据分组结果对源表格进行聚合处理
    const resultMap = new Map();
    for (const [groupKey, group] of groupData) {
      const values = group.map((data) =>
        aggregateRule(
          currentTableListBindings.map((binding, index) => {
            const value = getValueByJsonPath(data, binding);
            const sourceField = currentTableFieldList[index];
            // 如果是百分比字段则转换为小数形式参与计算
            return convertPercentageForCalculation(
              value,
              sourceField.id,
              componentManager
            );
          }),
          functionName
        )
      );
      resultMap.set(groupKey, aggregateRule(values, FunctionEnum.SUM));
    }

    const changedKeys = new Set(resultMap.keys());

    if (!resultMap || resultMap.size === 0) return true;

    const targetRelationBinding = targetRelationField.dataBinding;
    const targetTableBinding = targetTableField.dataBinding;

    // 校验目标表格数据
    let isValid = true;
    targetTableData.forEach((item) => {
      const targetFieldValue = getValueByJsonPath(item, targetRelationBinding);

      // 跳过无效的关联值，只处理有效的关联
      if (
        targetFieldValue == null ||
        targetFieldValue === "" ||
        targetFieldValue === undefined
      ) {
        return;
      }

      if (changedKeys.has(targetFieldValue)) {
        const newValue = resultMap.get(targetFieldValue);
        const rawOldValue = getValueByJsonPath(item, targetTableBinding);

        // 将目标字段的原始值也转换为小数形式，确保与计算结果使用相同格式比较
        const oldValue = convertPercentageForCalculation(
          rawOldValue,
          targetTableField.id,
          componentManager
        );

        const { result: compareResult } = OperatorComparator.compare(
          newValue,
          oldValue,
          operator
        );
        if (!compareResult) {
          isValid = false;
        }
      }
    });

    return isValid;
  };

  const messages = [];
  const allResult = rules.every((rule) => {
    const result = compare(rule);
    if (!result) {
      const prefix = `【${getRuleVerifyTypeShortLabel(rule.verifyType)}】`;
      messages.push(
        rule.errorPrompt
          ? prefix + `${rule.errorPrompt}`
          : prefix + "表格间保存规则校验未通过，请检查！"
      );
    }
    return result;
  });

  return { success: allResult, messages };
};
