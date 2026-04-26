export enum RuleVerifyTypeEnum {
  /**
   * 强校验，阻止作业流程
   */
  STRONG_VERIFY = 0,
  /**
   * 仅提示，可跳过继续作业
   */
  ONLY_PROMPT = 1,
}

export const RuleVerifyTypeLabelMap = {
  [RuleVerifyTypeEnum.STRONG_VERIFY]: "强校验，阻止作业流程",
  [RuleVerifyTypeEnum.ONLY_PROMPT]: "仅提示，可跳过继续作业",
};

export const RuleVerifyTypeShortLabelMap = {
  [RuleVerifyTypeEnum.STRONG_VERIFY]: "强制",
  [RuleVerifyTypeEnum.ONLY_PROMPT]: "提示",
};

export const RuleVerifyTypeOptions = [
  {
    label: RuleVerifyTypeLabelMap[RuleVerifyTypeEnum.STRONG_VERIFY],
    value: RuleVerifyTypeEnum.STRONG_VERIFY,
  },
  {
    label: RuleVerifyTypeLabelMap[RuleVerifyTypeEnum.ONLY_PROMPT],
    value: RuleVerifyTypeEnum.ONLY_PROMPT,
  },
];

export const getRuleVerifyTypeLabel = (value: number) => {
  return RuleVerifyTypeLabelMap[value as RuleVerifyTypeEnum];
};

export const getRuleVerifyTypeShortLabel = (value: number) => {
  return RuleVerifyTypeShortLabelMap[value as RuleVerifyTypeEnum];
};
