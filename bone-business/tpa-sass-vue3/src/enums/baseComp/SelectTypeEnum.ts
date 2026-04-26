/**
 * 选择类型枚举
 */
export enum SelectTypeEnum {
  single = 0,
  multiple = 1,
}

/**
 * 选择类型标签
 */
export const SelectTypeLabels: Record<SelectTypeEnum, string> = {
  [SelectTypeEnum.single]: "单选",
  [SelectTypeEnum.multiple]: "多选",
};

/**
 * 选择类型选项
 */
export const SelectTypeOptions = [
  {
    label: SelectTypeLabels[SelectTypeEnum.single],
    value: SelectTypeEnum.single,
  },
  {
    label: SelectTypeLabels[SelectTypeEnum.multiple],
    value: SelectTypeEnum.multiple,
  },
];
