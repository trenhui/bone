// 按照模板表头名称一一对应、导入时字段映射
export enum UploadHeadCheckEnum {
  NAME = 1,
  MAPPING = 2,
}

export const UploadHeadCheckLabels = {
  [UploadHeadCheckEnum.NAME]: "按照模板表头名称一一对应",
  [UploadHeadCheckEnum.MAPPING]: "导入时字段映射",
};

export const UploadHeadCheckOptions = [
  { label: "按照模板表头名称一一对应", value: UploadHeadCheckEnum.NAME },
  { label: "导入时字段映射", value: UploadHeadCheckEnum.MAPPING },
];
