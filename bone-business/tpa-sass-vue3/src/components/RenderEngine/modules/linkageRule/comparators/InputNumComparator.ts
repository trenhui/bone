import { BaseComparator } from "./BaseComparator";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";
import Decimal from "decimal.js";

type ConfigType = number | string | null | undefined;

const parseDecimal = (value: number | string): Decimal | null => {
  try {
    return new Decimal(value.toString());
  } catch (error) {
    console.error(`Invalid decimal value: ${value}`);
    return null;
  }
};

//二元运算符比较
const binaryOperatorCompare = (
  sourceValue: number | string,
  targetValue: number | string,
  operator: OperatorEnum
) => {
  const dec1 = parseDecimal(sourceValue);
  const dec2 = parseDecimal(targetValue);

  if (dec1 === null || dec2 === null) {
    console.error(
      `Invalid decimal value: sourceValue=${sourceValue}, targetValue=${targetValue}`
    );
    return false;
  }

  switch (operator) {
    case OperatorEnum.EQ:
      return dec1.equals(dec2);
    case OperatorEnum.NEQ:
      return !dec1.equals(dec2);
    case OperatorEnum.GT:
      return dec1.gt(dec2);
    case OperatorEnum.GTE:
      return dec1.gte(dec2);
    case OperatorEnum.LT:
      return dec1.lt(dec2);
    case OperatorEnum.LTE:
      return dec1.lte(dec2);
    default:
      return false;
  }
};

// 介于运算符比较
const between = (
  sourceValue: number | string,
  targetValue: number | string
) => {
  // 正则表达式匹配范围格式, 如: [1,2] 或 (1,2)
  const match = (targetValue as string).match(
    /([\[\(])\s*(-?\d+(\.\d+)?)\s*,\s*(-?\d+(\.\d+)?)\s*([\]\)])/
  );
  if (!match) {
    console.error(`Invalid range format: ${targetValue}`);
    return false;
  }

  const [, leftBracket, minValue, , maxValue, , rightBracket] = match;
  const minDecimal = parseDecimal(minValue);
  const maxDecimal = parseDecimal(maxValue);
  const sourceDecimal = parseDecimal(sourceValue);

  if (minDecimal === null || maxDecimal === null || sourceDecimal === null) {
    console.error(
      `Invalid decimal value: sourceValue=${sourceValue}, minValue=${minValue}, maxValue=${maxValue}`
    );
    return false;
  }

  const isGreaterThanMin =
    leftBracket === "("
      ? sourceDecimal.gt(minDecimal)
      : sourceDecimal.gte(minDecimal);
  const isLessThanMax =
    rightBracket === ")"
      ? sourceDecimal.lt(maxDecimal)
      : sourceDecimal.lte(maxDecimal);

  return isGreaterThanMin && isLessThanMax;
};

export class InputNumComparator extends BaseComparator<ConfigType> {
  compare(
    sourceValue: ConfigType,
    targetValue: ConfigType,
    operator: OperatorEnum
  ): boolean {
    if (!isValidOperator(BaseCompType.InputNum, operator)) {
      return false;
    }

    if (operator === OperatorEnum.NULL) {
      return this.isEmpty(sourceValue);
    }

    if (operator === OperatorEnum.NOT_NULL) {
      return !this.isEmpty(sourceValue);
    }

    if (!sourceValue || !targetValue) {
      return false;
    }

    switch (operator) {
      case OperatorEnum.EQ:
      case OperatorEnum.NEQ:
      case OperatorEnum.GT:
      case OperatorEnum.GTE:
      case OperatorEnum.LT:
      case OperatorEnum.LTE:
        return binaryOperatorCompare(sourceValue, targetValue, operator);
      case OperatorEnum.BETWEEN:
        return between(sourceValue, targetValue);
      default:
        return false;
    }
  }
}
