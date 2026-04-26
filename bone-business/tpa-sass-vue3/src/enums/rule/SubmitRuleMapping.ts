import { BaseCompType } from "../baseComp/BaseCompEnum";
import { OperatorEnum, OperatorOptions } from "./OperatorEnum";
import { FunctionEnum } from "./FunctionEnum";
import { FunctionTypeEnum } from "./FunctionTypeEnum";
import { FunctionOptions } from "./FunctionEnum";

/**
 * 函数类型有效组件类型
 */
export const FunctionTypeValidCompTypes = {
  [FunctionTypeEnum.NUMBER]: [BaseCompType.InputNum, BaseCompType.Input],
};

/**
 * 函数类型有效函数
 */
export const FunctionTypeValidFunctions = {
  [FunctionTypeEnum.NUMBER]: [
    FunctionEnum.SUM,
    FunctionEnum.SUB,
    FunctionEnum.MUL,
    FunctionEnum.DIV,
    FunctionEnum.NONE,
  ],
};

/**
 * 函数类型有效操作符
 */
export const FunctionTypeValidOperators = {
  [FunctionTypeEnum.NUMBER]: [
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
    OperatorEnum.GT,
    OperatorEnum.GTE,
    OperatorEnum.LT,
    OperatorEnum.LTE,
    OperatorEnum.BETWEEN,
  ],
};

/**
 * 获取有效组件类型
 * @param type 函数类型
 * @returns 有效组件类型
 */
export const getValidCompTypes = (
  type: string | FunctionTypeEnum
): BaseCompType[] => {
  return FunctionTypeValidCompTypes[type as FunctionTypeEnum];
};

/**
 * 获取有效函数
 * @param type 函数类型
 * @returns 有效函数
 */
export const getValidFunctions = (
  type: string | FunctionTypeEnum
): FunctionEnum[] => {
  return FunctionTypeValidFunctions[type as FunctionTypeEnum];
};

/**
 * 获取有效操作符
 * @param type 函数类型
 * @returns 有效操作符
 */
export const getValidOperators = (
  type: string | FunctionTypeEnum
): OperatorEnum[] => {
  return FunctionTypeValidOperators[type as FunctionTypeEnum];
};

/**
 * 获取函数选项
 * @param functionType 函数类型
 * @returns 函数选项
 */
export const getFunctionOptionsByFunctionType = (
  functionType: string | FunctionTypeEnum
) => {
  const validFunctions = getValidFunctions(functionType);
  return validFunctions.map((func: FunctionEnum) => FunctionOptions[func]);
};

/**
 * 获取操作符选项
 * @param functionType 函数类型
 * @returns 操作符选项
 */
export const getOperatorOptionsByFunctionType = (
  functionType: string | FunctionTypeEnum
) => {
  const validOperators = getValidOperators(functionType);
  return validOperators.map(
    (operator: OperatorEnum) => OperatorOptions[operator]
  );
};

/**
 * 是否有效函数
 * @param functionType 函数类型
 * @returns 是否有效
 */
export const isValidFunction = (
  func: string | FunctionEnum,
  type: string | FunctionTypeEnum
): boolean => {
  return getValidFunctions(type).includes(func as FunctionEnum);
};

/**
 * 是否有效操作符
 * @param operator 操作符
 * @param type 函数类型
 * @returns 是否有效
 */
export const isValidOperator = (
  operator: string | OperatorEnum,
  type: string | FunctionTypeEnum
): boolean => {
  return getValidOperators(type).includes(operator as OperatorEnum);
};
