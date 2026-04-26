export const OPERATORS = {
  EQUALS: "eq" as const,
  NOT_EQUALS: "ne" as const,
  GREATER_THAN: "gt" as const,
  GREATER_THAN_OR_EQUALS: "gte" as const,
  LESS_THAN: "lt" as const,
  LESS_THAN_OR_EQUALS: "lte" as const,
  BETWEEN: "between" as const,
} as const;

export const OPERATOR_LABELS = {
  [OPERATORS.EQUALS]: "等于",
  [OPERATORS.NOT_EQUALS]: "不等于",
  [OPERATORS.GREATER_THAN]: "大于",
  [OPERATORS.GREATER_THAN_OR_EQUALS]: "大于等于",
  [OPERATORS.LESS_THAN]: "小于",
  [OPERATORS.LESS_THAN_OR_EQUALS]: "小于等于",
  [OPERATORS.BETWEEN]: "区间",
} as const;
