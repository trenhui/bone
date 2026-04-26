import { ref, watch, computed } from "vue";
import { useDictStore } from "@/store";
import { useDataBinding } from "./useDataBinding";
import { BaseCompType } from "@/enums";

/**
 * 自定义SelectDrop筛选功能Hook
 * 使用el-table原生筛选功能
 *
 * @param {Object} options 配置选项
 * @param {Ref} options.tableData 表格数据
 * @param {Array} options.columns 列配置
 * @param {String} options.tableId 表格ID
 * @param {Object} options.tableManager 表格管理器
 * @param {ComputedRef} options.isConfigMode 是否配置模式
 * @returns {Object} 筛选相关的方法和数据
 */
export function useSelectDropFilter({
  tableData,
  columns,
  tableId,
  tableManager,
  isConfigMode,
}) {
  // 筛选选项：{columnId: [{text, value}]}
  const filterOptions = ref({});

  /**
   * 判断指定列是否应该启用筛选功能
   */
  const shouldEnableFilter = (columnItem) => {
    return (
      !isConfigMode.value &&
      columnItem.type.replace("PK", "") === BaseCompType.SelectDrop
    );
  };

  /**
   * 获取列的所有可选值和对应的显示标签
   */
  const getColumnOptions = async (columnItem) => {
    if (!shouldEnableFilter(columnItem)) return undefined;
    if (!tableData.value || tableData.value.length === 0) return undefined;

    const values = new Set();

    // 收集所有不重复的值
    tableData.value.forEach((row, index) => {
      const { targetDataBinding } = useDataBinding(
        columnItem.dataBinding,
        index
      );
      const value = tableManager.getByJp(tableId, targetDataBinding.value);

      if (value !== null && value !== undefined && value !== "") {
        values.add(String(value));
      }
    });

    const originalValues = Array.from(values);

    if (originalValues.length === 0) return undefined;

    try {
      // 获取字典标签
      const dictStore = useDictStore();
      const dictLabels = await dictStore.getDictLabel(
        columnItem.selectDatasource.type,
        columnItem.selectDatasource.code,
        originalValues
      );

      // 构建选项
      const options = originalValues.map((value) => ({
        text: dictLabels[value] || value,
        value: value,
      }));

      // 按显示文本排序
      return options.sort((a, b) => a.text.localeCompare(b.text));
    } catch (error) {
      console.error("获取字典标签失败:", error);
      return originalValues.sort().map((value) => ({
        text: value,
        value: value,
      }));
    }
  };

  /**
   * 更新所有筛选选项
   */
  const updateFilterOptions = async () => {
    const options = {};

    for (const column of columns.value) {
      if (shouldEnableFilter(column)) {
        const columnOptions = await getColumnOptions(column);
        options[column.id] = columnOptions;
      }
    }

    filterOptions.value = options;
  };

  /**
   * 获取指定列的筛选选项
   */
  const getFilterOptions = (columnItem) => {
    const options = shouldEnableFilter(columnItem)
      ? (filterOptions.value[columnItem.id] ?? undefined)
      : undefined;

    return options;
  };

  /**
   * 处理el-table原生列筛选
   */
  const handleColumnFilter = (value, row, column, columnItem) => {
    const rowIndex = tableData.value.indexOf(row);

    const { targetDataBinding } = useDataBinding(
      columnItem.dataBinding,
      rowIndex
    );

    const cellValue = tableManager.getByJp(tableId, targetDataBinding.value);

    const cellValueStr = String(cellValue || "");

    const result = cellValueStr == value;

    return result;
  };

  // 监听数据变化，更新筛选选项
  watch(
    () => tableData.value,
    () => {
      updateFilterOptions();
    },
    { immediate: true, deep: true }
  );

  return {
    // 数据
    filterOptions,

    // 方法
    shouldEnableFilter,
    getFilterOptions,
    updateFilterOptions,
    handleColumnFilter, // 添加el-table原生筛选处理方法
  };
}
