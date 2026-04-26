import request from "@/utils/request";

const BASE_URL = "/code-api/";

class dataSourceConfigAPI {
  static getDataSourceConfigList(params: any) {
    return request({
      url: `${BASE_URL}data-source-config/list`,
      method: "get",
      params,
    });
  }
  static deleteDataSourceConfig(id: any) {
    return request({
      url: `${BASE_URL}data-source-config/delete?id=` + id,
      method: "delete",
    });
  }
  static createDataSourceConfig(data: any) {
    return request({
      url: `${BASE_URL}data-source-config/create`,
      method: "post",
      data,
    });
  }
  static updateDataSourceConfig(data: any) {
    return request({
      url: `${BASE_URL}data-source-config/update`,
      method: "put",
      data,
    });
  }
  static getDataSourceConfig(id: any) {
    return request({
      url: `${BASE_URL}'data-source-config/get?id=` + id,
      method: "get",
    });
  }
}

export default dataSourceConfigAPI;
