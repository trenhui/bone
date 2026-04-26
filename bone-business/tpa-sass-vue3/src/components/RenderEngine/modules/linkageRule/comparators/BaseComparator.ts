import { OperatorEnum } from "@/enums/rule/OperatorEnum";

export abstract class BaseComparator<T> {
  abstract compare(
    sourceValue: T,
    targetValue: T,
    operator: OperatorEnum
  ): boolean;

  protected isEmpty(value: T): boolean {
    return (
      value === null ||
      value === undefined ||
      (typeof value === "string" && value.trim() === "")
    );
  }

  protected localeCompare(sourceValue: T, targetValue: T): number {
    return String(sourceValue).localeCompare(String(targetValue), "zh-CN");
  }
}
