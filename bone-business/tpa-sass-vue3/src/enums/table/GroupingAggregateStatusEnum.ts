/**
 * 分组聚合状态枚举
 */
export enum GroupingAggregateStatusEnum {
  OPENED = 1,
  CLOSED = 0,
}

/**
 * 分组聚合状态标签
 */
export const GroupingAggregateStatusLabels: Record<
  GroupingAggregateStatusEnum,
  string
> = {
  [GroupingAggregateStatusEnum.OPENED]: "打开",
  [GroupingAggregateStatusEnum.CLOSED]: "关闭",
};

/**
 * 分组聚合状态选项
 */
export const GroupingAggregateStatusOptions = [
  {
    label: GroupingAggregateStatusLabels[GroupingAggregateStatusEnum.OPENED],
    value: GroupingAggregateStatusEnum.OPENED,
  },
  {
    label: GroupingAggregateStatusLabels[GroupingAggregateStatusEnum.CLOSED],
    value: GroupingAggregateStatusEnum.CLOSED,
  },
];
