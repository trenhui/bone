// 数据、文件附件、影像件、图片
export enum UploadTypeEnum {
  DATA = 1,
  IMG_DOC = 2,
  IMG = 3,
  FILE = 4,
}

export const UploadTypeLabels = {
  [UploadTypeEnum.DATA]: "数据",
  [UploadTypeEnum.IMG_DOC]: "影像件",
  [UploadTypeEnum.IMG]: "图片",
  [UploadTypeEnum.FILE]: "文件附件",
};

export const UploadTypeOptions = [
  { label: "数据", value: UploadTypeEnum.DATA },
  { label: "影像件", value: UploadTypeEnum.IMG_DOC },
  { label: "图片", value: UploadTypeEnum.IMG },
  { label: "文件附件", value: UploadTypeEnum.FILE },
];

export function getUploadTypeLabel(value: UploadTypeEnum | undefined | null) {
  if (!value) {
    return "未定义";
  }
  return UploadTypeLabels[value];
}
