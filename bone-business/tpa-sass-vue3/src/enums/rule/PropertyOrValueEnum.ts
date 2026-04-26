/**
 * 字段动态规则中属性或值枚举
 */
export enum PropertyOrValueEnum {
  property = "property",
  value = "value",
}

/**
 * 属性或值标签
 */
export const PropertyOrValueLabels: Record<PropertyOrValueEnum, string> = {
  [PropertyOrValueEnum.property]: "属性",
  [PropertyOrValueEnum.value]: "值",
};

/**
 * 属性或值选项
 */
export const PropertyOrValueOptions = [
  {
    label: PropertyOrValueLabels[PropertyOrValueEnum.property],
    value: PropertyOrValueEnum.property,
  },
  {
    label: PropertyOrValueLabels[PropertyOrValueEnum.value],
    value: PropertyOrValueEnum.value,
  },
];

/**
 * 获取属性或值标签
 */
export const getPropertyOrValueLabel = (
  value: string | PropertyOrValueEnum
) => {
  return PropertyOrValueLabels[value as PropertyOrValueEnum];
};
