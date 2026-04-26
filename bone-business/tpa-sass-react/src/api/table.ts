import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/table";

class TableAPI {
  static getTableConfig(id: any) {
    return request({
      url: `${BASE_URL}/get?tableId=${id}`,
      method: "get",
    });
  }
  static updateTableConfig(data: any) {
    return request({
      url: `${BASE_URL}/update`,
      method: "post",
      data,
    });
  }
  static getDataSummaryRuleByTableId(tableId: string) {
    return request({
      url: `${BASE_URL}/getDataSummaryRuleByTableId?tableId=${tableId}`,
      method: "get",
    });
  }
  static getTextNumberField(tableId: string) {
    return request({
      url: `${BASE_URL}/getTextNumberField?tableId=${tableId}`,
      method: "get",
    });
  }
  static getAggregateRule(tableId: string) {
    return request({
      url: `${BASE_URL}/getAggregateRule?tableId=${tableId}`,
      method: "get",
    });
  }
  static getFieldList(tableId: string) {
    return request({
      url: `${BASE_URL}/fieldList?tableId=${tableId}`,
      method: "get",
    });
  }
  static createAggregateRule(data: any) {
    return request({
      url: `${BASE_URL}/createAggregateRule`,
      method: "post",
      data,
    });
  }
  static updateAggregateRule(data: any) {
    return request({
      url: `${BASE_URL}/updateAggregateRule`,
      method: "post",
      data,
    });
  }
  static deleteAggregateRule(aggregateRuledId: string) {
    return request({
      url: `${BASE_URL}/deleteAggregateRule?aggregateRuledId=${aggregateRuledId}`,
      method: "get",
    });
  }
  static getPageTableListByFieldId(fieldId: string) {
    return request({
      url: `${BASE_URL}/getByFieldId?fieldId=${fieldId}`,
      method: "get",
    });
  }
}

export default TableAPI;
