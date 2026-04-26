import {
  CompareValue,
  CompareOperator,
  CompareOptions,
  CompareResult,
  RangeValue,
  RangeString,
} from "./types";
import { ValueParser } from "./valueParser";
import { RangeParser } from "./rangeParser";

export class NumericComparator {
  /**
   * 比较两个值
   */
  static compare(
    value1: CompareValue,
    value2: CompareValue | RangeValue | RangeString,
    operator: CompareOperator,
    options: CompareOptions = {}
  ): CompareResult {
    try {
      // 处理区间比较
      if (operator === "between") {
        return this.handleBetweenComparison(
          value1,
          value2 as RangeValue | RangeString
        );
      }

      // 转换为数字
      const num1 = ValueParser.toNumber(value1);
      const num2 = ValueParser.toNumber(value2 as CompareValue);

      // 特殊值处理
      if (num1 === null || num2 === null) {
        return this.handleNullComparison(num1, num2, operator);
      }

      // 数值比较
      return this.compareNumbers(num1, num2, operator, options);
    } catch (error) {
      return {
        result: false,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }

  /**
   * 处理 null 值的比较
   */
  private static handleNullComparison(
    value1: number | null,
    value2: number | null,
    operator: CompareOperator
  ): CompareResult {
    switch (operator) {
      case "eq":
        return { result: value1 === value2 };
      case "ne":
        return { result: value1 !== value2 };
      default:
        return {
          result: false,
          error: "Cannot compare null values with operator: " + operator,
        };
    }
  }

  /**
   * 处理区间比较
   */
  private static handleBetweenComparison(
    value: CompareValue,
    range: RangeValue | RangeString
  ): CompareResult {
    try {
      const num = ValueParser.toNumber(value);
      if (num === null) {
        return {
          result: false,
          error: "Cannot compare null value with range",
        };
      }

      const parsedRange =
        typeof range === "string" ? RangeParser.parse(range) : range;

      const result = RangeParser.isInRange(num, parsedRange);

      return { result };
    } catch (error) {
      return {
        result: false,
        error:
          error instanceof Error ? error.message : "Invalid range comparison",
      };
    }
  }

  /**
   * 比较两个数字
   */
  private static compareNumbers(
    num1: number,
    num2: number,
    operator: CompareOperator,
    options: CompareOptions
  ): CompareResult {
    const { precision = 10 } = options;

    switch (operator) {
      case "eq":
        return {
          result: Math.abs(num1 - num2) < Math.pow(10, -precision),
        };
      case "ne":
        return {
          result: Math.abs(num1 - num2) >= Math.pow(10, -precision),
        };
      case "gt":
        return { result: num1 > num2 };
      case "gte":
        return { result: num1 >= num2 };
      case "lt":
        return { result: num1 < num2 };
      case "lte":
        return { result: num1 <= num2 };
      default:
        return {
          result: false,
          error: `Unsupported operator: ${operator}`,
        };
    }
  }
}
