export enum ClaimStageEnum {
  INIT = "INIT",

  SIGNING = "SIGNING",

  SUBMITTING = "SUBMITTING",

  INSPECTION = "INSPECTION",

  AUDITING = "AUDITING",

  REVIEWING = "REVIEWING",
}

export const ClaimStageLabel = {
  [ClaimStageEnum.INIT]: "初始化阶段",
  [ClaimStageEnum.SIGNING]: "签收阶段",
  [ClaimStageEnum.SUBMITTING]: "录入阶段",
  [ClaimStageEnum.INSPECTION]: "质检阶段",
  [ClaimStageEnum.AUDITING]: "审核阶段",
  [ClaimStageEnum.REVIEWING]: "复核阶段",
};

export const ClaimStageOptions = [
  {
    label: ClaimStageLabel[ClaimStageEnum.INIT],
    value: ClaimStageEnum.INIT,
  },
  {
    label: ClaimStageLabel[ClaimStageEnum.SIGNING],
    value: ClaimStageEnum.SIGNING,
  },
  {
    label: ClaimStageLabel[ClaimStageEnum.SUBMITTING],
    value: ClaimStageEnum.SUBMITTING,
  },
  {
    label: ClaimStageLabel[ClaimStageEnum.INSPECTION],
    value: ClaimStageEnum.INSPECTION,
  },
  {
    label: ClaimStageLabel[ClaimStageEnum.AUDITING],
    value: ClaimStageEnum.AUDITING,
  },
  {
    label: ClaimStageLabel[ClaimStageEnum.REVIEWING],
    value: ClaimStageEnum.REVIEWING,
  },
];

export const getClaimStageLabel = (stage: ClaimStageEnum | string) => {
  return ClaimStageLabel[stage as ClaimStageEnum];
};
