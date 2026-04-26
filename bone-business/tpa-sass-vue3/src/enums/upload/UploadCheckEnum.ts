// 所有数据校验后导入、逐行导入
export enum UploadCheckEnum {
  ALL = 1,
  ROW = 2,
}

export const UploadCheckLabels = {
  [UploadCheckEnum.ALL]: "所有数据校验后导入",
  [UploadCheckEnum.ROW]: "逐行导入",
};

export const UploadCheckOptions = [
  { label: "所有数据校验后导入", value: UploadCheckEnum.ALL },
  { label: "逐行导入", value: UploadCheckEnum.ROW },
];

export function getUploadCheckTypeLabel(
  value: UploadCheckEnum | undefined | null
) {
  if (!value) {
    return "未定义";
  }
  return UploadCheckLabels[value];
}
