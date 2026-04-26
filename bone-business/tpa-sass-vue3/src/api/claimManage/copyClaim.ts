import request from "@/utils/request";

const COPY_CLAIM_BASE_URL = "/data-api";

export const CopyClaimAPI = {
  copyClaim: (data: any) => {
    return request({
      url: COPY_CLAIM_BASE_URL + "/tpa/claim/copy",
      method: "post",
      data,
    });
  },
  queryCopyClaimRecord: (data: any) => {
    return request<any, any>({
      url: COPY_CLAIM_BASE_URL + "/tpa/claim/querycopy",
      method: "post",
      data,
    });
  },
};

export default CopyClaimAPI;

export interface CopyClaimParams {
  /** 原赔案号 */
  claimNos: string[];
  /** 是否生成新批次和签收时间 */
  isBatchAndSignTime: boolean;
  /** 原赔案赔付金额是否转至新赔案的三方已赔 */
  isCompensationAmount: boolean;
  /** 原配案收单流水号是否复制 */
  isCopySerialNo: boolean;
  /** 原配案保司报案号是否复制 */
  isCopyInsureClaimNo: boolean;
  /** 新赔案状态 */
  newClaimStatus: string;
  /** 新赔案处理人员 */
  newClaimOperator: string;
  operatorName: string;
  /** 备注 */
  remark: string;
}

export interface CopyClaimRecord {
  id: string;
  oldBatchNo: string;
  oldClaimNo: string;
  oldSignTime: string;
  newBatchNo: string;
  newClaimNo: string;
  newSignTime: string;
  operator: string;
  operateTime: string;
  remark: string;
}
