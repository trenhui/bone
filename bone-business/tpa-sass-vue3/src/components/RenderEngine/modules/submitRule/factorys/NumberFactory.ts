import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import { FunctionTypeEnum } from "@/enums/rule/FunctionTypeEnum";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { BaseFactory } from "./BaseFactory";
import {
  isValidFunction,
  isValidOperator,
} from "@/enums/rule/SubmitRuleMapping";
import Decimal from "decimal.js";

class NumberComparisonError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "NumberComparisonError";
  }
}

const parseDecimal = (value: any): Decimal | null => {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  try {
    const decimal = new Decimal(value.toString());
    if (decimal.isNaN()) {
      return null;
    }
    return decimal;
  } catch (error) {
    return null;
  }
};

const handleBetween = (source: Decimal, targetValue: string): boolean => {
  const regex =
    /^\s*([\[\(])\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*([\]\)])\s*$/;
  const match = targetValue.match(regex);

  if (!match) {
    throw new NumberComparisonError("介于操作的范围格式无效");
  }

  const [, leftBracket, minStr, maxStr, rightBracket] = match;
  const min = parseDecimal(minStr);
  const max = parseDecimal(maxStr);

  if (min === null || max === null) {
    throw new NumberComparisonError("介于操作的范围值无效");
  }

  const isMinInclusive = leftBracket === "[";
  const isMaxInclusive = rightBracket === "]";

  const minComparison = isMinInclusive
    ? source.greaterThanOrEqualTo(min)
    : source.greaterThan(min);
  const maxComparison = isMaxInclusive
    ? source.lessThanOrEqualTo(max)
    : source.lessThan(max);

  return minComparison && maxComparison;
};

export class NumberFactory implements BaseFactory {
  execute(func: FunctionEnum, fields: any[]): number | null {
    if (!isValidFunction(func, FunctionTypeEnum.NUMBER)) {
      throw new NumberComparisonError("数字函数无效");
    }

    const parsedFields = fields.map(parseDecimal);

    if (parsedFields.some((field) => field === null)) {
      throw new NumberComparisonError("无效数字输入");
    }

    const validFields = parsedFields as Decimal[];

    switch (func) {
      case FunctionEnum.SUM:
        return validFields
          .reduce((acc, curr) => acc.plus(curr), new Decimal(0))
          .toNumber();
      case FunctionEnum.SUB:
        return validFields
          .reduce(
            (acc, curr, index) => (index === 0 ? curr : acc.minus(curr)),
            new Decimal(0)
          )
          .toNumber();
      case FunctionEnum.MUL:
        return validFields
          .reduce((acc, curr) => acc.times(curr), new Decimal(1))
          .toNumber();
      case FunctionEnum.DIV:
        //第一个字段为被除数，后续字段为除数，遇到除数为0跳过
        if (validFields.length === 0) {
          throw new NumberComparisonError("除法运算至少需要一个字段");
        }

        return validFields
          .reduce((acc, curr, index) => {
            if (index === 0) {
              return curr; // 第一个字段作为被除数
            }
            if (curr.isZero()) {
              // 遇到除数为0时跳过
              return acc;
            }
            return acc.div(curr);
          }, new Decimal(0))
          .toNumber();
      case FunctionEnum.NONE:
        if (validFields.length === 1) {
          return validFields[0].toNumber();
        }
        throw new NumberComparisonError("当函数为 无 时，目标字段只能有一个");
      default:
        throw new NumberComparisonError("不支持的数字函数");
    }
  }

  compare(sourceValue: any, operator: OperatorEnum, targetValue: any): boolean {
    if (!isValidOperator(operator, FunctionTypeEnum.NUMBER)) {
      throw new NumberComparisonError("无效的数字操作符");
    }

    const source = parseDecimal(sourceValue);
    if (source === null) {
      throw new NumberComparisonError("函数计算后得到值不是有效的数字");
    }

    const target = parseDecimal(targetValue);

    if (operator === OperatorEnum.NEQ) {
      return target === null || !source.equals(target);
    }

    if (operator === OperatorEnum.BETWEEN) {
      if (typeof targetValue !== "string") {
        throw new NumberComparisonError("介于操作需要一个字符串格式的范围");
      }
      return handleBetween(source, targetValue);
    }

    if (target === null) {
      throw new NumberComparisonError("目标值不是有效的数字");
    }

    switch (operator) {
      case OperatorEnum.EQ:
        return source.equals(target);
      case OperatorEnum.GT:
        return source.greaterThan(target);
      case OperatorEnum.GTE:
        return source.greaterThanOrEqualTo(target);
      case OperatorEnum.LT:
        return source.lessThan(target);
      case OperatorEnum.LTE:
        return source.lessThanOrEqualTo(target);
      default:
        throw new NumberComparisonError("不支持的比较操作符");
    }
  }
}
