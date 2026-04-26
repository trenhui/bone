import { watch, computed } from "vue";
import { aggregateRule } from "@/utils/aggregation/index";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import {
  convertPercentageForCalculation,
  convertPercentageForDisplay,
} from "../utils/dataFormatUtils";

let unwatchers = [];

// 用于处理表格联动行规则
export const useTableLinkageRowRule = (rules, row, componentManager) => {
  if (!Array.isArray(rules) || rules.length === 0) return;

  cleanupWatchers();

  rules.forEach((rule) => {
    const { sourceFieldList, functionName, targetField } = rule;

    const targetDataBinding = targetField.dataBinding;

    const sourceDataBindings = sourceFieldList.map(
      (field) => field.dataBinding
    );

    // 使用计算属性来获取源数据，如果是百分比字段则转换为小数形式参与计算
    const sourceData = computed(() =>
      sourceDataBindings.map((binding, index) => {
        const value = getValueByJsonPath(row, binding);
        const sourceField = sourceFieldList[index];
        return convertPercentageForCalculation(
          value,
          sourceField.id,
          componentManager
        );
      })
    );

    const calculateResult = () => {
      const result = aggregateRule(sourceData.value, functionName);

      // 如果目标字段是百分比类型，需要将计算结果转换为百分比显示值
      const displayResult = convertPercentageForDisplay(
        result,
        targetField.id,
        componentManager
      );

      setValueByJsonPath(row, targetDataBinding, displayResult);
    };

    // 监听源数据的变化
    const unwatch = watch(sourceData, calculateResult, {
      deep: true,
      immediate: true,
    });

    unwatchers.push(unwatch);
  });
};

export const cleanupWatchers = () => {
  if (unwatchers) {
    unwatchers.forEach((unwatch) => unwatch());
    unwatchers = [];
  }
};
