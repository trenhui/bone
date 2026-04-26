import { NumericComparator } from "./numericComparator";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import {
  CompareOperator,
  CompareValue,
  CompareResult,
  RangeValue,
  RangeString,
  CompareOptions,
} from "./types";

// 映射对象
const operatorMapping: any = {
  [OperatorEnum.EQ]: "eq",
  [OperatorEnum.NEQ]: "ne",
  [OperatorEnum.GT]: "gt",
  [OperatorEnum.GTE]: "gte",
  [OperatorEnum.LT]: "lt",
  [OperatorEnum.LTE]: "lte",
  [OperatorEnum.BETWEEN]: "between",
};

// 封装的比较函数
export class OperatorComparator {
  /**
   * 使用新操作符进行比较
   */
  static compare(
    value1: CompareValue,
    value2: CompareValue | RangeValue | RangeString,
    operator: OperatorEnum,
    options: CompareOptions = {}
  ): CompareResult {
    // 映射操作符
    const compareOp = this.mapOperator(operator);

    // 调用 NumericComparator 进行比较
    return NumericComparator.compare(value1, value2, compareOp, options);
  }

  /**
   * 映射新操作符到 CompareOperator
   */
  private static mapOperator(operator: OperatorEnum): CompareOperator {
    const mappedOp = operatorMapping[operator];
    if (!mappedOp) {
      throw new Error(`Unsupported operator: ${operator}`);
    }
    return mappedOp;
  }
}
