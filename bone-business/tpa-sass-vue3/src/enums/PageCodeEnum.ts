export enum PageCodeEnum {
  newSign = "newSign",
  entry = "entry",
  qualitycheck = "qualitycheck",
  audit = "audit",
  review = "review",
}

export const PageCodeLabels = {
  [PageCodeEnum.newSign]: "新批次签收页",
  [PageCodeEnum.entry]: "录入页面",
  [PageCodeEnum.qualitycheck]: "质检页面",
  [PageCodeEnum.audit]: "审核页面",
  [PageCodeEnum.review]: "复核页面",
};

export const PageCodeOptions = [
  {
    label: PageCodeLabels[PageCodeEnum.entry],
    value: PageCodeEnum.entry,
  },
  {
    label: PageCodeLabels[PageCodeEnum.qualitycheck],
    value: PageCodeEnum.qualitycheck,
  },
  {
    label: PageCodeLabels[PageCodeEnum.audit],
    value: PageCodeEnum.audit,
  },
  {
    label: PageCodeLabels[PageCodeEnum.review],
    value: PageCodeEnum.review,
  },
];

export const getPageCodeChinese = (pageCode: string | PageCodeEnum) => {
  return PageCodeLabels[pageCode as PageCodeEnum];
};
