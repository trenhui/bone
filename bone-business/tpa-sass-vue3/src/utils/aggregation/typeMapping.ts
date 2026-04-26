import { AggregateMethodType } from "./type";
import { SummaryMethodEnum } from "@/enums/table/SummaryMethodEnum";
import { GroupingAggregateMethodEnum } from "@/enums/table/GroupingAggregateMethodEnum";
import { FunctionEnum } from "@/enums/rule/FunctionEnum";

export const mapSummaryRowMethodToAggregateType = (
  type: SummaryMethodEnum
): AggregateMethodType => {
  switch (type) {
    case SummaryMethodEnum.COUNT:
      return AggregateMethodType.COUNT;
    case SummaryMethodEnum.SUM:
      return AggregateMethodType.SUM;
    case SummaryMethodEnum.AVG:
      return AggregateMethodType.AVG;
    default:
      throw new Error(`不支持的汇总行聚合方法: ${type}`);
  }
};

export const mapGroupAggregateMethodToAggregateType = (
  type: GroupingAggregateMethodEnum
): AggregateMethodType => {
  switch (type) {
    case GroupingAggregateMethodEnum.COUNT:
      return AggregateMethodType.COUNT;
    case GroupingAggregateMethodEnum.SUM:
      return AggregateMethodType.SUM;
    case GroupingAggregateMethodEnum.AVG:
      return AggregateMethodType.AVG;
    default:
      throw new Error(`不支持的分组聚合方法: ${type}`);
  }
};

export const mapRuleFunctionToAggregateType = (
  type: FunctionEnum
): AggregateMethodType => {
  switch (type) {
    case FunctionEnum.SUM:
      return AggregateMethodType.SUM;
    case FunctionEnum.SUB:
      return AggregateMethodType.SUB;
    case FunctionEnum.MUL:
      return AggregateMethodType.MUL;
    case FunctionEnum.DIV:
      return AggregateMethodType.DIV;
    default:
      throw new Error(`不支持的规则函数: ${type}`);
  }
};
