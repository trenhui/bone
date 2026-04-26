export enum BizIdentityTypeEnum {
  INSURANCE_COMPANY = 1,
  INSURANCE_COMPANY_BRANCH = 2,
  INSURED_COMPANY = 3,
  INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER = 4,
}

export const BizIdentityTypeLabel = {
  [BizIdentityTypeEnum.INSURANCE_COMPANY]: "保险公司",
  [BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH]: "保险公司分公司",
  [BizIdentityTypeEnum.INSURED_COMPANY]: "投保公司",
  [BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER]:
    "保险公司分公司(保单号)",
};

export const BizIdentityTypeOptions = [
  {
    value: BizIdentityTypeEnum.INSURANCE_COMPANY,
    label: BizIdentityTypeLabel[BizIdentityTypeEnum.INSURANCE_COMPANY],
  },
  {
    value: BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH,
    label: BizIdentityTypeLabel[BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH],
  },
  {
    value: BizIdentityTypeEnum.INSURED_COMPANY,
    label: BizIdentityTypeLabel[BizIdentityTypeEnum.INSURED_COMPANY],
  },
  {
    value: BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER,
    label:
      BizIdentityTypeLabel[
        BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER
      ],
  },
];

export const getBizIdentityTypeLabel = (
  value: Number | BizIdentityTypeEnum
) => {
  return BizIdentityTypeLabel[value as BizIdentityTypeEnum];
};
