// 同时新增和更新、仅新增、仅更新
export enum UploadModeEnum {
  ALL = 1,
  ADD = 2,
  UPDATE = 3,
}
export const UploadModeLabels = {
  [UploadModeEnum.ALL]: "同时新增和更新",
  [UploadModeEnum.ADD]: "仅新增",
  [UploadModeEnum.UPDATE]: "仅更新",
};
export const UploadModeOptions = [
  {
    label: "同时新增和更新",
    value: UploadModeEnum.ALL,
  },
  {
    label: "仅新增",
    value: UploadModeEnum.ADD,
  },
  {
    label: "仅更新",
    value: UploadModeEnum.UPDATE,
  },
];
