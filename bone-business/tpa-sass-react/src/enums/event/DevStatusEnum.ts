//准备中，已启用
export enum DevStatusEnum {
  ready = 0,
  done = 1,
}

export const DevStatusLabels = {
  [DevStatusEnum.ready]: "准备中",
  [DevStatusEnum.done]: "已启用",
};

export const DevStatusOptions = [
  { label: DevStatusLabels[DevStatusEnum.ready], value: DevStatusEnum.ready },
  { label: DevStatusLabels[DevStatusEnum.done], value: DevStatusEnum.done },
];

export const getDevStatusLabel = (value: number | DevStatusEnum | null) => {
  if (value === null) {
    return DevStatusLabels[DevStatusEnum.ready];
  }
  return DevStatusLabels[value as DevStatusEnum];
};
