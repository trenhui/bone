// 0通用期间1身份证有效期
export enum DateRangeValueTypeEnum {
  TEXT = 0,
  ID_CARD = 1,
}

export const DateRangeValueTypeLabels: Record<DateRangeValueTypeEnum, string> =
  {
    [DateRangeValueTypeEnum.TEXT]: "通用期间",
    [DateRangeValueTypeEnum.ID_CARD]: "身份证有效期",
  };

export const DateRangeValueTypeOptions = [
  {
    label: DateRangeValueTypeLabels[DateRangeValueTypeEnum.TEXT],
    value: DateRangeValueTypeEnum.TEXT,
  },

  {
    label: DateRangeValueTypeLabels[DateRangeValueTypeEnum.ID_CARD],
    value: DateRangeValueTypeEnum.ID_CARD,
  },
];

export const getDateRangeValueTypeLabel = (value: DateRangeValueTypeEnum) => {
  return DateRangeValueTypeLabels[value];
};
