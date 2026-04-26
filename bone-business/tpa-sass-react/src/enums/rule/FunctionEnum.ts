/**
 * 函数枚举
 */
export enum FunctionEnum {
  SUM = "sum",
  SUB = "sub",
  MUL = "mul",
  DIV = "div",
  NONE = "none",
}

/**
 * 函数标签
 */
export const FunctionLabels: Record<FunctionEnum, string> = {
  [FunctionEnum.SUM]: "求和",
  [FunctionEnum.SUB]: "求差",
  [FunctionEnum.MUL]: "相乘",
  [FunctionEnum.DIV]: "相除",
  [FunctionEnum.NONE]: "无",
};

/**
 * 函数选项
 */
export const FunctionOptions: Record<
  FunctionEnum,
  { label: string; value: FunctionEnum }
> = {
  [FunctionEnum.SUM]: {
    label: FunctionLabels[FunctionEnum.SUM],
    value: FunctionEnum.SUM,
  },
  [FunctionEnum.SUB]: {
    label: FunctionLabels[FunctionEnum.SUB],
    value: FunctionEnum.SUB,
  },
  [FunctionEnum.MUL]: {
    label: FunctionLabels[FunctionEnum.MUL],
    value: FunctionEnum.MUL,
  },
  [FunctionEnum.DIV]: {
    label: FunctionLabels[FunctionEnum.DIV],
    value: FunctionEnum.DIV,
  },
  [FunctionEnum.NONE]: {
    label: FunctionLabels[FunctionEnum.NONE],
    value: FunctionEnum.NONE,
  },
};

/**
 * 获取函数标签
 * @param type 函数类型
 * @returns 函数标签
 */
export const getFunctionLabel = (type: string | FunctionEnum) => {
  return FunctionLabels[type as FunctionEnum];
};
