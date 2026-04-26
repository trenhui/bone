import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { BaseComparator } from "./BaseComparator";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";

type ConfigType = string | string[] | null | undefined;

const separator = ",";

const toSet = (value: string | string[]) => {
  if (Array.isArray(value)) {
    return new Set(value);
  }
  return new Set(value.split(separator).map((item) => item.trim()));
};

const equals = (
  sourceValue: string | string[],
  targetValue: string | string[]
) => {
  //考虑多选的情况
  if (sourceValue.includes(separator) || targetValue.includes(separator)) {
    const set1 = toSet(sourceValue);
    const set2 = toSet(targetValue);

    if (set1.size !== set2.size) {
      return false;
    }
    for (const item of set1) {
      if (!set2.has(item)) {
        return false;
      }
    }
    return true;
  }

  //考虑单选的情况
  return sourceValue === targetValue;
};

const contains = (
  sourceValue: string | string[],
  targetValue: string | string[]
) => {
  const sourceSet = toSet(sourceValue);
  const targetSet = toSet(targetValue);

  // 检查source中是否有任意一个元素在target中
  for (const item of sourceSet) {
    if (targetSet.has(item)) {
      return true;
    }
  }

  return false;
};

export class SelectCtrlComparator extends BaseComparator<ConfigType> {
  compare(
    sourceValue: ConfigType,
    targetValue: ConfigType,
    operator: OperatorEnum
  ): boolean {
    if (!isValidOperator(BaseCompType.SelectCtrl, operator)) {
      return false;
    }

    if (operator === OperatorEnum.NULL) {
      return this.isEmpty(sourceValue);
    }

    if (operator === OperatorEnum.NOT_NULL) {
      return !this.isEmpty(sourceValue);
    }

    if (!sourceValue && operator === OperatorEnum.NOT_IN) {
      return true;
    }

    if (!sourceValue || !targetValue) {
      return false;
    }

    switch (operator) {
      case OperatorEnum.EQ:
        return equals(sourceValue, targetValue);
      case OperatorEnum.NEQ:
        return !equals(sourceValue, targetValue);
      case OperatorEnum.IN:
        return contains(sourceValue, targetValue);
      case OperatorEnum.NOT_IN:
        return !contains(sourceValue, targetValue);
      default:
        return false;
    }
  }
}
