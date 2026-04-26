import { CompareValue } from "./types";

export class ValueParser {
  /**
   * 将输入值转换为数字
   */
  static toNumber(value: CompareValue): number | null {
    // 处理 null 和 undefined
    if (value == null) {
      return null;
    }

    // 处理字符串
    if (typeof value === "string") {
      // 去除空格
      const trimmed = value.trim();

      // 处理空字符串
      if (trimmed === "") {
        return null;
      }

      // 尝试转换为数字
      const num = Number(trimmed);
      return isNaN(num) ? null : num;
    }

    // 处理数字
    return typeof value === "number" && !isNaN(value) ? value : null;
  }

  /**
   * 比较两个值是否相等（考虑 null/undefined）
   */
  static areEqual(value1: CompareValue, value2: CompareValue): boolean {
    // 如果两个值都是 null 或 undefined，认为它们相等
    if (value1 == null && value2 == null) {
      return true;
    }

    // 转换为数字后比较
    const num1 = this.toNumber(value1);
    const num2 = this.toNumber(value2);

    // 如果转换后都为 null，认为相等
    if (num1 === null && num2 === null) {
      return true;
    }

    // 如果其中一个为 null，认为不相等
    if (num1 === null || num2 === null) {
      return false;
    }

    // 数字比较
    return num1 === num2;
  }
}
