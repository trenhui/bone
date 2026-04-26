import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import { aggregateRule } from "@/utils/aggregation/index";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import EventBus from "@/utils/eventBus";
import { isEqual } from "lodash-es";
import {
  convertPercentageForCalculation,
  convertPercentageForDisplay,
} from "../utils/dataFormatUtils";

export const useTableLinkageCrossTableRule = (
  rules,
  tableManager,
  componentManager
) => {
  if (!Array.isArray(rules) || rules.length === 0) return;

  const calculateRule = (rule) => {
    // 获取表格间关联关系
    const {
      currentTableId,
      targetTableId,
      targetRelationField,
      currentRelationField,
      functionName,
      targetTableField,
      currentTableFieldList,
    } = rule;

    // 获取源表格和目标表格数据
    const currentTableData = tableManager.get(currentTableId);
    const targetTableData = tableManager.get(targetTableId);

    // 计算结果
    if (
      !currentTableData ||
      !targetTableData ||
      currentTableData.length === 0 ||
      targetTableData.length === 0
    ) {
      return new Map();
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

    if (!resultMap || resultMap.size === 0) return;

    const targetRelationBinding = targetRelationField.dataBinding;
    const targetTableBinding = targetTableField.dataBinding;

    const updatedRows = [];

    // 计算目标表格数据
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
        const calculatedValue = resultMap.get(targetFieldValue);

        // 如果目标字段是百分比类型，需要将计算结果转换为百分比显示值
        const newValue = convertPercentageForDisplay(
          calculatedValue,
          targetTableField.id,
          componentManager
        );

        const oldValue = getValueByJsonPath(item, targetTableBinding);
        if (!isEqual(newValue, oldValue)) {
          setValueByJsonPath(item, targetTableBinding, newValue);
          updatedRows.push(item);
        }
      }
    });

    // 如果有更新，则更新目标表格数据
    if (updatedRows.length > 0) {
      console.log("updatedRows", updatedRows);
      EventBus.emit(`table:${targetTableId}:linkageChange`, updatedRows);
    }
  };

  rules.forEach(calculateRule);
};
