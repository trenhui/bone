export enum SummaryMethodEnum {
  SUM = "sum",
  AVG = "average",
  COUNT = "count",
}

export const SummaryMethodLabels: Record<SummaryMethodEnum, string> = {
  [SummaryMethodEnum.SUM]: "求和",
  [SummaryMethodEnum.AVG]: "平均值",
  [SummaryMethodEnum.COUNT]: "计数",
};

export const SummaryMethodOptions = [
  {
    label: SummaryMethodLabels[SummaryMethodEnum.SUM],
    value: SummaryMethodEnum.SUM,
  },
  {
    label: SummaryMethodLabels[SummaryMethodEnum.AVG],
    value: SummaryMethodEnum.AVG,
  },
  {
    label: SummaryMethodLabels[SummaryMethodEnum.COUNT],
    value: SummaryMethodEnum.COUNT,
  },
];

export const SummaryMethodDefault = SummaryMethodEnum.SUM;

export const getSummaryMethodLabel = (
  value: SummaryMethodEnum | null | number
) => {
  return value ? SummaryMethodLabels[value as SummaryMethodEnum] : "";
};
