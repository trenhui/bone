/**
 * 规则操作符枚举
 */
export enum OperatorEnum {
  NULL = "null",
  NOT_NULL = "notNull",
  IN = "in",
  NOT_IN = "notIn",
  EQ = "eq",
  NEQ = "neq",
  GT = "gt",
  GTE = "gte",
  LT = "lt",
  LTE = "lte",
  BETWEEN = "between",
  EARLIER_THAN = "earlierThan",
  LATER_THAN = "laterThan",
}

/**
 * 操作符标签
 */
export const OperatorLabels: Record<OperatorEnum, string> = {
  [OperatorEnum.NULL]: "为空",
  [OperatorEnum.NOT_NULL]: "不为空",
  [OperatorEnum.IN]: "包含",
  [OperatorEnum.NOT_IN]: "不包含",
  [OperatorEnum.EQ]: "等于",
  [OperatorEnum.NEQ]: "不等于",
  [OperatorEnum.GT]: "大于",
  [OperatorEnum.GTE]: "大于等于",
  [OperatorEnum.LT]: "小于",
  [OperatorEnum.LTE]: "小于等于",
  [OperatorEnum.BETWEEN]: "介于",
  [OperatorEnum.EARLIER_THAN]: "早于",
  [OperatorEnum.LATER_THAN]: "晚于",
};

/**
 * 操作符选项
 */
export const OperatorOptions = {
  [OperatorEnum.NULL]: {
    label: OperatorLabels[OperatorEnum.NULL],
    value: OperatorEnum.NULL,
  },

  [OperatorEnum.NOT_NULL]: {
    label: OperatorLabels[OperatorEnum.NOT_NULL],
    value: OperatorEnum.NOT_NULL,
  },

  [OperatorEnum.IN]: {
    label: OperatorLabels[OperatorEnum.IN],
    value: OperatorEnum.IN,
  },

  [OperatorEnum.NOT_IN]: {
    label: OperatorLabels[OperatorEnum.NOT_IN],
    value: OperatorEnum.NOT_IN,
  },

  [OperatorEnum.EQ]: {
    label: OperatorLabels[OperatorEnum.EQ],
    value: OperatorEnum.EQ,
  },

  [OperatorEnum.NEQ]: {
    label: OperatorLabels[OperatorEnum.NEQ],
    value: OperatorEnum.NEQ,
  },

  [OperatorEnum.GT]: {
    label: OperatorLabels[OperatorEnum.GT],
    value: OperatorEnum.GT,
  },

  [OperatorEnum.GTE]: {
    label: OperatorLabels[OperatorEnum.GTE],
    value: OperatorEnum.GTE,
  },

  [OperatorEnum.LT]: {
    label: OperatorLabels[OperatorEnum.LT],
    value: OperatorEnum.LT,
  },

  [OperatorEnum.LTE]: {
    label: OperatorLabels[OperatorEnum.LTE],
    value: OperatorEnum.LTE,
  },

  [OperatorEnum.BETWEEN]: {
    label: OperatorLabels[OperatorEnum.BETWEEN],
    value: OperatorEnum.BETWEEN,
  },

  [OperatorEnum.EARLIER_THAN]: {
    label: OperatorLabels[OperatorEnum.EARLIER_THAN],
    value: OperatorEnum.EARLIER_THAN,
  },

  [OperatorEnum.LATER_THAN]: {
    label: OperatorLabels[OperatorEnum.LATER_THAN],
    value: OperatorEnum.LATER_THAN,
  },
};

/**
 * 获取操作符标签
 * @param operator 操作符
 * @returns 操作符标签
 */
export const getOperatorLabel = (operator: string | OperatorEnum): string => {
  return OperatorLabels[operator as OperatorEnum];
};

/**
 * 判断是否为单目操作符
 * @param operator 操作符
 * @returns 是否为单目操作符
 */
export const isUnaryOperator = (operator: string | OperatorEnum): boolean => {
  return operator === OperatorEnum.NULL || operator === OperatorEnum.NOT_NULL;
};
