import request from "@/utils/request";

const DATA_BASE_URL = "/data-api";

class DataAPI {
  static getForm(id: string) {
    return request({
      url: DATA_BASE_URL + "/tpa/claim/detail?id=" + id,
      method: "get",
    });
  }

  static saveForm(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/claim/submit",
      method: "post",
      data,
    });
  }

  static getList(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/query/list",
      method: "post",
      data,
    });
  }

  static updateListRow(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/query/update",
      method: "post",
      data,
    });
  }

  static deleteListRow(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/query/delete",
      method: "post",
      data,
    });
  }

  static getOne(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/query/one",
      method: "post",
      data,
    });
  }

  static getPolicyList(data: any) {
    return request({
      url: DATA_BASE_URL + "/tpa/adjust/querypolicy",
      method: "get",
      params: data,
    });
  }

  static getUserName() {
    return request<any, string>({
      url: DATA_BASE_URL + "/tpa/user",
      method: "get",
    });
  }
}

export default DataAPI;
