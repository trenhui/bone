export enum OperationEnabledEnum {
  ENABLED = 1,
  DISABLED = 0,
}

export const OperationEnabledLabels = {
  [OperationEnabledEnum.ENABLED]: "开启",
  [OperationEnabledEnum.DISABLED]: "关闭",
};

export const OperationEnabledOptions = [
  {
    label: OperationEnabledLabels[OperationEnabledEnum.ENABLED],
    value: OperationEnabledEnum.ENABLED,
  },
  {
    label: OperationEnabledLabels[OperationEnabledEnum.DISABLED],
    value: OperationEnabledEnum.DISABLED,
  },
];
