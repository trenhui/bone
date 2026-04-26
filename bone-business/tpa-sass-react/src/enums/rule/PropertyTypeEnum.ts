// =============================================================================
// 字段动态规则属性类型枚举定义
// 用于配置表单字段的各种动态属性，如必填状态、显示隐藏、输入状态等
// =============================================================================

import {
  RequiredOptions,
  getRequiredLabel,
} from "@/enums/baseComp/RequiredEnum";
import {
  DisplayedOptions,
  getDisplayedLabel,
} from "@/enums/baseComp/DisplayedEnum";
import {
  InputStatusOptions,
  getInputStatusLabel,
} from "@/enums/baseComp/InputStatusEnum";
import {
  TableDisplayOptions,
  getTableDisplayLabel,
} from "@/enums/table/TableDisplayEnum";
import {
  InputValueTypeOptions,
  getInputValueTypeLabel,
} from "@/enums/baseComp/InputValueTypeEnum";
import { BaseCompType } from "../baseComp/BaseCompEnum";
import {
  DateRangeValueTypeOptions,
  getDateRangeValueTypeLabel,
} from "../baseComp/DateRangeValueTypeEnum";

// =============================================================================
// 1. 枚举定义
// =============================================================================

/**
 * 字段动态规则中可配置的属性类型枚举
 * 用于定义普通表单字段可以配置的动态属性
 */
export enum PropertyTypeEnum {
  /** 是否必填 */
  required = "required",
  /** 显示隐藏 */
  displayed = "displayed",
  /** 输入状态（只读、可编辑等） */
  inputStatus = "inputStatus",
  /** 值类型（针对不同组件类型） */
  valueType = "valueType",
}

/**
 * 表格字段动态规则中可配置的属性类型枚举
 * 用于定义表格字段可以配置的动态属性
 */
export enum TablePropertyTypeEnum {
  /** 显示隐藏 */
  display = "display",
}

// =============================================================================
// 2. 标签映射
// =============================================================================

/**
 * 普通属性类型的中文标签映射
 */
export const PropertyTypeLabels: Record<PropertyTypeEnum, string> = {
  [PropertyTypeEnum.required]: "是否必填",
  [PropertyTypeEnum.displayed]: "显示隐藏",
  [PropertyTypeEnum.inputStatus]: "输入状态",
  [PropertyTypeEnum.valueType]: "值类型",
};

/**
 * 表格属性类型的中文标签映射
 */
export const TablePropertyTypeLabels: Record<TablePropertyTypeEnum, string> = {
  [TablePropertyTypeEnum.display]: "显示隐藏",
};

// =============================================================================
// 3. 选项配置
// =============================================================================

/**
 * 普通属性类型选项数组
 * 用于下拉选择等UI组件
 */
export const PropertyTypeOptions = Object.values(PropertyTypeEnum).map(
  (type) => ({
    label: PropertyTypeLabels[type],
    value: type,
  })
);

/**
 * 表格属性类型选项数组
 * 用于下拉选择等UI组件
 */
export const TablePropertyTypeOptions = Object.values(
  TablePropertyTypeEnum
).map((type) => ({
  label: TablePropertyTypeLabels[type],
  value: type,
}));

// =============================================================================
// 4. 属性值选项配置
// =============================================================================

/**
 * 值类型选项映射
 * 根据不同的组件类型提供不同的值类型选项
 */
const valueTypeOptions = {
  [BaseCompType.Input]: InputValueTypeOptions, // 输入框的值类型选项
  [BaseCompType.DateRange]: DateRangeValueTypeOptions, // 日期范围的值类型选项
};

/**
 * 普通属性的属性值选项映射
 * 每种属性类型对应不同的可选值
 */
export const PropertyValueOptions = {
  [PropertyTypeEnum.required]: RequiredOptions, // 必填选项：是/否
  [PropertyTypeEnum.displayed]: DisplayedOptions, // 显示选项：显示/隐藏
  [PropertyTypeEnum.inputStatus]: InputStatusOptions, // 输入状态选项：只读/可编辑等
  [PropertyTypeEnum.valueType]: valueTypeOptions, // 值类型选项：根据组件类型而定
};

/**
 * 表格属性的属性值选项映射
 */
export const TablePropertyValueOptions = {
  [TablePropertyTypeEnum.display]: TableDisplayOptions, // 表格显示选项
};

// =============================================================================
// 5. 属性值标签获取函数
// =============================================================================

/**
 * 根据组件类型获取值类型的标签
 * @param value 值类型的值
 * @param type 组件类型（可选）
 * @returns 对应的中文标签
 */
const getValueTypeLabel = (value: any, type?: BaseCompType) => {
  if (type === BaseCompType.Input) {
    return getInputValueTypeLabel(value);
  } else if (type === BaseCompType.DateRange) {
    return getDateRangeValueTypeLabel(value);
  }
  return value;
};

/**
 * 普通属性值标签获取函数映射
 * 用于将属性值转换为对应的中文标签
 */
export const PropertyValueLabels: Record<
  PropertyTypeEnum,
  (value: any, type?: BaseCompType) => string
> = {
  [PropertyTypeEnum.required]: getRequiredLabel, // 获取必填状态标签
  [PropertyTypeEnum.displayed]: getDisplayedLabel, // 获取显示状态标签
  [PropertyTypeEnum.inputStatus]: getInputStatusLabel, // 获取输入状态标签
  [PropertyTypeEnum.valueType]: getValueTypeLabel, // 获取值类型标签
};

/**
 * 表格属性值标签获取函数映射
 */
export const TablePropertyValueLabels: Record<
  TablePropertyTypeEnum,
  (value: any) => string
> = {
  [TablePropertyTypeEnum.display]: getTableDisplayLabel, // 获取表格显示状态标签
};

// =============================================================================
// 6. 工具函数
// =============================================================================

/**
 * 获取普通属性类型的中文标签
 * @param type 属性类型
 * @returns 对应的中文标签
 */
export const getPropertyTypeLabel = (type: string | PropertyTypeEnum) => {
  return PropertyTypeLabels[type as PropertyTypeEnum];
};

/**
 * 获取表格属性类型的中文标签
 * @param type 表格属性类型
 * @returns 对应的中文标签
 */
export const getTablePropertyTypeLabel = (
  type: string | TablePropertyTypeEnum
) => {
  return TablePropertyTypeLabels[type as TablePropertyTypeEnum];
};
