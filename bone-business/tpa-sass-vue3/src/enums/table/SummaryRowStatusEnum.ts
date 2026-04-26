export enum SummaryRowStatusEnum {
  OPENED = 1,
  CLOSED = 0,
}

export const SummaryRowStatusLabels: Record<SummaryRowStatusEnum, string> = {
  [SummaryRowStatusEnum.OPENED]: "打开",
  [SummaryRowStatusEnum.CLOSED]: "关闭",
};

export const SummaryRowStatusOptions = [
  {
    label: SummaryRowStatusLabels[SummaryRowStatusEnum.OPENED],
    value: SummaryRowStatusEnum.OPENED,
  },
  {
    label: SummaryRowStatusLabels[SummaryRowStatusEnum.CLOSED],
    value: SummaryRowStatusEnum.CLOSED,
  },
];
