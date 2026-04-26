import request from "@/utils/request";

const UPLOAD_BASE_URL = "/page-api/cfg/upload";

class UploadAPI {
  /** 上传数据组件 */
  static getUploadDataByCode(code: string, bizIdentityCode: string = "") {
    return request<any, UploadDataComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadDataByCode?code=${code}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getUploadDataById(id: string) {
    return request<any, UploadDataComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadDataById?id=${id}`,
      method: "get",
    });
  }
  static updateUploadData(data: UploadDataComponent) {
    return request({
      url: `${UPLOAD_BASE_URL}/updateUploadData`,
      method: "post",
      data,
    });
  }

  /** 上传影像件组件 */
  static getUploadImgDocByCode(code: string, bizIdentityCode: string = "") {
    return request<any, UploadImageDocComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadImageByCode?code=${code}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getUploadImgDocById(id: string) {
    return request<any, UploadImageDocComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadImageById?id=${id}`,
      method: "get",
    });
  }
  static updateUploadImgDoc(data: UploadImageDocComponent) {
    return request({
      url: `${UPLOAD_BASE_URL}/updateUploadImage`,
      method: "post",
      data,
    });
  }

  /** 上传图片组件 */
  static getUploadImageByCode(code: string, bizIdentityCode: string = "") {
    return request<any, UploadImageComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadPictureByCode?code=${code}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getUploadImageById(id: string) {
    return request<any, UploadImageComponent>({
      url: `${UPLOAD_BASE_URL}/getUploadPictureById?id=${id}`,
      method: "get",
    });
  }
  static updateUploadImage(data: UploadImageComponent) {
    return request({
      url: `${UPLOAD_BASE_URL}/updateUploadPicture`,
      method: "post",
      data,
    });
  }

  /** 获取团单个险配置中的上传组件列表 */
  static getPolicyUploadComponentList() {
    return request<any, any>({
      url: `${UPLOAD_BASE_URL}/getUploadComponentList2`,
      method: "get",
    });
  }

  /** 获取标准作业中的上传组件列表 */
  static getJobUploadComponentList() {
    return request<any, any>({
      url: `${UPLOAD_BASE_URL}/getUploadComponentList1`,
      method: "get",
    });
  }
}

export default UploadAPI;

export type UploadComponent =
  | UploadDataComponent
  | UploadImageDocComponent
  | UploadImageComponent;

export interface UploadDataComponent {
  id: string;
  /** 组件标题 */
  title: string;
  /** 导入类型 固定为1*/
  dataType: number;
  /** 适用数据模型 */
  model: {
    modelId: string;
    modelName: string;
  };
  /** 字段列表 */
  fieldList: any[];
  /** 字段ID列表 */
  fieldIdList?: any[];
  /** 单字段规则 */
  singleFieldRuleList: SingleFieldRule[];
  /** 组合字段规则 */
  groupFieldRuleList: GroupFieldRule[];
  /** 模板文件名 */
  templateFileName: string;
  /** 文件最大大小 */
  fileMaxSize?: number;
  /** 文件内最大数据量 */
  fileMaxCount?: number;
  /** 文件格式 */
  fileFormat?: string;
  /** 表头校验方式 */
  headerCheckMode?: number;
  /** 导入类型 */
  importTypeList?: number[];
  /** 导入校验方式 */
  checkType?: number;
  /** 导入说明 */
  importDescription?: string;
}

export interface UploadImageDocComponent {
  id: string;
  /** 组件标题 */
  title: string;
  /** 导入类型 固定为2*/
  dataType: number;
  /** 影像压缩包格式描述 */
  packageDescription: string;
  /** 文件大小上限 */
  fileMaxSize: number;
  /** 包文件夹上限 */
  folderMaxSize: number;
  /** 限定文件格式 */
  fileFormat: string;
  /** 支持导入方式,1:新增,2:替换 */
  importTypeList: number[];
  /** 同名文件处理方式,1:新旧都保留,2：覆盖原来的,仅保留新的 */
  sameFileHandle: number;
  /** 导入操作说明 */
  importDescription: string;
}

export interface UploadImageComponent {
  id: string;
  /** 导入标题名称 */
  title: string;
  /** 导入类型 固定为3 */
  dataType: number;
  /** 单张大小上限 */
  singleMaxSize: number;
  /** 限定文件格式 */
  fileFormat: string;
  /** 上传张数限定 */
  maxCount: number;
  /** 导入操作说明 */
  importDescription: string;
}

export interface SingleFieldRule {
  fieldId: string;
  bizName?: string;
  required: boolean;
  unique: boolean;
  isEditing?: boolean;
}

export interface GroupFieldRule {
  fieldIdList?: string[];
  fieldList?: any[];
  fieldIdListA?: string[];
  fieldListA?: any[];
  fieldIdListB?: string[];
  fieldListB?: any[];
  unique: boolean;
  isEditing?: boolean;
}

export const UPLOAD_CODE = {
  /** 新批次签收-导入人员 */
  NEW_SIGN_UPLOAD_PEOPLE: "newSignUploadPeople",
  /** 个人专属额度-初始化 */
  PERSONAL_QUOTA_INITIALIZE: "personalQuotaInitialize",
  /** 个人专属额度-额度加减 */
  PERSONAL_QUOTA_CHANGE: "personalQuotaChange",
  /** 个人专属额度-减人 */
  PERSONAL_QUOTA_REMOVE_PEOPLE: "personalQuotaRemovePeople",
  /** 签收详情-上传影像 */
  SIGN_DETAIL_UPLOAD_IMAGE: "signDetailUploadImage",
  /** 初审详情-上传影像 */
  FIRST_AUDIT_DETAIL_UPLOAD_PICTURE: "firstAuditDetailUploadPicture",
};
