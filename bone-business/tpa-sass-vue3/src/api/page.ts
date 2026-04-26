import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/page";

class PageAPI {
  static createExclusivePage(params: ICreateExclusivePageParams) {
    return request({
      url: `${BASE_URL}/createExclusivePage`,
      method: "get",
      params,
    });
  }
  static getEditingPageSchema(
    code: any,
    displayMode = "view",
    bizIdentityCode = ""
  ) {
    return request({
      url: `${BASE_URL}/preview?pageCode=${code}&displayMode=${displayMode}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getPublishingPageSchema(
    code: any,
    displayMode = "view",
    bizIdentityCode = ""
  ) {
    return request({
      url: `${BASE_URL}/previewByRelease?pageCode=${code}&displayMode=${displayMode}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getLastPublishTime(bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${BASE_URL}/lastPublishTime?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static publishBaseTemplate() {
    return request<any, any>({
      url: `${BASE_URL}/publishBasic`,
      method: "get",
    });
  }
  static publishExclusive(bizIdentityCode: string) {
    return request<any, any>({
      url: `${BASE_URL}/publishExclusive?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static deleteExclusivePages(bizIdentityCode: string) {
    return request({
      url: `${BASE_URL}/delete?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }

  static copyEntryToQualityCheck(bizIdentityCode: string = "") {
    return request({
      url: `${BASE_URL}/copyEntryToQualityCheck?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }

  // 获取页面业务信息开关
  static getPageHeadEnable(pageCode: string, bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${BASE_URL}/getPage?pageCode=${pageCode}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }

  // 配置页面业务信息开关
  static updatePageHeadEnable(id: string, businessFieldEnabled: number) {
    return request<any, any>({
      url: `${BASE_URL}/updatePage`,
      method: "post",
      data: {
        id,
        businessFieldEnabled,
      },
    });
  }

  // 同步基础模板规则到主体
  static pullRulesFromBase(bizIdentityCode: string) {
    return request({
      url: `${BASE_URL}/pullRulesFromBase?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
}

export default PageAPI;

export interface ICreateExclusivePageParams {
  bizType: number;
  insuranceCompanyCode?: string;
  insuranceCompanyName?: string;
  insuranceCompanyBranchCode?: string;
  insuranceCompanyBranchName?: string;
  insuredCompanyCode?: string;
  insuredCompanyName?: string;
  policyNo?: string;
}
