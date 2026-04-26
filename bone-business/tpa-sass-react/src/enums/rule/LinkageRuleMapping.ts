import { BaseCompType } from "../baseComp/BaseCompEnum";
import { OperatorEnum, OperatorOptions } from "./OperatorEnum";

/**
 * 组件类型有效操作符
 */
export const ComponentTypeValidOperators = {
  [BaseCompType.Input]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.IN,
    OperatorEnum.NOT_IN,
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
  ],
  [BaseCompType.InputNum]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.EQ,
    OperatorEnum.GT,
    OperatorEnum.GTE,
    OperatorEnum.LT,
    OperatorEnum.LTE,
    OperatorEnum.BETWEEN,
  ],
  [BaseCompType.SelectDrop]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.IN,
    OperatorEnum.NOT_IN,
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
  ],
  [BaseCompType.SelectCtrl]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.IN,
    OperatorEnum.NOT_IN,
    OperatorEnum.EQ,
    OperatorEnum.NEQ,
  ],
  [BaseCompType.DateTime]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.EQ,
    OperatorEnum.EARLIER_THAN,
    OperatorEnum.LATER_THAN,
    OperatorEnum.BETWEEN,
  ],
  [BaseCompType.DateRange]: [
    OperatorEnum.NULL,
    OperatorEnum.NOT_NULL,
    OperatorEnum.EQ,
  ],
};

/**
 * 获取组件类型有效操作符
 * @param type 组件类型
 * @returns 有效操作符
 */
export const getOperatorOptionsByCompType = (type: string | BaseCompType) => {
  const validOperators = ComponentTypeValidOperators[type as BaseCompType];
  return validOperators.map((operator) => OperatorOptions[operator]);
};

/**
 * 验证操作符是否有效
 * @param type 组件类型
 * @param operator 操作符
 * @returns 操作符是否有效
 */
export const isValidOperator = (
  type: string | BaseCompType,
  operator: string | OperatorEnum
): boolean => {
  return ComponentTypeValidOperators[type as BaseCompType].includes(
    operator as OperatorEnum
  );
};
