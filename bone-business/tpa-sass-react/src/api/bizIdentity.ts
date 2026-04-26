import request from "@/utils/request";

const BIZ_IDENTITY_BASE_URL = "/page-api/cfg/bizIdentity";

class BizIdentityAPI {
  static checkIsCreatedByPageCode(bizIdentityCode: string) {
    return request({
      url: `${BIZ_IDENTITY_BASE_URL}/checkIsCreatedByPageCode?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getBizIdentityList(data: any) {
    return request<any, any>({
      url: `${BIZ_IDENTITY_BASE_URL}/list`,
      method: "post",
      data,
    });
  }

  static getBizIdentityOptions(
    bizType: number,
    parentCode: string | null,
    parentName: string | null,
    nameLike: string | null
  ) {
    return request<any, IBizIdentity[]>({
      url: `${BIZ_IDENTITY_BASE_URL}/getByParam`,
      method: "get",
      params: {
        bizType: bizType,
        parentCode: parentCode,
        parentName: parentName,
        nameLike: nameLike,
      },
    });
  }
}

export default BizIdentityAPI;

export interface IBizIdentity {
  bizType: number;
  code: string;
  name: string;
}
