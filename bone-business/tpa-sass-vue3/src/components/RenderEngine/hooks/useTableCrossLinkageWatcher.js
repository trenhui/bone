import { watch } from "vue";
import { useTableLinkageCrossTableRule } from "./useTableLinkageCrossTableRule";
import { useDataBinding } from "./useDataBinding";

/**
 * 跨表格联动规则监听 Hook
 * @param {Object} tableRules - 表格规则对象
 * @param {Object} tableManager - 表格管理器
 * @returns {Object} 返回初始化和清理方法
 */
export function useTableCrossLinkageWatcher(
  tableRules,
  tableManager,
  componentManager
) {
  /** 跨表格联动规则监听器集合 */
  const crossLinkageWatchers = new Map();

  /** 是否正在执行跨表格联动规则，防止无限循环 */
  let isExecutingCrossLinkageRules = false;

  /**
   * 执行跨表格联动规则计算
   */
  const executeCrossTableLinkageRules = () => {
    if (
      isExecutingCrossLinkageRules ||
      !tableRules.value?.linkageCrossTableRule ||
      tableRules.value.linkageCrossTableRule.length === 0
    ) {
      return;
    }

    isExecutingCrossLinkageRules = true;

    try {
      useTableLinkageCrossTableRule(
        tableRules.value.linkageCrossTableRule,
        tableManager,
        componentManager
      );
    } finally {
      isExecutingCrossLinkageRules = false;
    }
  };

  /**
   * 清理所有跨表格联动规则监听
   */
  const cleanup = () => {
    crossLinkageWatchers.forEach((unwatch) => {
      if (unwatch) {
        unwatch();
      }
    });
    crossLinkageWatchers.clear();
  };

  /**
   * 初始化跨表格联动规则监听
   */
  const init = () => {
    if (
      !tableRules.value?.linkageCrossTableRule ||
      tableRules.value.linkageCrossTableRule.length === 0
    ) {
      return;
    }

    // 先清理之前的监听器
    cleanup();

    // 按表格分组监听相关字段
    const tableFieldsMap = new Map();

    tableRules.value.linkageCrossTableRule.forEach((rule) => {
      const { currentTableId, currentRelationField, currentTableFieldList } =
        rule;

      if (!tableFieldsMap.has(currentTableId)) {
        tableFieldsMap.set(currentTableId, new Set());
      }

      const fields = tableFieldsMap.get(currentTableId);
      // 添加关联字段
      fields.add(currentRelationField.dataBinding);
      // 添加计算字段
      currentTableFieldList.forEach((field) => {
        fields.add(field.dataBinding);
      });
    });

    // 为每个表格的相关字段设置精确监听
    tableFieldsMap.forEach((fields, tableId) => {
      const tableData = tableManager.get(tableId);
      if (tableData) {
        const unwatch = watch(
          () => {
            const watchedValues = {};
            fields.forEach((fieldBinding) => {
              // 监听该字段在所有行中的值
              watchedValues[fieldBinding] = tableData.map((row, index) => {
                const { targetDataBinding } = useDataBinding(
                  fieldBinding,
                  index
                );
                return tableManager.getByJp(tableId, targetDataBinding.value);
              });
            });
            return watchedValues;
          },
          () => {
            executeCrossTableLinkageRules();
          },
          { deep: true, immediate: false } // 不立即执行，避免初始化时的重复计算
        );
        crossLinkageWatchers.set(tableId, unwatch);
      }
    });
  };

  /**
   * 手动执行跨表格联动规则（用于特殊场景）
   */
  const execute = () => {
    executeCrossTableLinkageRules();
  };

  return {
    init,
    cleanup,
    execute,
  };
}
