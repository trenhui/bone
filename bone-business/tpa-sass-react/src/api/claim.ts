import request from "@/utils/request";

const CLAIM_BASE_URL = "/data-api/tpa/claim";

class ClaimAPI {
  /**
   * 挂起赔案
   * @param data
   * @returns
   */
  static hangup(data: { claimId: string; hangupType: string; reason: string }) {
    return request({
      url: CLAIM_BASE_URL + "/hangup",
      method: "post",
      data,
    });
  }

  /**
   * 查询挂起记录
   * @param data
   * @returns
   */
  static hangupRecord(claimNo: string) {
    return request<any, IHangupRecord[]>({
      url: CLAIM_BASE_URL + "/hanguprecord",
      method: "get",
      params: { claimNo },
    });
  }

  /**
   * 查询tpa的操作记录
   * @param data
   * @returns
   */
  static record(claimNo: string) {
    return request<any, IClaimRecord[]>({
      url: CLAIM_BASE_URL + "/record",
      method: "get",
      params: { claimNo },
    });
  }

  /**
   * 初审页面右上角，挂起信息
   * @param claimId
   * @returns
   */
  static hangupInfo(claimId: string) {
    return request<any, IHangupInfo>({
      url: CLAIM_BASE_URL + "/hangupinfo",
      method: "get",
      params: { id: claimId },
    });
  }

  /**
   * 查看报案信息
   * @param claimId
   * @returns
   */
  static applyInfo(claimId: string) {
    return request<any, IApplyInfo>({
      url: CLAIM_BASE_URL + "/applyinfo",
      method: "get",
      params: { id: claimId },
    });
  }

  /**
   * 查询特约信息/特殊信息 0是特约，1是特殊
   * @param claimId
   * @param type
   * @returns
   */
  static policySetting(claimId: string, type: number) {
    return request<any, string>({
      url: CLAIM_BASE_URL + "/policysetting",
      method: "get",
      params: { id: claimId, type },
    });
  }

  /**
   * 查询该赔案是否有重复发票。这是一个异步接口。
   * @param claimId
   * @returns
   */
  static checkSameInvoice(claimId: string) {
    return request<any, string>({
      url: CLAIM_BASE_URL + "/checksameinvoice",
      method: "get",
      params: { id: claimId },
    });
  }

  /**
   * 通过异步任务的id查询该异步任务的状态。
   * @param taskId
   * @returns
   */
  static checkSameInvoiceTaskStatus(taskId: string) {
    return request<any, number>({
      url: "/data-api/tpa/task",
      method: "get",
      params: { taskId },
    });
  }

  /**
   * 查询该赔案发票查重结果。
   * @param claimId
   * @returns
   */
  static checkSameInvoiceResult(claimId: string) {
    return request<any, string>({
      url: CLAIM_BASE_URL + "/checksameresult",
      method: "get",
      params: { id: claimId },
    });
  }

  /*
   * 查询外包录入记录
   * @param claimNo
   * @returns
   */
  static getOutWriterRecords(claimNo: string) {
    return request<any, IOutEntryRecord[]>({
      url: "/data-api/v1/tpa/support/getOutWriterRecords",
      method: "get",
      params: { claimNumber: claimNo },
    });
  }

  /**
   * 查询历史案件记录
   * @param data
   * @returns
   */
  static queryHistoryClaims(data: {
    claimNo: string;
    outIdentityNo: string;
    outUserName: string;
  }) {
    return request<any, IHistoryCase[]>({
      url: "/data-api/v1/tpa/support/queryHistoryClaims",
      method: "post",
      data,
    });
  }

  /**
   * 获取报案信息
   * @param claimId
   * @returns
   */
  static getReportClaimCondition(claimId: string) {
    return request<any, IReportInfo>({
      url: "/data-api/tpa/claim/getreportclaimcondition",
      method: "get",
      params: { id: claimId },
    });
  }

  /**
   * 报案
   * @param data
   * @returns
   */
  static reportClaim(data: IReportInfo) {
    return request({
      url: "/data-api/tpa/claim/reportclaim",
      method: "post",
      data,
    });
  }

  /**
   * 复核驳回
   * @param data
   * @returns
   */
  static rejectReviewClaim(data: {
    claimId: string;
    rejectType: string;
    reason: string;
  }) {
    return request({
      url: "/data-api/tpa/claim/reject",
      method: "post",
      data,
    });
  }

  /**
   * 查询人员信息
   * @param data
   * @returns
   */
  static queryPersonInfo(data: { claimNo: string; dataType: number }) {
    return request<any, IPersonInfo>({
      url: "/data-api/v1/tpa/support/queryPersonInfo",
      method: "post",
      data,
    });
  }

  /**
   * 退回至环节页面初始化
   * @param data
   * @returns
   */
  static returnNodePageInit(claimNumber: string) {
    return request<any, IReturnNodePageInit>({
      url: "/data-api/tpa/claim/returnNodePageInit",
      method: "get",
      params: { claimNumber },
    });
  }

  /**
   * 退回至环节
   * @param data
   * @returns
   */
  static returnNodeBack(data: IReturnNodeBack) {
    return request<any, any>({
      url: "/data-api/tpa/claim/returnNodeBack",
      method: "post",
      data,
    });
  }

  /**
   * 批量责任
   * @param data
   * @returns
   */
  static batchDuty(data: {
    claimId: string;
    policyNo: string;
    invoiceIds: string[];
    dutyIds: string[];
  }) {
    return request<any, any>({
      url: "/data-api/invoice/duties/batchUpdate",
      method: "post",
      data,
    });
  }

  /**
   * 批量拒赔
   * @param data
   * @returns
   */
  static batchRejectInvoice(data: { claimId: string; invoiceIds: string[] }) {
    return request<any, any>({
      url: "/data-api/invoice/amount/batchUpdate",
      method: "post",
      data,
    });
  }

  /**
   * 复制发票
   * @param data
   * @returns
   */
  static copyInvoice(data: {
    claimId: string;
    invoiceId: string;
    updateInvoiceNo: string;
  }) {
    return request<any, any>({
      url: "/data-api/invoice/copy",
      method: "post",
      data,
    });
  }
}

export default ClaimAPI;

// 挂起记录
export interface IHangupRecord {
  hangUpTime: string;
  claimNo: string;
  explanation: string;
  reason: string;
  reasonType: string;
}

// 赔案的操作记录
export interface IClaimRecord {
  objectId: number;
  remark: string;
  operation: string;
  createTime: string;
  createBy: string;
}

// 挂起信息
export interface IHangupInfo {
  hangupOperator: string;
  hangupTime: string;
  hangupType: string;
  hangupReason: string;
}

// 查看报案信息
export interface IApplyInfo {
  applyName: string;
  applyIdentityNo: string;
  applyType: string;
  applyTime: string;
  applyPhone: string;
  outInsureIdentityNo: string;
  outInsureName: string;
  outInsureTime: string;
  policyNo: string;
  collectName: string;
  accountNo: string;
  bankName: string;
  bankAddress: string;
}

// 外包录入记录
export interface IOutEntryRecord {
  claimNumber: string;
  writeDate: string;
  writeUser: string;
  writeJson: string;
  writeChinese: string;
}

// 历史案件记录
export interface IHistoryCase {
  claimNo: string;
  taskNo: string;
  claimStatus: string;
  policyNo: string;
  isReject: string;
  compensationAmount: number;
  publicAmount: string;
  auditUser: string;
  auditDate: string;
  personName: string;
  personCertId: string;
  insuranceName: string;
  insureName: string;
  colorMark: number;
}

// 报案信息
export interface IReportInfo {
  policyNo: string;
  outInsureName: string;
  outInsureIdentityType: string;
  outInsureIdentityNo: string;
  outInsureIdentity: string;
  outInsureGender: string;
  outInsureAge: number;
  outInsureBirthDay: string;
  reportName: string;
  reportPhone: string;
  contactName: string;
  contactPhone: string;
  outInsureAddress: string;
  outInsureDetail: string;
  outInsureTime: string;
  relationType: string;
  diseaseReason: string;
  responsibilityType: string;
  kindCodeTypes: { name: string; code: string }[];
  kindCode: string;
  itemCodeTypes: { name: string; code: string; parentCode: string }[];
  itemCode: string;
  secondaryItemCodeTypes: { name: string; code: string; parentCode: string }[];
  secondaryItemCode: string;
  payoutAmountMap: {
    /* 发票总金额-统筹 */
    noPoolingAmount: number;
    /* 发票总金额 */
    totalAmount: number;
    /* 自定义 */
    custom: number;
  };
  payoutAmountType: string;
}

// 人员信息
export interface IPersonInfo {
  primaryInsuredName: string;
  primaryInsuredIdNumber: string;
  primaryInsuredIdType: string;
  primaryInsuredIdValidityPeriod: string[];
  collectName: string;
  collectIdNumber: string;
  collectIdType: string;
  collectIdValidityPeriod: string[];
  collectContact: string;
  collectAddress: string;
  collectBankName: string;
  collectBankAccount: string;
}

// 初始化退回至环节页面
export interface IReturnNodePageInit {
  reasonCode: string;
  returnStageList: { code: string; name: string; operaterName: string }[];
}

// 退回至环节
export interface IReturnNodeBack {
  claimNumber: string;
  targetStage: string;
  dealerStrategy: string;
  assignDealerName: string;
  reasonCode: string;
  reasonData: string;
  remark: string;
}
