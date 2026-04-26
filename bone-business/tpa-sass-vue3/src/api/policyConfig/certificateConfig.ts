import request from "@/utils/request";

const BASE_URL = "/data-api";

class CertificateConfigAPI {
  /**
   * 获取单证配置选项
   * @returns
   */
  static getOptions(insuranceName: string, certificateType: string) {
    return request<any, CertificateConfigOptions>({
      url: `${BASE_URL}/tpa/certificateConfig/getOptions`,
      method: "get",
      params: { insuranceName, certificateType },
    });
  }

  /**
   * 根据保单号获取单证配置
   * @param policyNo
   * @returns
   */
  static getCertificateConfigByPolicyNo(policyNo: string) {
    return request<any, ICertificateConfig>({
      url: `${BASE_URL}/tpa/certificateConfig/getOptionByPolicyNo`,
      method: "get",
      params: { policyNo },
    });
  }

  /**
   * 保存单证配置
   * @param data
   * @returns
   */
  static saveCertificateConfig(data: {
    policyNo: string;
    config: ICertificateConfig;
  }) {
    return request({
      url: `${BASE_URL}/tpa/certificateConfig/save`,
      method: "post",
      data,
    });
  }
}

export default CertificateConfigAPI;

export interface CertificateConfigOptions {
  certificateTemplate: { desc: string; code: string }[];
  claimAuditType: { desc: string; code: number }[];
  claimProcessNode: { desc: string; code: string }[];
  adjustmentConclusion: { desc: string; code: string }[];
  imageType: { desc: string; imageType: string; imageTypePK: string }[];
}

export interface ICertificateConfig {
  createApplication: boolean;
  applicationConfig: {
    /** 适用赔案 */
    claimAuditType: number;
    /** 选择模版 */
    certificateTemplate: string;
    /** 生成节点 */
    claimProcessNode: string;
    /** 生成条件（理赔结论） */
    adjustmentConclusion: string;
    /** 所属影像分类-下拉框(保司分类) */
    imageType: string;
    /** 所属影像分类-下拉框(普康分类) */
    imageTypePK: string;
  };
  createNotification: boolean;
  notificationConfig: {
    /** 适用赔案 */
    claimAuditType: number;
    /** 选择模版 */
    certificateTemplate: string;
    /** 生成节点 */
    claimProcessNode: string;
    /** 生成条件（理赔结论） */
    adjustmentConclusion: string;
    /** 所属影像分类-下拉框(保司分类) */
    imageType: string;
    /** 所属影像分类-下拉框(普康分类) */
    imageTypePK: string;
  };
}
