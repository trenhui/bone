/**
 * 显示枚举
 */
export enum DisplayedEnum {
  show = 1,
  hidden = 0,
}

/**
 * 显示标签
 */
export const DisplayedLabels: Record<DisplayedEnum, string> = {
  [DisplayedEnum.show]: "显示",
  [DisplayedEnum.hidden]: "隐藏",
};

/**
 * 显示选项
 */
export const DisplayedOptions = [
  {
    label: DisplayedLabels[DisplayedEnum.show],
    value: DisplayedEnum.show,
  },
  {
    label: DisplayedLabels[DisplayedEnum.hidden],
    value: DisplayedEnum.hidden,
  },
];

/**
 * 获取显示标签
 */
export const getDisplayedLabel = (value: Number | DisplayedEnum) => {
  return DisplayedLabels[value as DisplayedEnum];
};
