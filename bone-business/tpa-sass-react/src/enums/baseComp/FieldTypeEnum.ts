/**
 * 字段类型枚举
 */
export enum FieldTypeEnum {
  BASE = 1,
  EXCLUSIVE = 2,
}

/**
 * 字段类型标签
 */
export const FieldTypeNames: Record<FieldTypeEnum, string> = {
  [FieldTypeEnum.BASE]: "基础字段",
  [FieldTypeEnum.EXCLUSIVE]: "专属字段",
};
