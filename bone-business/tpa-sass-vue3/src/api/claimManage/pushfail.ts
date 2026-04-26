import request from "@/utils/request";

const PUSH_FAIL_BASE_URL = "/data-api";

class PushFailAPI {
  /**
   * 获取推送失败列表
   * @param data
   * @returns
   */
  static getClaimPushFailList(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/pageClaimPushFail",
      method: "post",
      data,
    });
  }
  /**
   * 重新推送
   * @param data
   * @returns
   */
  static repush(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/repush",
      method: "post",
      data,
    });
  }
  /**
   * 退回人工处理
   * @param data
   * @returns
   */
  static return(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/return",
      method: "post",
      data,
    });
  }

  /**
   * 导出推送失败
   * @param data
   * @returns
   */
  static exportClaimPushFail(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/exportClaimPushFail",
      method: "post",
      responseType: "blob",
      data,
    });
  }

  /**
   * 获取推送失败操作记录
   * @param data
   * @returns
   */
  static getClaimPushFailOperate(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/pageClaimPushFailOperate",
      method: "post",
      data,
    });
  }

  /**
   * 导出推送失败操作记录
   * @param data
   * @returns
   */
  static exportClaimPushFailOperate(data: any) {
    return request<any, any>({
      url: PUSH_FAIL_BASE_URL + "/claim/push/exportClaimPushFailOperate",
      method: "post",
      responseType: "blob",
      data,
    });
  }
}

export default PushFailAPI;

export interface ClaimPushFail {
  id: number;
  errorType: string;
  vipSign: string;
  policyNo: string;
  batchNo: string;
  claimNo: string;
  failTime: string;
  pushBackReason: string;
  outInsureName: string;
  outInsureIdentityTypeCn: string;
  outInsureIdentityNo: string;
  mainInsureName: string;
  mainInsureIdentityTypeCn: string;
  mainInsureIdentityNo: string;
  insureName: string;
  insuranceName: string;
  branchName: string;
  auditingOperatorName: string;
  bizIdentityCode: string;
  tenantId: string;
}

export interface ClaimPushFailOperate {
  id: number;
  errorType: string;
  vipSign: string;
  policyNo: string;
  batchNo: string;
  claimNo: string;
  failTime: string;
  createUser: string;
  createTime: string;
  backNode: string;
  backAuditUser: string;
  pushBackReason: string;
  outInsureName: string;
  outInsureIdentityTypeCn: string;
  outInsureIdentityNo: string;
  mainInsureName: string;
  mainInsureIdentityTypeCn: string;
  mainInsureIdentityNo: string;
  insureName: string;
  insuranceName: string;
  branchName: string;
  auditingOperatorName: string;
}
