// 可比较的值类型
export type CompareValue = number | string | null | undefined;

// 比较操作符
export type CompareOperator =
  | "eq" // 等于
  | "ne" // 不等于
  | "gt" // 大于
  | "gte" // 大于等于
  | "lt" // 小于
  | "lte" // 小于等于
  | "between"; // 区间

// 区间类型
export type IntervalType = "[" | "(" | "]" | ")";

// 区间值接口
export interface RangeValue {
  min: CompareValue;
  max: CompareValue;
  leftBracket: IntervalType;
  rightBracket: IntervalType;
}

// 区间字符串类型
export type RangeString = `${IntervalType}${string},${string}${IntervalType}`;

// 比较选项
export interface CompareOptions {
  precision?: number; // 精度
}

// 比较结果
export interface CompareResult {
  result: boolean;
  error?: string;
}
