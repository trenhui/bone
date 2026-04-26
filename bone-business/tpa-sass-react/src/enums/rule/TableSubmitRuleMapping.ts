import { TableSubmitRuleType } from "./TableSubmitRuleTypeEnum";
import {
  TableSubmitRuleEnum,
  TableSubmitRuleOptions,
} from "./TableSubmitRuleEnum";
import { BaseCompType } from "../baseComp/BaseCompEnum";
import { FunctionEnum, FunctionOptions } from "./FunctionEnum";
import { OperatorEnum, OperatorOptions } from "./OperatorEnum";

export const TableSubmitRuleTypeValidRules = {
  [TableSubmitRuleType.IN_ROW]: [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE],
  [TableSubmitRuleType.CROSS_TABLE]: [
    TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE,
  ],
};

export const TableSubmitRuleValidFunctions = {
  [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: [
    FunctionEnum.SUM,
    FunctionEnum.SUB,
    FunctionEnum.MUL,
    FunctionEnum.DIV,
  ],
  [TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: [
    FunctionEnum.SUM,
    FunctionEnum.SUB,
    FunctionEnum.MUL,
    FunctionEnum.DIV,
  ],
};

export const TableSubmitRuleValidCompTypes = {
  [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: [
    BaseCompType.InputNum,
    BaseCompType.Input,
  ],
  [TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: [
    BaseCompType.InputNum,
    BaseCompType.Input,
  ],
};

export const TableSubmitRuleValidOperators = {
  [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: [
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
    OperatorEnum.GT,
    OperatorEnum.GTE,
    OperatorEnum.LT,
    OperatorEnum.LTE,
    OperatorEnum.BETWEEN,
  ],
  [TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: [
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
    OperatorEnum.GT,
    OperatorEnum.GTE,
    OperatorEnum.LT,
    OperatorEnum.LTE,
  ],
};

export const getTableSubmitRulesByType = (ruleType: TableSubmitRuleType) => {
  return TableSubmitRuleTypeValidRules[ruleType];
};

export const getTableSubmitRuleOptions = (ruleType: TableSubmitRuleType) => {
  const rules = getTableSubmitRulesByType(ruleType);
  return rules.map((rule) => TableSubmitRuleOptions[rule]);
};

export const getTableSubmitRuleValidFunctions = (rule: TableSubmitRuleEnum) => {
  return TableSubmitRuleValidFunctions[rule];
};

export const getTableSubmitRuleValidFunctionsOptions = (
  rule: TableSubmitRuleEnum
) => {
  const validFunctions = getTableSubmitRuleValidFunctions(rule);
  return validFunctions.map((func: FunctionEnum) => FunctionOptions[func]);
};

export const getTableSubmitRuleValidCompTypes = (rule: TableSubmitRuleEnum) => {
  return TableSubmitRuleValidCompTypes[rule];
};

export const getTableSubmitRuleValidOperators = (rule: TableSubmitRuleEnum) => {
  return TableSubmitRuleValidOperators[rule];
};

export const getTableSubmitRuleValidOperatorsOptions = (
  rule: TableSubmitRuleEnum
) => {
  const validOperators = getTableSubmitRuleValidOperators(rule);
  return validOperators.map(
    (operator: OperatorEnum) => OperatorOptions[operator]
  );
};
