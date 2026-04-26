export enum GenericOrExclusiveEnum {
  generic = 0,
  exclusive = 1,
}

export const GenericOrExclusiveLabels = {
  [GenericOrExclusiveEnum.generic]: "通用规则",
  [GenericOrExclusiveEnum.exclusive]: "专属规则",
};

export const getGenericOrExclusiveLabel = (
  value: number | GenericOrExclusiveEnum
) => {
  return GenericOrExclusiveLabels[value as GenericOrExclusiveEnum];
};
