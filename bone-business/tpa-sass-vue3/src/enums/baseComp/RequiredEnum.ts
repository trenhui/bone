/**
 * 必填枚举
 */
export enum RequiredEnum {
  required = 1,
  notRequired = 0,
}

/**
 * 必填标签
 */
export const RequiredLabels: Record<RequiredEnum, string> = {
  [RequiredEnum.required]: "必填",
  [RequiredEnum.notRequired]: "非必填",
};

/**
 * 必填选项
 */
export const RequiredOptions = [
  {
    label: RequiredLabels[RequiredEnum.required],
    value: RequiredEnum.required,
  },
  {
    label: RequiredLabels[RequiredEnum.notRequired],
    value: RequiredEnum.notRequired,
  },
];

/**
 * 获取必填标签
 */
export const getRequiredLabel = (value: Number | RequiredEnum) => {
  return RequiredLabels[value as RequiredEnum];
};
