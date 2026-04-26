export enum UploadImgDocSameHandleEnum {
  REPLACE = 1,
  DUPLICATE = 2,
}

export const UploadImgDocSameHandleLabels = {
  [UploadImgDocSameHandleEnum.REPLACE]: "覆盖原来仅保留新的",
  [UploadImgDocSameHandleEnum.DUPLICATE]: "同时保留原来和新的",
};

export const UploadImgDocSameHandleOptions = [
  {
    label: UploadImgDocSameHandleLabels[UploadImgDocSameHandleEnum.REPLACE],
    value: UploadImgDocSameHandleEnum.REPLACE,
  },
  {
    label: UploadImgDocSameHandleLabels[UploadImgDocSameHandleEnum.DUPLICATE],
    value: UploadImgDocSameHandleEnum.DUPLICATE,
  },
];

export const getUploadImgDocSameHandleLabel = (value: number) => {
  return UploadImgDocSameHandleLabels[
    value as keyof typeof UploadImgDocSameHandleLabels
  ];
};
