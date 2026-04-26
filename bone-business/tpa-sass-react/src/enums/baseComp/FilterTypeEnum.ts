/**
 * 筛选类型枚举
 */
export enum FilterTypeEnum {
  NotSupported = 0, // 不支持筛选
  Supported = 1, // 支持筛选
}

/**
 * 筛选类型标签
 */
export const FilterTypeLabels: Record<FilterTypeEnum, string> = {
  [FilterTypeEnum.NotSupported]: "不支持",
  [FilterTypeEnum.Supported]: "支持",
};

/**
 * 筛选类型选项
 */
export const FilterTypeOptions = [
  {
    label: FilterTypeLabels[FilterTypeEnum.NotSupported],
    value: FilterTypeEnum.NotSupported,
  },
  {
    label: FilterTypeLabels[FilterTypeEnum.Supported],
    value: FilterTypeEnum.Supported,
  },
];
