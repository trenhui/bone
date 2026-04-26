// 展示类型
export enum DisplayTypeEnum {
  BUTTON = 1,
  LINK = 2,
}

// 展示类型标签
export const DisplayTypeLabels = {
  [DisplayTypeEnum.BUTTON]: "按钮",
  [DisplayTypeEnum.LINK]: "文字链接",
};

// 展示类型选项
export const DisplayTypeOptions = [
  {
    label: DisplayTypeLabels[DisplayTypeEnum.BUTTON],
    value: DisplayTypeEnum.BUTTON,
  },
  {
    label: DisplayTypeLabels[DisplayTypeEnum.LINK],
    value: DisplayTypeEnum.LINK,
  },
];

// 获取展示类型标签
export const getDisplayTypeLabel = (value: number | DisplayTypeEnum) => {
  return DisplayTypeLabels[value as DisplayTypeEnum];
};
