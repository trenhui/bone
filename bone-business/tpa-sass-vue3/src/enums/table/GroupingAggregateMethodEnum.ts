export enum GroupingAggregateMethodEnum {
  SUM = "sum",
  AVG = "average",
  COUNT = "count",
}

export const GroupingAggregateMethodLabels: Record<
  GroupingAggregateMethodEnum,
  string
> = {
  [GroupingAggregateMethodEnum.SUM]: "求和",
  [GroupingAggregateMethodEnum.AVG]: "平均值",
  [GroupingAggregateMethodEnum.COUNT]: "计数",
};

export const GroupingAggregateMethodOptions = [
  {
    label: GroupingAggregateMethodLabels[GroupingAggregateMethodEnum.SUM],
    value: GroupingAggregateMethodEnum.SUM,
  },
  {
    label: GroupingAggregateMethodLabels[GroupingAggregateMethodEnum.AVG],
    value: GroupingAggregateMethodEnum.AVG,
  },
  {
    label: GroupingAggregateMethodLabels[GroupingAggregateMethodEnum.COUNT],
    value: GroupingAggregateMethodEnum.COUNT,
  },
];

export const GroupingAggregateMethodDefault = GroupingAggregateMethodEnum.SUM;

export const getGroupingAggregateMethodLabel = (
  value: GroupingAggregateMethodEnum | null | number
) => {
  return value
    ? GroupingAggregateMethodLabels[value as GroupingAggregateMethodEnum]
    : "";
};
