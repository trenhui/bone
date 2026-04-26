import {
  TableLinkageRuleEnum,
  TableLinkageRuleOptions,
} from "./TableLinkageRuleEnum";
import { FunctionEnum, FunctionOptions } from "./FunctionEnum";
import { BaseCompType, BaseCompTypeOptions } from "../baseComp/BaseCompEnum";
import { TableLinkageRuleType } from "./TableLinkageRuleTypeEnum";

export const TableLinkageRuleTypeValidRules = {
  [TableLinkageRuleType.IN_ROW]: [
    TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE,
  ],
  [TableLinkageRuleType.CROSS_TABLE]: [
    TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE,
  ],
};

export const TableLinkageRuleValidFunctions = {
  [TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: [
    FunctionEnum.SUM,
    FunctionEnum.SUB,
    FunctionEnum.MUL,
    FunctionEnum.DIV,
  ],
  [TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: [
    FunctionEnum.SUM,
    FunctionEnum.SUB,
    FunctionEnum.MUL,
    FunctionEnum.DIV,
  ],
};

export const TableLinkageRuleValidCompTypes = {
  [TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: [
    BaseCompType.InputNum,
    BaseCompType.Input,
  ],
  [TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: [
    BaseCompType.InputNum,
    BaseCompType.Input,
  ],
};

export const getTableLinkageRulesByType = (ruleType: TableLinkageRuleType) => {
  return TableLinkageRuleTypeValidRules[ruleType];
};

export const getTableLinkageRuleOptions = (ruleType: TableLinkageRuleType) => {
  const rules = getTableLinkageRulesByType(ruleType);
  return rules.map((rule) => TableLinkageRuleOptions[rule]);
};

export const getTableLinkageRuleValidFunctions = (
  rule: TableLinkageRuleEnum
) => {
  return TableLinkageRuleValidFunctions[rule];
};

export const getTableLinkageRuleValidFunctionsOptions = (
  rule: TableLinkageRuleEnum
) => {
  const validFunctions = getTableLinkageRuleValidFunctions(rule);
  return validFunctions.map((func: FunctionEnum) => FunctionOptions[func]);
};

export const getTableLinkageRuleValidCompTypes = (
  rule: TableLinkageRuleEnum
) => {
  return TableLinkageRuleValidCompTypes[rule];
};
