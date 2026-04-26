import { setValueByJsonPath } from "@/utils/jsonpathUtils";
import { useDataBinding } from "./useDataBinding";

/**
 * 选项集联动字段（表单）
 * @param extraProperties 额外属性
 * @param affectedFieldList 选项集联动字段集合
 * @returns
 */
export const useFieldSetLinkageOptionField = (
  extraProperties,
  affectedFieldList,
  dataManager
) => {
  if (
    !extraProperties ||
    !affectedFieldList ||
    !affectedFieldList.length ||
    !dataManager
  )
    return;

  affectedFieldList.forEach((affectedItem) => {
    const {
      extraProperty: extraPropertyKey,
      dataBinding,
      type,
      script,
    } = affectedItem;

    let extraPropertyValue = null;
    try {
      if (type === 1) {
        extraPropertyValue = new Function("row", script)(extraProperties);
      } else {
        extraPropertyValue = extraProperties[extraPropertyKey];
      }
    } catch (error) {
      console.error(error);
    }

    if (extraPropertyValue) {
      dataManager.setByJp(dataBinding, extraPropertyValue);
    }
  });
};

/**
 * 选项集联动字段（表格）
 * @param extraProperties 额外属性
 * @param affectedFieldList 选项集联动字段集合
 * @returns
 */
export const useTableLinkageOptionField = (
  extraProperties,
  affectedFieldList,
  tableRow
) => {
  if (
    !extraProperties ||
    !affectedFieldList ||
    !affectedFieldList.length ||
    !tableRow
  )
    return;

  affectedFieldList.forEach((affectedItem) => {
    const {
      extraProperty: extraPropertyKey,
      dataBinding,
      type,
      script,
    } = affectedItem;

    let extraPropertyValue = null;
    try {
      if (type === 1) {
        extraPropertyValue = new Function("row", script)(extraProperties);
      } else {
        extraPropertyValue = extraProperties[extraPropertyKey];
      }
    } catch (error) {
      console.error(error);
    }

    const { targetDataBinding } = useDataBinding(dataBinding);

    if (extraPropertyValue) {
      setValueByJsonPath(tableRow, targetDataBinding.value, extraPropertyValue);
    }
  });
};

/**
 * 选项集联动字段
 * @param isTable 是否是表格
 * @param extraProperties 额外属性
 * @param affectedFieldList 选项集联动字段集合
 * @param dataManager 数据管理
 * @param tableManager 表格管理
 * @param tableIndex 表格索引
 * @param tableId 表格ID
 * @returns
 */
export const useLinkageOptionField = (
  isTable,
  extraProperties,
  affectedFieldList,
  dataManager,
  tableRow
) => {
  if (isTable) {
    return useTableLinkageOptionField(
      extraProperties,
      affectedFieldList,
      tableRow
    );
  }
  return useFieldSetLinkageOptionField(
    extraProperties,
    affectedFieldList,
    dataManager
  );
};
