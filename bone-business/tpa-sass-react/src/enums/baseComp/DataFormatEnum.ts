/**
 * 数字输入组件的数据格式枚举
 */
export enum DataFormatEnum {
  number = 0,
  percentage = 1,
  money = 2,
}

/**
 * 数据格式标签
 */
export const DataFormatLabels: Record<DataFormatEnum, string> = {
  [DataFormatEnum.number]: "数值",
  [DataFormatEnum.percentage]: "百分比",
  [DataFormatEnum.money]: "金额",
};

/**
 * 数据格式选项
 */
export const DataFormatOptions = [
  {
    label: DataFormatLabels[DataFormatEnum.number],
    value: DataFormatEnum.number,
  },
  {
    label: DataFormatLabels[DataFormatEnum.percentage],
    value: DataFormatEnum.percentage,
  },
  {
    label: DataFormatLabels[DataFormatEnum.money],
    value: DataFormatEnum.money,
  },
];
