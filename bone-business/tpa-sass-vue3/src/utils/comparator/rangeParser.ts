import { RangeValue, RangeString, CompareValue } from "./types";
import { ValueParser } from "./valueParser";

export class RangeParser {
  /**
   * 解析区间字符串为 RangeValue 对象
   */
  static parse(range: RangeString): RangeValue {
    // 验证格式
    const regex =
      /^[\[\(]([-+]?\d*\.?\d+|\s*null\s*|\s*undefined\s*),([-+]?\d*\.?\d+|\s*null\s*|\s*undefined\s*)[\]\)]$/;
    const match = range.match(regex);

    if (!match) {
      throw new Error("Invalid range format");
    }

    const [, minStr, maxStr] = match;

    // 解析最小值和最大值
    const min = this.parseValue(minStr);
    const max = this.parseValue(maxStr);

    // 验证区间
    if (min != null && max != null && min > max) {
      throw new Error("Min value cannot be greater than max value");
    }

    return {
      min,
      max,
      leftBracket: range[0] as "[" | "(",
      rightBracket: range[range.length - 1] as "]" | ")",
    };
  }

  /**
   * 将 RangeValue 对象转换为字符串
   */
  static stringify(range: RangeValue): string {
    const minStr = range.min ?? "null";
    const maxStr = range.max ?? "null";
    return `${range.leftBracket}${minStr},${maxStr}${range.rightBracket}`;
  }

  /**
   * 检查值是否在区间内
   */
  static isInRange(value: number, range: RangeValue): boolean {
    const minNum = ValueParser.toNumber(range.min);
    const maxNum = ValueParser.toNumber(range.max);

    // 如果区间边界无效，返回 false
    if (minNum === null || maxNum === null) {
      return false;
    }

    const minCompare =
      range.leftBracket === "[" ? value >= minNum : value > minNum;

    const maxCompare =
      range.rightBracket === "]" ? value <= maxNum : value < maxNum;

    return minCompare && maxCompare;
  }

  /**
   * 解析字符串值
   */
  private static parseValue(str: string): CompareValue {
    const trimmed = str.trim();
    if (trimmed === "null" || trimmed === "undefined") {
      return null;
    }
    return ValueParser.toNumber(trimmed);
  }
}
