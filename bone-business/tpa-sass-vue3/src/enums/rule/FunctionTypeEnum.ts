/**
 * 函数类型枚举
 */
export enum FunctionTypeEnum {
  NUMBER = "number",
}

/**
 * 函数类型标签
 */
export const FunctionTypeLabels: Record<FunctionTypeEnum, string> = {
  [FunctionTypeEnum.NUMBER]: "数字函数类型",
};

/**
 * 获取函数类型标签
 * @param functionType 函数类型
 * @returns 函数类型标签
 */
export const getFunctionTypeLabel = (
  functionType: string | FunctionTypeEnum
) => {
  return FunctionTypeLabels[functionType as FunctionTypeEnum];
};
