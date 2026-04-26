import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { BaseComparator } from "./BaseComparator";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";
import dayjs from "dayjs";

type ConfigType = dayjs.ConfigType;

const parseDate = (value: ConfigType): dayjs.Dayjs | null => {
  return dayjs(value).isValid() ? dayjs(value) : null;
};

const doCompare = (
  sourceValue: ConfigType,
  targetValue: ConfigType
): number | null => {
  const date1 = parseDate(sourceValue);
  const date2 = parseDate(targetValue);

  if (!date1 || !date2) {
    return null;
  }

  return date1.diff(date2);
};

// 二元运算符比较
const binaryOperatorCompare = (
  sourceValue: ConfigType,
  targetValue: ConfigType,
  operator: OperatorEnum
): boolean => {
  const comparisonResult = doCompare(sourceValue, targetValue);

  // 如果比较结果为 null，表示无法比较，返回 false
  if (comparisonResult === null) {
    return false;
  }

  switch (operator) {
    case OperatorEnum.EQ:
      return comparisonResult === 0;
    case OperatorEnum.NEQ:
      return comparisonResult !== 0;
    case OperatorEnum.EARLIER_THAN:
      return comparisonResult < 0;
    case OperatorEnum.LATER_THAN:
      return comparisonResult > 0;
    default:
      return false;
  }
};

// 介于运算符比较
const between = (sourceValue: ConfigType, targetValue: ConfigType) => {
  const match = (targetValue as string).match(
    /([\[\(])\s*([\d\-T:\s]+?)\s*,\s*([\d\-T:\s]+?)\s*([\]\)])/
  );
  if (!match) {
    console.error(`Invalid range format: ${targetValue}`);
    return false;
  }

  const [, leftBracket, minValue, maxValue, rightBracket] = match;
  const minDate = parseDate(minValue);
  const maxDate = parseDate(maxValue);
  const sourceDate = parseDate(sourceValue);

  if (!minDate || !maxDate || !sourceDate) {
    console.error(
      `Invalid date value: sourceValue=${sourceValue}, minValue=${minValue}, maxValue=${maxValue}`
    );
    return false;
  }

  const isGreaterThanMin =
    leftBracket === "("
      ? sourceDate.isAfter(minDate)
      : !sourceDate.isBefore(minDate);
  const isLessThanMax =
    rightBracket === ")"
      ? sourceDate.isBefore(maxDate)
      : !sourceDate.isAfter(maxDate);

  return isGreaterThanMin && isLessThanMax;
};

export class DateTimeComparator extends BaseComparator<ConfigType> {
  compare(
    sourceValue: ConfigType,
    targetValue: ConfigType,
    operator: OperatorEnum
  ): boolean {
    if (!isValidOperator(BaseCompType.DateTime, operator)) {
      return false;
    }

    // 首先处理空操作符和非空操作符的情况
    if (operator === OperatorEnum.NULL) {
      return this.isEmpty(sourceValue);
    }
    if (operator === OperatorEnum.NOT_NULL) {
      return !this.isEmpty(sourceValue);
    }

    // 如果源值为空，其他操作符都返回 false
    if (this.isEmpty(sourceValue)) {
      return false;
    }

    // 处理其他操作符
    switch (operator) {
      case OperatorEnum.EQ:
      case OperatorEnum.NEQ:
      case OperatorEnum.EARLIER_THAN:
      case OperatorEnum.LATER_THAN:
        return binaryOperatorCompare(sourceValue, targetValue, operator);
      case OperatorEnum.BETWEEN:
        return between(sourceValue, targetValue);
      default:
        return false;
    }
  }
}
