import { AggregateMethodType, configType } from "./type";
import { aggregateMethodFactory } from "./methods";
import { SummaryMethodEnum } from "@/enums/table/SummaryMethodEnum";
import { GroupingAggregateMethodEnum } from "@/enums/table/GroupingAggregateMethodEnum";
import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import {
  mapSummaryRowMethodToAggregateType,
  mapGroupAggregateMethodToAggregateType,
  mapRuleFunctionToAggregateType,
} from "./typeMapping";

export const aggregate = (
  values: configType[],
  type: AggregateMethodType
): number | null => {
  try {
    const method = aggregateMethodFactory(type);
    return method.calculate(values);
  } catch (error) {
    console.error(error);
    return NaN;
  }
};

export const aggregateSummaryRow = (
  values: configType[],
  type: SummaryMethodEnum
): number | null => {
  const aggregateType = mapSummaryRowMethodToAggregateType(type);
  return aggregate(values, aggregateType);
};

export const aggregateGroup = (
  values: configType[],
  type: GroupingAggregateMethodEnum
): number | null => {
  const aggregateType = mapGroupAggregateMethodToAggregateType(type);
  return aggregate(values, aggregateType);
};

export const aggregateRule = (
  values: configType[],
  type: FunctionEnum
): number | null => {
  const aggregateType = mapRuleFunctionToAggregateType(type);
  return aggregate(values, aggregateType);
};
