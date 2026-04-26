/**
 * 分组聚合字段类型
 */
export enum GroupingAggregateTypeEnum {
  GROUP = 1,
  AGGREGATE = 2,
  OTHER = 3,
}

/**
 * 分组聚合字段类型标签
 */
export const GroupingAggregateTypeLabels: Record<
  GroupingAggregateTypeEnum,
  string
> = {
  [GroupingAggregateTypeEnum.GROUP]: "分组字段",
  [GroupingAggregateTypeEnum.AGGREGATE]: "聚合字段",
  [GroupingAggregateTypeEnum.OTHER]: "展示字段",
};

/**
 * 获取分组聚合字段类型标签
 * @param value
 * @returns
 */
export const getGroupingAggregateTypeLabel = (
  value: GroupingAggregateTypeEnum | null | number
) => {
  return value
    ? GroupingAggregateTypeLabels[value as GroupingAggregateTypeEnum]
    : "";
};
