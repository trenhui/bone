export enum AlignmentTypeEnum {
  Left = 0,
  Center = 1,
  Right = 2,
}

export const AlignmentTypeLabels = {
  [AlignmentTypeEnum.Left]: "左对齐",
  [AlignmentTypeEnum.Center]: "居中对齐",
  [AlignmentTypeEnum.Right]: "右对齐",
};

export const AlignmentTypeOptions = [
  {
    label: AlignmentTypeLabels[AlignmentTypeEnum.Left],
    value: AlignmentTypeEnum.Left,
  },
  {
    label: AlignmentTypeLabels[AlignmentTypeEnum.Center],
    value: AlignmentTypeEnum.Center,
  },
  {
    label: AlignmentTypeLabels[AlignmentTypeEnum.Right],
    value: AlignmentTypeEnum.Right,
  },
];

export const getAlignmentTypeLabel = (type: number | AlignmentTypeEnum) => {
  return AlignmentTypeLabels[type as AlignmentTypeEnum];
};
