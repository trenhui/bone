// 新增、替换
export enum UploadImgDocModeEnum {
  ADD = 1,
  REPLACE = 2,
}

export const UploadImgDocModeLabels = {
  [UploadImgDocModeEnum.ADD]: "新增",
  [UploadImgDocModeEnum.REPLACE]: "替换",
};

export const UploadImgDocModeOptions = [
  {
    label: UploadImgDocModeLabels[UploadImgDocModeEnum.ADD],
    value: UploadImgDocModeEnum.ADD,
  },
  {
    label: UploadImgDocModeLabels[UploadImgDocModeEnum.REPLACE],
    value: UploadImgDocModeEnum.REPLACE,
  },
];

export const getUploadImgDocModeLabel = (value: number) => {
  return UploadImgDocModeLabels[value as keyof typeof UploadImgDocModeLabels];
};
