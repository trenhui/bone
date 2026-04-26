import request from "@/utils/request";

const BASE_URL = "/code-api/";

class CodeGenerationAPI {
  static getCodegenTablePage(params: any) {
    return request({
      url: `${BASE_URL}codegen/table/page`,
      method: "get",
      params,
    });
  }
  static deleteCodegen(tableId: any) {
    return request({
      url: `${BASE_URL}codegen/delete?tableId=` + tableId,
      method: "delete",
    });
  }
  static syncCodegenFromDB(tableId: any) {
    return request({
      url: `${BASE_URL}codegen/sync-from-db?tableId=` + tableId,
      method: "put",
    });
  }
  static getSchemaTableList(params: any) {
    return request({
      url: `${BASE_URL}codegen/db/table/list`,
      method: "get",
      params,
    });
  }
  static createCodegenList(data: any) {
    return request({
      url: `${BASE_URL}codegen/create-list`,
      method: "post",
      data,
    });
  }
  static downloadCodegen(params: any) {
    return request({
      url: `${BASE_URL}/codegen/download2`,
      method: "get",
      responseType: "blob",
      params,
    });
  }
}

export default CodeGenerationAPI;
