import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { BaseComparator } from "./BaseComparator";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";
import dayjs from "dayjs";

export type ConfigType = string | string[] | null | undefined;

const separator = ",";

const MIN_DATE = dayjs("1970-01-01"); // 最早日期
const MAX_DATE = dayjs("9999-12-31"); // 最晚日期

const parseDate = (value: dayjs.ConfigType): dayjs.Dayjs | null => {
  return dayjs(value).isValid() ? dayjs(value) : null;
};

const formatDateRange = (value: ConfigType) => {
  if (!value) {
    return [null, null];
  }

  if (Array.isArray(value)) {
    return value.map((d) => parseDate(d.trim()));
  }

  return value.split(separator).map((d) => parseDate(d.trim()));
};

const equals = (sourceValue: ConfigType, targetValue: ConfigType) => {
  const [start1, end1] = formatDateRange(sourceValue);
  const [start2, end2] = formatDateRange(targetValue);

  //用最大值和最小值来代替无效日期
  const validStart1 = start1 || MIN_DATE;
  const validEnd1 = end1 || MAX_DATE;
  const validStart2 = start2 || MIN_DATE;
  const validEnd2 = end2 || MAX_DATE;

  return validStart1.isSame(validStart2) && validEnd1.isSame(validEnd2);
};

export class DateRangeComparator extends BaseComparator<ConfigType> {
  compare(
    sourceValue: ConfigType,
    targetValue: ConfigType,
    operator: OperatorEnum
  ): boolean {
    if (!isValidOperator(BaseCompType.DateRange, operator)) {
      return false;
    }

    switch (operator) {
      case OperatorEnum.NULL:
        return this.isEmpty(sourceValue);
      case OperatorEnum.NOT_NULL:
        return !this.isEmpty(sourceValue);
      case OperatorEnum.EQ:
        return equals(sourceValue, targetValue);
      case OperatorEnum.NEQ:
        return !equals(sourceValue, targetValue);
      default:
        return false;
    }
  }
}
