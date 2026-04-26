import { watch } from "vue";
import { useDataBinding } from "./useDataBinding";
import { aggregateRule } from "@/utils/aggregation/index";
import {
  convertPercentageForCalculation,
  convertPercentageForDisplay,
} from "../utils/dataFormatUtils";

/**
 * 表格联动规则监听 Hook
 * @param {Object} tableRules - 表格规则对象
 * @param {Ref} tableData - 表格数据响应式引用
 * @param {String} tableId - 表格ID
 * @param {Object} tableManager - 表格管理器
 * @param {Object} componentManager - 组件管理器
 * @returns {Object} 返回初始化和清理方法
 */
export function useTableLinkageWatcher(
  tableRules,
  tableData,
  tableId,
  tableManager,
  componentManager
) {
  /** 表格联动规则监听器 */
  let tableLinkageWatcher = null;

  /** 是否正在执行联动规则，防止无限循环 */
  let isExecutingLinkageRules = false;

  /** 上次监听的数据快照，用于检测变化 */
  let lastWatchedData = new Map();

  /**
   * 获取所有联动规则涉及的源字段
   */
  const getSourceFields = () => {
    const sourceFields = new Set();
    if (tableRules.value?.linkageRowRule) {
      tableRules.value.linkageRowRule.forEach((rule) => {
        rule.sourceFieldList.forEach((field) => {
          sourceFields.add(field.dataBinding);
        });
      });
    }
    return sourceFields;
  };

  /**
   * 获取所有联动规则涉及的目标字段
   */
  const getTargetFields = () => {
    const targetFields = new Set();
    if (tableRules.value?.linkageRowRule) {
      tableRules.value.linkageRowRule.forEach((rule) => {
        targetFields.add(rule.targetField.dataBinding);
      });
    }
    return targetFields;
  };

  /**
   * 检测哪些行的源字段发生了变化
   * @param {Set} sourceFields - 源字段集合
   * @returns {Set} 变化的行索引集合
   */
  const detectChangedRows = (sourceFields) => {
    const changedRows = new Set();

    tableData.value.forEach((row, rowIndex) => {
      sourceFields.forEach((fieldBinding) => {
        const { targetDataBinding } = useDataBinding(fieldBinding, rowIndex);
        const currentValue = tableManager.getByJp(
          tableId,
          targetDataBinding.value
        );
        const lastValue = lastWatchedData.get(targetDataBinding.value);

        if (currentValue !== lastValue) {
          changedRows.add(rowIndex);
          // 注意：这里不立即更新快照，在执行完联动规则后再更新
        }
      });
    });

    return changedRows;
  };

  /**
   * 更新数据快照
   * @param {Set} sourceFields - 源字段集合
   */
  const updateDataSnapshot = (sourceFields) => {
    tableData.value.forEach((row, rowIndex) => {
      sourceFields.forEach((fieldBinding) => {
        const { targetDataBinding } = useDataBinding(fieldBinding, rowIndex);
        const currentValue = tableManager.getByJp(
          tableId,
          targetDataBinding.value
        );
        lastWatchedData.set(targetDataBinding.value, currentValue);
      });
    });
  };

  /**
   * 执行指定行的联动规则计算
   * @param {number} rowIndex - 行索引
   */
  const executeRowLinkageRules = (rowIndex) => {
    if (
      !tableRules.value?.linkageRowRule ||
      tableRules.value.linkageRowRule.length === 0 ||
      rowIndex >= tableData.value.length
    ) {
      return;
    }

    tableRules.value.linkageRowRule.forEach((rule) => {
      const { sourceFieldList, functionName, targetField } = rule;

      const { targetDataBinding } = useDataBinding(
        targetField.dataBinding,
        rowIndex
      );

      const sourceDataBindings = sourceFieldList.map(
        (field) =>
          useDataBinding(field.dataBinding, rowIndex).targetDataBinding.value
      );

      // 获取源数据，如果是百分比字段则转换为小数形式参与计算
      const sourceData = sourceDataBindings.map((binding, index) => {
        const value = tableManager.getByJp(tableId, binding);
        const sourceField = sourceFieldList[index];
        return convertPercentageForCalculation(
          value,
          sourceField.id,
          componentManager
        );
      });

      // 计算结果
      const result = aggregateRule(sourceData, functionName);

      console.log("sourceData", sourceData);
      console.log("result", result);

      // 如果目标字段是百分比类型，需要将计算结果转换为百分比显示值
      const displayResult = convertPercentageForDisplay(
        result,
        targetField.id,
        componentManager
      );

      console.log("displayResult", displayResult);

      // 只有当结果与当前值不同时才更新，避免不必要的触发
      const currentValue = tableManager.getByJp(
        tableId,
        targetDataBinding.value
      );
      if (currentValue !== displayResult) {
        tableManager.setByJp(tableId, targetDataBinding.value, displayResult);
      }
    });
  };

  /**
   * 智能执行联动规则：只对变化的行执行计算
   */
  const executeSmartLinkageRules = () => {
    if (
      isExecutingLinkageRules ||
      !tableRules.value?.linkageRowRule ||
      tableRules.value.linkageRowRule.length === 0
    ) {
      return;
    }

    isExecutingLinkageRules = true;

    try {
      const sourceFields = getSourceFields();
      const changedRows = detectChangedRows(sourceFields);

      // 只对变化的行执行联动规则
      changedRows.forEach((rowIndex) => {
        executeRowLinkageRules(rowIndex);
      });

      // 执行完联动规则后，更新数据快照
      updateDataSnapshot(sourceFields);
    } finally {
      isExecutingLinkageRules = false;
    }
  };

  /**
   * 执行所有行的联动规则计算（用于初始化）
   */
  const executeAllTableLinkageRules = () => {
    if (
      isExecutingLinkageRules ||
      !tableRules.value?.linkageRowRule ||
      tableRules.value.linkageRowRule.length === 0
    ) {
      return;
    }

    isExecutingLinkageRules = true;

    try {
      // 初始化时，先执行所有行的联动规则
      tableData.value.forEach((row, rowIndex) => {
        executeRowLinkageRules(rowIndex);
      });

      // 执行完后，更新数据快照
      const sourceFields = getSourceFields();
      updateDataSnapshot(sourceFields);
    } finally {
      isExecutingLinkageRules = false;
    }
  };

  /**
   * 清理表格联动规则监听
   */
  const cleanup = () => {
    if (tableLinkageWatcher) {
      tableLinkageWatcher();
      tableLinkageWatcher = null;
    }
    lastWatchedData.clear();
  };

  /**
   * 初始化表格联动规则监听
   */
  const init = () => {
    if (
      !tableRules.value?.linkageRowRule ||
      tableRules.value.linkageRowRule.length === 0
    ) {
      return;
    }

    // 先清理之前的监听器
    cleanup();

    // 获取所有源字段和目标字段
    const sourceFields = getSourceFields();
    const targetFields = getTargetFields();

    // 单一监听器：只监听源字段的变化，排除目标字段避免循环
    tableLinkageWatcher = watch(
      () => {
        const watchedValues = {};
        tableData.value.forEach((row, rowIndex) => {
          sourceFields.forEach((fieldBinding) => {
            // 如果源字段同时也是目标字段，需要特殊处理避免循环
            if (!targetFields.has(fieldBinding) || !isExecutingLinkageRules) {
              const { targetDataBinding } = useDataBinding(
                fieldBinding,
                rowIndex
              );
              watchedValues[targetDataBinding.value] = tableManager.getByJp(
                tableId,
                targetDataBinding.value
              );
            }
          });
        });
        return watchedValues;
      },
      () => {
        executeSmartLinkageRules();
      },
      { deep: true, immediate: false } // 不立即执行，避免重复执行
    );
  };

  /**
   * 手动执行联动规则（用于特殊场景）
   */
  const execute = () => {
    executeAllTableLinkageRules();
  };

  return {
    init,
    cleanup,
    execute,
  };
}
