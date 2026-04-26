/**
 * 日期格式枚举
 */
export enum DateFormatEnum {
  YYYY = 1,
  YYYYMM = 2,
  YYYYMMDD = 3,
  YYYYMMDDHHMM = 4,
  YYYYMMDDHHMMSS = 5,
}

/**
 * 日期格式映射
 */
export const DateFormatMap: Record<DateFormatEnum, string> = {
  [DateFormatEnum.YYYY]: "YYYY",
  [DateFormatEnum.YYYYMM]: "YYYY-MM",
  [DateFormatEnum.YYYYMMDD]: "YYYY-MM-DD",
  [DateFormatEnum.YYYYMMDDHHMM]: "YYYY-MM-DD HH:mm",
  [DateFormatEnum.YYYYMMDDHHMMSS]: "YYYY-MM-DD HH:mm:ss",
};

/**
 * 日期时间组件类型映射
 */
export const DateTimeTypeMap: Record<DateFormatEnum, string> = {
  [DateFormatEnum.YYYY]: "year",
  [DateFormatEnum.YYYYMM]: "month",
  [DateFormatEnum.YYYYMMDD]: "date",
  [DateFormatEnum.YYYYMMDDHHMM]: "datetime",
  [DateFormatEnum.YYYYMMDDHHMMSS]: "datetime",
};

/**
 * 日期期间组件类型映射
 */
export const DateRangeTypeMap: Record<DateFormatEnum, string> = {
  [DateFormatEnum.YYYY]: "yearrange",
  [DateFormatEnum.YYYYMM]: "monthrange",
  [DateFormatEnum.YYYYMMDD]: "daterange",
  [DateFormatEnum.YYYYMMDDHHMM]: "datetimerange",
  [DateFormatEnum.YYYYMMDDHHMMSS]: "datetimerange",
};

/**
 * 日期格式选项
 */
export const DateFormatTypeOptions = [
  { label: "年", value: DateFormatEnum.YYYY },
  { label: "年-月", value: DateFormatEnum.YYYYMM },
  { label: "年-月-日", value: DateFormatEnum.YYYYMMDD },
  { label: "年-月-日 时:分", value: DateFormatEnum.YYYYMMDDHHMM },
  { label: "年-月-日 时:分:秒", value: DateFormatEnum.YYYYMMDDHHMMSS },
];

/**
 * 验证并获取有效的日期格式
 */
const getValidDateFormat = (dateFormatType: any): DateFormatEnum => {
  const defaultFormat = DateFormatEnum.YYYYMMDD;
  const isValidFormat = Object.values(DateFormatEnum).includes(dateFormatType);
  return isValidFormat ? dateFormatType : defaultFormat;
};

/**
 * 获取日期时间组件类型
 */
export const getDateTimeType = (dateFormatType: any): string => {
  const validFormat = getValidDateFormat(dateFormatType);
  return DateTimeTypeMap[validFormat];
};

/**
 * 获取日期期间组件类型
 */
export const getDateRangeType = (dateFormatType: any): string => {
  const validFormat = getValidDateFormat(dateFormatType);
  return DateRangeTypeMap[validFormat];
};

/**
 * 获取日期时间格式
 */
export const getDateFormat = (dateFormatType: any): string => {
  const validFormat = getValidDateFormat(dateFormatType);
  return DateFormatMap[validFormat];
};
