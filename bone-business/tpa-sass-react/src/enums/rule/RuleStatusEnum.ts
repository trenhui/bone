export enum RuleStatusEnum {
  active = 1,
  inactive = 0,
}

export const RuleStatusLabels = {
  [RuleStatusEnum.active]: "启用",
  [RuleStatusEnum.inactive]: "未启用",
};

export const RuleStatusOptions = [
  { label: "启用", value: RuleStatusEnum.active },
  { label: "未启用", value: RuleStatusEnum.inactive },
];

export const getRuleStatusLabel = (value: number | RuleStatusEnum) => {
  return RuleStatusLabels[value as RuleStatusEnum];
};
