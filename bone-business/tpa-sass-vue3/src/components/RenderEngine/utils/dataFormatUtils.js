import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";

/**
 * 数据格式处理工具函数
 */

/**
 * 检查字段是否为百分比类型
 * @param {String} fieldId - 字段ID
 * @param {Object} componentManager - 组件管理器
 * @returns {Boolean} 是否为百分比类型
 */
export const isPercentageField = (fieldId, componentManager) => {
  if (!componentManager) return false;
  const field = componentManager.get(fieldId);
  return field && field.dataFormat === DataFormatEnum.percentage;
};

/**
 * 处理百分比数据：从显示值转换为计算值
 * @param {Number} value - 显示值
 * @param {String} fieldId - 字段ID
 * @param {Object} componentManager - 组件管理器
 * @returns {Number} 计算值
 */
export const convertPercentageForCalculation = (
  value,
  fieldId,
  componentManager
) => {
  const isPercentage = isPercentageField(fieldId, componentManager);

  if (isPercentage) {
    // 处理数字类型
    if (typeof value === "number" && !isNaN(value)) {
      const convertedValue = value / 100;
      console.log(
        `[百分比转换-计算] 字段ID: ${fieldId}, 原值: ${value}, 转换后: ${convertedValue}`
      );
      return convertedValue;
    }

    // 处理字符串类型
    if (typeof value === "string") {
      const numValue = parseFloat(value);
      if (!isNaN(numValue)) {
        const convertedValue = numValue / 100;
        console.log(
          `[百分比转换-计算] 字段ID: ${fieldId}, 原值: ${value}(string), 转换后: ${convertedValue}`
        );
        return convertedValue;
      }
    }

    console.log(
      `[百分比转换-计算] 字段ID: ${fieldId}, 值类型: ${typeof value}, 值: ${value}, 跳过转换`
    );
  }

  return value;
};

/**
 * 处理百分比数据：从计算值转换为显示值
 * @param {Number} value - 计算值
 * @param {String} fieldId - 字段ID
 * @param {Object} componentManager - 组件管理器
 * @returns {Number} 显示值
 */
export const convertPercentageForDisplay = (
  value,
  fieldId,
  componentManager
) => {
  const isPercentage = isPercentageField(fieldId, componentManager);

  if (isPercentage) {
    // 处理数字类型
    if (typeof value === "number" && !isNaN(value)) {
      const convertedValue = value * 100;
      console.log(
        `[百分比转换-显示] 字段ID: ${fieldId}, 原值: ${value}, 转换后: ${convertedValue}`
      );
      return convertedValue;
    }

    // 处理字符串类型
    if (typeof value === "string") {
      const numValue = parseFloat(value);
      if (!isNaN(numValue)) {
        const convertedValue = numValue * 100;
        console.log(
          `[百分比转换-显示] 字段ID: ${fieldId}, 原值: ${value}(string), 转换后: ${convertedValue}`
        );
        return convertedValue;
      }
    }

    console.log(
      `[百分比转换-显示] 字段ID: ${fieldId}, 值类型: ${typeof value}, 值: ${value}, 跳过转换`
    );
  }

  return value;
};

/**
 * 根据字段类型处理数值数据
 * @param {Number} value - 原始值
 * @param {String} fieldId - 字段ID
 * @param {Object} componentManager - 组件管理器
 * @param {String} convertType - 转换类型: 'toCalculation' | 'toDisplay'
 * @returns {Number} 处理后的值
 */
export const convertValueByFieldType = (
  value,
  fieldId,
  componentManager,
  convertType = "toCalculation"
) => {
  if (convertType === "toCalculation") {
    return convertPercentageForCalculation(value, fieldId, componentManager);
  } else if (convertType === "toDisplay") {
    return convertPercentageForDisplay(value, fieldId, componentManager);
  }
  return value;
};
