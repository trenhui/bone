/**
 * 显示枚举
 */
export enum TableDisplayEnum {
  show = "show",
  hidden = "hidden",
}

/**
 * 显示标签
 */
export const TableDisplayLabels: Record<TableDisplayEnum, string> = {
  [TableDisplayEnum.show]: "显示",
  [TableDisplayEnum.hidden]: "隐藏",
};

/**
 * 显示选项
 */
export const TableDisplayOptions = [
  {
    label: TableDisplayLabels[TableDisplayEnum.show],
    value: TableDisplayEnum.show,
  },
  {
    label: TableDisplayLabels[TableDisplayEnum.hidden],
    value: TableDisplayEnum.hidden,
  },
];

/**
 * 获取显示标签
 */
export const getTableDisplayLabel = (value: Number | TableDisplayEnum) => {
  return TableDisplayLabels[value as TableDisplayEnum];
};
