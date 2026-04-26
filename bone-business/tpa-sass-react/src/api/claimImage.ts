import request from "@/utils/request";

const CLAIM_IMAGE_BASE_URL = "/data-api/tpa/image";

class ClaimImageAPI {
  static query(data: any) {
    return request<any, IImageDoc[]>({
      url: CLAIM_IMAGE_BASE_URL + "/query",
      method: "post",
      data,
    });
  }

  static updateList(data: IImageDoc[]) {
    return request<any, IImageDoc[]>({
      url: CLAIM_IMAGE_BASE_URL + "/updatelist",
      method: "post",
      data: {
        claimImageDTOList: data,
      },
    });
  }

  static delete(data: any) {
    return request<any, any>({
      url: CLAIM_IMAGE_BASE_URL + "/delete",
      method: "post",
      data,
    });
  }

  static updatePushFlag(data: any) {
    return request<any, any>({
      url: CLAIM_IMAGE_BASE_URL + "/push_flag",
      method: "post",
      data,
    });
  }

  static updateOcrFlag(data: any) {
    return request<any, any>({
      url: CLAIM_IMAGE_BASE_URL + "/ocr_flag",
      method: "post",
      data,
    });
  }

  static getTypeList(insuranceName: string) {
    return request<any, IImageType[]>({
      url: CLAIM_IMAGE_BASE_URL + "/typeList?insuranceName=" + insuranceName,
      method: "get",
    });
  }

  static getInvoiceList(id: string) {
    return request<any, IInvoice[]>({
      url: CLAIM_IMAGE_BASE_URL + "/invoice_list?id=" + id,
      method: "get",
    });
  }

  static bindInvoice(data: { imageId: string; invoiceUuidList: string[] }) {
    return request<any, IInvoice[]>({
      url: CLAIM_IMAGE_BASE_URL + "/bind_invoice",
      method: "post",
      data,
    });
  }

  // 查询普康宝影像件 出险人(0)或者申请人(1)
  static getPkbImage(data: { claimId: string; personType: 0 | 1 }) {
    return request<any, IImageDoc[]>({
      url: CLAIM_IMAGE_BASE_URL + "/pkbimage",
      method: "post",
      data,
    });
  }

  // 添加个人影像库到赔案
  static addPersonalImageToClaim(claimNumber: string) {
    return request<any, any>({
      url: CLAIM_IMAGE_BASE_URL + "/addPersonalImageToClaim",
      method: "get",
      params: { claimNumber },
    });
  }
}

export default ClaimImageAPI;

export interface IImageDoc {
  relatedId: string;
  // 影像路径
  imagePath: string;
  imageName: string;
  // 分类
  imageType: string;
  // 普康影像分类
  imagePkType: string;
  imageDetailId: string;
  // 分类内的顺序，从1开始，如果没有分类，则不使用
  imageIndex: number;
  // 是否清晰
  clearType: number | null;
  // 角度
  angle: number;
  // 备注
  remark: string;
  // 推送标志
  pushFlag: number;
  tenantId: string;
  id: string;
  // 个人影像库标志 0否 1是
  fromPersonalImage: number;

  // 是否选中 用于前端
  isChecked?: boolean;
  editAngle?: number;
  classIndex?: number;
}

export interface IImageType {
  classifyName: string;
  classifyCode: string;
}

export interface IInvoice {
  invoiceId: string;
  invoiceNo: string;
  invoiceUuid: string;
  bound: boolean;
}
