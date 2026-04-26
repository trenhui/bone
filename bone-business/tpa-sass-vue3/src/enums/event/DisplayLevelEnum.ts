/**
 * 展示主题枚举
 */
export enum DisplayLevelEnum {
  default = 0,
  info = 1,
  primary = 2,
  success = 3,
  warning = 4,
  danger = 5,
}

/**
 * 展示主题映射
 */
export const DisplayLevelMap = {
  [DisplayLevelEnum.default]: "default",
  [DisplayLevelEnum.info]: "info",
  [DisplayLevelEnum.primary]: "primary",
  [DisplayLevelEnum.success]: "success",
  [DisplayLevelEnum.warning]: "warning",
  [DisplayLevelEnum.danger]: "danger",
};

export const DisplayLevelColor = {
  [DisplayLevelEnum.default]: "var(--el-color-default)",
  [DisplayLevelEnum.info]: "var(--el-color-info)",
  [DisplayLevelEnum.primary]: "var(--el-color-primary)",
  [DisplayLevelEnum.success]: "var(--el-color-success)",
  [DisplayLevelEnum.warning]: "var(--el-color-warning)",
  [DisplayLevelEnum.danger]: "var(--el-color-danger)",
};

export const DisplayLevelLabels = {
  [DisplayLevelEnum.default]: "默认",
  [DisplayLevelEnum.info]: "信息",
  [DisplayLevelEnum.primary]: "主要",
  [DisplayLevelEnum.success]: "成功",
  [DisplayLevelEnum.warning]: "警告",
  [DisplayLevelEnum.danger]: "危险",
};

export const DisplayLevelOptions = [
  {
    label: DisplayLevelLabels[DisplayLevelEnum.default],
    value: DisplayLevelEnum.default,
    color: DisplayLevelColor[DisplayLevelEnum.default],
  },
  {
    label: DisplayLevelLabels[DisplayLevelEnum.info],
    value: DisplayLevelEnum.info,
    color: DisplayLevelColor[DisplayLevelEnum.info],
  },
  {
    label: DisplayLevelLabels[DisplayLevelEnum.primary],
    value: DisplayLevelEnum.primary,
    color: DisplayLevelColor[DisplayLevelEnum.primary],
  },
  {
    label: DisplayLevelLabels[DisplayLevelEnum.success],
    value: DisplayLevelEnum.success,
    color: DisplayLevelColor[DisplayLevelEnum.success],
  },
  {
    label: DisplayLevelLabels[DisplayLevelEnum.warning],
    value: DisplayLevelEnum.warning,
    color: DisplayLevelColor[DisplayLevelEnum.warning],
  },
  {
    label: DisplayLevelLabels[DisplayLevelEnum.danger],
    value: DisplayLevelEnum.danger,
    color: DisplayLevelColor[DisplayLevelEnum.danger],
  },
];

/**
 * 获取展示主题
 */
export const getDisplayLevel = (type: Number | DisplayLevelEnum) => {
  return DisplayLevelMap[type as DisplayLevelEnum];
};

// 获取展示主题标签
export const getDisplayLevelLabel = (type: Number | DisplayLevelEnum) => {
  return DisplayLevelLabels[type as DisplayLevelEnum];
};
