// 0：通用文本，1：手机号码，2：身份证号，3：联系方式
export enum InputValueTypeEnum {
  TEXT = 0,
  PHONE = 1,
  ID_CARD = 2,
  CONTACT = 3,
}

export const InputValueTypeLabels: Record<InputValueTypeEnum, string> = {
  [InputValueTypeEnum.TEXT]: "通用文本",
  [InputValueTypeEnum.PHONE]: "手机号码",
  [InputValueTypeEnum.ID_CARD]: "身份证号",
  [InputValueTypeEnum.CONTACT]: "联系方式",
};

export const InputValueTypeOptions = [
  {
    label: InputValueTypeLabels[InputValueTypeEnum.TEXT],
    value: InputValueTypeEnum.TEXT,
  },
  {
    label: InputValueTypeLabels[InputValueTypeEnum.PHONE],
    value: InputValueTypeEnum.PHONE,
  },
  {
    label: InputValueTypeLabels[InputValueTypeEnum.ID_CARD],
    value: InputValueTypeEnum.ID_CARD,
  },
  {
    label: InputValueTypeLabels[InputValueTypeEnum.CONTACT],
    value: InputValueTypeEnum.CONTACT,
  },
];

export const getInputValueTypeLabel = (value: InputValueTypeEnum) => {
  return InputValueTypeLabels[value];
};
