// 1:签收,2:初审,3:录入,4:质检,5:审核,6:复核
export enum ProcessTypeEnum {
  SIGN = 1,
  PRECHECK = 2,
  ENTRY = 3,
  QUALITY_CHECK = 4,
  AUDIT = 5,
  REVIEW = 6,
  JOB_MANAGE = 7,
}

export const ProcessTypeLabel = {
  [ProcessTypeEnum.SIGN]: "签收环节",
  [ProcessTypeEnum.PRECHECK]: "初审环节",
  [ProcessTypeEnum.ENTRY]: "录入环节",
  [ProcessTypeEnum.QUALITY_CHECK]: "质检环节",
  [ProcessTypeEnum.AUDIT]: "审核环节",
  [ProcessTypeEnum.REVIEW]: "复核环节",
  [ProcessTypeEnum.JOB_MANAGE]: "作业管理环节",
};

export const ProcessTypeOptions = Object.entries(ProcessTypeLabel).map(
  ([key, value]) => ({
    label: value,
    value: key,
  })
);

export const getProcessTypeLabel = (processType: number | ProcessTypeEnum) => {
  return ProcessTypeLabel[processType as keyof typeof ProcessTypeLabel];
};
