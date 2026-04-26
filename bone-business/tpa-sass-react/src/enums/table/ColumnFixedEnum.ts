export enum LeftColumnFixedEnum {
  DISABLE = 0,
  PREV = 1,
  PREV2 = 2,
  PREV3 = 3,
}

export const LeftColumnFixedOptions = [
  { label: "关闭", value: LeftColumnFixedEnum.DISABLE },
  { label: "前一列固定", value: LeftColumnFixedEnum.PREV },
  { label: "前二列固定", value: LeftColumnFixedEnum.PREV2 },
  { label: "前三列固定", value: LeftColumnFixedEnum.PREV3 },
];

export enum RightColumnFixedEnum {
  DISABLE = 0,
  LAST = 1,
  LAST2 = 2,
}

export const RightColumnFixedOptions = [
  { label: "关闭", value: RightColumnFixedEnum.DISABLE },
  { label: "最后一列固定", value: RightColumnFixedEnum.LAST },
  { label: "最后二列固定", value: RightColumnFixedEnum.LAST2 },
];
