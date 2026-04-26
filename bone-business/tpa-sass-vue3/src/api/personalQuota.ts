import request from "@/utils/request";

const BASE_URL = "/data-api/tpa/personalQuota";

class PersonalQuotaAPI {
  /** 获取保单下的被保险人姓名列表 */
  static getInsuredNameList(
    policyNo: string,
    pageNo?: number,
    pageSize?: number
  ) {
    return request<any, any>({
      url: `${BASE_URL}/getInsuredNameList`,
      method: "get",
      params: { policyNo, pageNo, pageSize },
    });
  }

  /** 获取保单下的个人额度操作批次 */
  static getOperateBatchList(
    policyNo: string,
    pageNo?: number,
    pageSize?: number
  ) {
    return request<any, any>({
      url: `${BASE_URL}/getOperateBatchList`,
      method: "get",
      params: { policyNo, pageNo, pageSize },
    });
  }

  /** 获取保单下的个人额度列表 */
  static getPersonalQuotaList(data: PersonalQuotaParams) {
    return request<any, any>({
      url: `${BASE_URL}/list`,
      method: "post",
      data,
    });
  }

  /** 获取某条个人额度数据的保全记录 */
  static getPersonalQuotaChangeList(data: PersonalQuotaChangeParams) {
    return request<any, any>({
      url: `${BASE_URL}/changeList`,
      method: "post",
      data,
    });
  }
}

export default PersonalQuotaAPI;

export interface PersonalQuotaParams {
  /** 页号 */
  pageNo: number;

  /** 每页记录数 */
  pageSize: number;

  /** 普康保单号 */
  policyNo: string;

  /** 被保险人姓名 */
  insuredName?: string;

  /** 被保险人证件类型 */
  insuredCertificateType?: string;

  /** 被保险人证件号 */
  insuredCertificateNumber?: string;

  /** 操作批次名,文件名+操作人姓名 */
  batchName?: string;
}

export interface PersonalQuotaChangeParams {
  /** 页号 */
  pageNo: number;

  /** 每页记录数 */
  pageSize: number;

  /** 普康保单号 */
  policyNo: string;

  /** 被保险人姓名 */
  insuredName: string;

  /** 被保险人证件类型 */
  insuredCertificateType: string;

  /** 被保险人证件号 */
  insuredCertificateNumber: string;
}
