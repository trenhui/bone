export enum OperationFixedEnum {
  FIXED = 1,
  UNFIXED = 0,
}

export const OperationFixedLabels = {
  [OperationFixedEnum.FIXED]: "固定",
  [OperationFixedEnum.UNFIXED]: "不固定",
};

export const OperationFixedOptions = [
  {
    label: OperationFixedLabels[OperationFixedEnum.FIXED],
    value: OperationFixedEnum.FIXED,
  },
  {
    label: OperationFixedLabels[OperationFixedEnum.UNFIXED],
    value: OperationFixedEnum.UNFIXED,
  },
];
