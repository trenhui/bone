import request from "@/utils/request";

export const getBackendRuleList = (bizIdentityCode: string = "") => {
  return request<any, BackendRule[]>({
    url:
      "/data-api/tpa/claim/config/getHookList?bizIdentityCode=" +
      bizIdentityCode,
    method: "get",
  });
};

export const saveBackendRule = (data: {
  bizIdentityCode: string;
  configList: BackendRule[];
}) => {
  return request({
    url: "/data-api/tpa/claim/config/saveHookConfig",
    method: "post",
    data,
  });
};

export interface BackendRule {
  /** 具体执行hook的中文描述，需要展示 */
  beanDesc: string;
  /** 具体的执行hook 的beanName,保存时候必传 */
  beanName: string;
  /** 细分功能领域code,不用展示 */
  beanType: string;
  /** 展示字段，细分功能领域，比如： 发票后置保存 */
  beanTypeDesc: string;
  /** 所属领域英文，比如claim,invoice */
  domain: string;
  /** 展示字段，所属领域中文，比如发票，赔案 */
  domainDesc: string;
  /** 状态 0:禁用 1:启用 */
  status: number;
}
