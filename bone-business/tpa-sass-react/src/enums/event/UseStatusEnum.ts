export enum UseStatusEnum {
  use = 1,
  unuse = 0,
}

export const UseStatusLabels = {
  [UseStatusEnum.use]: "使用中",
  [UseStatusEnum.unuse]: "未使用",
};

export const UseStatusOptions = [
  { label: UseStatusLabels[UseStatusEnum.use], value: UseStatusEnum.use },
  { label: UseStatusLabels[UseStatusEnum.unuse], value: UseStatusEnum.unuse },
];

export const getUseStatusLabel = (value: number | UseStatusEnum | null) => {
  if (value === null) {
    return UseStatusLabels[UseStatusEnum.unuse];
  }
  return UseStatusLabels[value as UseStatusEnum];
};
