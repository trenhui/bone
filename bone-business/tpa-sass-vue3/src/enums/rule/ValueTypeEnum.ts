/**
 * 值类型枚举
 */
export enum ValueTypeEnum {
  fixed = "fixed",
  dynamic = "dynamic",
}

/**
 * 值类型标签
 */
export const ValueTypeLabels: Record<ValueTypeEnum, string> = {
  [ValueTypeEnum.fixed]: "固定值",
  [ValueTypeEnum.dynamic]: "动态值",
};

/**
 * 值类型选项
 */
export const ValueTypeOptions = Object.values(ValueTypeEnum).map((type) => ({
  label: ValueTypeLabels[type],
  value: type,
}));

/**
 * 获取值类型标签
 */
export const getValueTypeLabel = (type: string | ValueTypeEnum) => {
  return ValueTypeLabels[type as ValueTypeEnum];
};
