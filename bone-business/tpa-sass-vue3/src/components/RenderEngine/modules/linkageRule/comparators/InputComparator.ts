import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { BaseComparator } from "./BaseComparator";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";

type ConfigType = string | null | undefined;

const contains = (source: ConfigType, target: ConfigType): boolean => {
  if (!source || !target) {
    return false;
  }

  return source.includes(target);
};

export class InputComparator extends BaseComparator<ConfigType> {
  compare(
    sourceValue: ConfigType,
    targetValue: ConfigType,
    operator: OperatorEnum
  ): boolean {
    if (!isValidOperator(BaseCompType.Input, operator)) {
      return false;
    }

    switch (operator) {
      case OperatorEnum.NULL:
        return this.isEmpty(sourceValue);
      case OperatorEnum.NOT_NULL:
        return !this.isEmpty(sourceValue);
      case OperatorEnum.EQ:
        return this.localeCompare(sourceValue, targetValue) === 0;
      case OperatorEnum.NEQ:
        return this.localeCompare(sourceValue, targetValue) !== 0;
      case OperatorEnum.IN:
        return contains(sourceValue, targetValue);
      case OperatorEnum.NOT_IN:
        return !contains(sourceValue, targetValue);
      default:
        return false;
    }
  }
}
