export enum BizIdentityStatusEnum {
  active = 1,
  inactive = 0,
}

export const BizIdentityStatusLabels = {
  [BizIdentityStatusEnum.active]: "启用",
  [BizIdentityStatusEnum.inactive]: "未启用",
};

export const BizIdentityStatusOptions = [
  { label: "启用", value: BizIdentityStatusEnum.active },
  { label: "未启用", value: BizIdentityStatusEnum.inactive },
];

export const getBizIdentityStatusLabel = (
  value: number | BizIdentityStatusEnum | null
) => {
  if (value === null) {
    return BizIdentityStatusLabels[BizIdentityStatusEnum.inactive];
  }
  return BizIdentityStatusLabels[value as BizIdentityStatusEnum];
};
