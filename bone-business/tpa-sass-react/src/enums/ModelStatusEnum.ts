export enum ModelStatusEnum {
  active = 1,
  inactive = 0,
}

export const ModelStatusLabels = {
  [ModelStatusEnum.active]: "启用",
  [ModelStatusEnum.inactive]: "未启用",
};

export const ModelStatusOptions = [
  { label: "启用", value: ModelStatusEnum.active },
  { label: "未启用", value: ModelStatusEnum.inactive },
];

export const getModelStatusLabel = (value: number | ModelStatusEnum | null) => {
  if (value === null) {
    return ModelStatusLabels[ModelStatusEnum.inactive];
  }
  return ModelStatusLabels[value as ModelStatusEnum];
};
