/**
 * 输入状态枚举
 */
export enum InputStatusEnum {
  editable = 0,
  disabled = 1,
}

/**
 * 输入状态标签
 */
export const InputStatusLabels: Record<InputStatusEnum, string> = {
  [InputStatusEnum.editable]: "可编辑",
  [InputStatusEnum.disabled]: "只读",
};

/**
 * 输入状态选项
 */
export const InputStatusOptions = [
  {
    label: InputStatusLabels[InputStatusEnum.editable],
    value: InputStatusEnum.editable,
  },
  {
    label: InputStatusLabels[InputStatusEnum.disabled],
    value: InputStatusEnum.disabled,
  },
];

export const getInputStatusLabel = (value: Number | InputStatusEnum) => {
  return InputStatusLabels[value as InputStatusEnum];
};
