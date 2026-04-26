import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/model";

class ModelAPI {
  static getPageModelList(pageCode: string, bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${BASE_URL}/listByPageCode`,
      method: "get",
      params: {
        pageCode,
        bizIdentityCode,
      },
    });
  }
  static updateModelStatus(data: any) {
    return request({
      url: `${BASE_URL}/updateStatus`,
      method: "post",
      data,
    });
  }
  static getModelList(params: any) {
    return request<any, any>({
      url: `${BASE_URL}/list`,
      method: "get",
      params,
    });
  }
  static getBizIdentityModelList(bizIdentityCode: string) {
    return request<any, any>({
      url: `${BASE_URL}/listByBizIdentityCode?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static getModelListByFieldSetId(id: any) {
    return request({
      url: `${BASE_URL}/listByFieldSetId?fieldSetId=${id}`,
      method: "get",
    });
  }
  static getModelListByTableId(id: any) {
    return request({
      url: `${BASE_URL}/listByTableId?tableId=${id}`,
      method: "get",
    });
  }
  static updateFieldWithMetaData() {
    return request({
      url: `${BASE_URL}/updateFieldWithMetaData`,
      method: "get",
    });
  }
  static getFieldListByModelCode(params: any) {
    return request<any, any>({
      url: `${BASE_URL}/listByModelCode`,
      method: "get",
      params,
    });
  }
  static syncBaseTemplateField(bizIdentityCode: string) {
    return request({
      url: `${BASE_URL}/updateFieldWithMetaDataByIdentityCode?bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }

  static getHeadModel(pageCode: string, bizIdentityCode: string) {
    return request<any, any>({
      url: `${BASE_URL}/getModelByName?pageCode=${pageCode}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
}

export default ModelAPI;
