export enum SelectLevelEnum {
  TWO = 0,
  THREE = 1,
}

export const SelectLevelLabels = {
  [SelectLevelEnum.TWO]: "二级",
  [SelectLevelEnum.THREE]: "三级",
};

export const SelectLevelOptions = [
  {
    label: SelectLevelLabels[SelectLevelEnum.TWO],
    value: SelectLevelEnum.TWO,
  },
  {
    label: SelectLevelLabels[SelectLevelEnum.THREE],
    value: SelectLevelEnum.THREE,
  },
];
