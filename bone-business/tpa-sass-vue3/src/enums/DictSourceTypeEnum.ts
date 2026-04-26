export enum DictSourceTypeEnum {
  OPTIONS_SET = 1,
  MAIN_DATA = 2,
  ENUM = 3,
}

export const DictSourceTypeOptions = [
  {
    type: DictSourceTypeEnum.OPTIONS_SET,
    name: "选项集",
  },
  {
    type: DictSourceTypeEnum.MAIN_DATA,
    name: "主数据",
  },
  {
    type: DictSourceTypeEnum.ENUM,
    name: "枚举",
  },
];

export const getDictSourceTypeName = (type: DictSourceTypeEnum | number) => {
  return DictSourceTypeOptions.find((item) => item.type === type)?.name;
};
