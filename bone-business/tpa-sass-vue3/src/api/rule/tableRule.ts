import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/tableRule";

class TableRuleAPI {
  static createLinkageRowRule(data: any) {
    return request({
      url: `${BASE_URL}/edit/createTableRowEditRule`,
      method: "post",
      data,
    });
  }
  static updateLinkageRowRule(data: any) {
    return request({
      url: `${BASE_URL}/edit/updateTableRowEditRule`,
      method: "post",
      data,
    });
  }
  static deleteLinkageRowRule(id: string) {
    return request({
      url: `${BASE_URL}/edit/deleteTableRowEditRule`,
      method: "get",
      params: { id },
    });
  }
  static getLinkageRowRule(tableId: string) {
    return request({
      url: `${BASE_URL}/edit/getTableRowEditRule`,
      method: "get",
      params: { tableId },
    });
  }
  static getTargetTableByCurrentTableId(tableId: string) {
    return request({
      url: `${BASE_URL}/edit/getTargetTableByCurrentTableId`,
      method: "get",
      params: { tableId },
    });
  }
  static createLinkageCrossTableRule(data: any) {
    return request({
      url: `${BASE_URL}/edit/createCrossTableDataEditRule`,
      method: "post",
      data,
    });
  }
  static updateLinkageCrossTableRule(data: any) {
    return request({
      url: `${BASE_URL}/edit/updateCrossTableDataEditRule`,
      method: "post",
      data,
    });
  }
  static getLinkageCrossTableRule(tableId: string, type: number) {
    return request({
      url: `${BASE_URL}/edit/getCrossTableDataEditRuleByTableId`,
      method: "get",
      params: { tableId, type },
    });
  }
  static deleteLinkageCrossTableRule(id: string) {
    return request({
      url: `${BASE_URL}/edit/deleteCrossTableDataEditRule`,
      method: "get",
      params: { id },
    });
  }
  static getTableRelationByTableId(tableId: string, type: number) {
    return request({
      url: `${BASE_URL}/getTableRelationByTargetTableId`,
      method: "get",
      params: { tableId, type },
    });
  }

  static createSubmitRowRule(data: any) {
    return request({
      url: `${BASE_URL}/verify/createTableRowVerifyRule`,
      method: "post",
      data,
    });
  }
  static updateSubmitRowRule(data: any) {
    return request({
      url: `${BASE_URL}/verify/updateTableRowVerifyRule`,
      method: "post",
      data,
    });
  }
  static deleteSubmitRowRule(id: string) {
    return request({
      url: `${BASE_URL}/verify/deleteTableRowVerifyRuleById`,
      method: "get",
      params: { id },
    });
  }
  static getSubmitRowRule(tableId: string) {
    return request({
      url: `${BASE_URL}/verify/getTableRowVerifyRuleByTableId`,
      method: "get",
      params: { tableId },
    });
  }

  static createSubmitCrossTableRule(data: any) {
    return request({
      url: `${BASE_URL}/verify/createCrossTableDataVerifyRule`,
      method: "post",
      data,
    });
  }
  static getSubmitCrossTableRule(tableId: string, type: number) {
    return request({
      url: `${BASE_URL}/verify/getCrossTableDataVerifyRule`,
      method: "get",
      params: { tableId, type },
    });
  }
  static updateSubmitCrossTableRule(data: any) {
    return request({
      url: `${BASE_URL}/verify/updateCrossTableDataVerifyRule`,
      method: "post",
      data,
    });
  }
  static deleteSubmitCrossTableRule(id: string) {
    return request({
      url: `${BASE_URL}/verify/deleteCrossTableDataVerifyRule`,
      method: "get",
      params: { id },
    });
  }
  static getTableLinkageRule(pageCode: string, bizIdentityCode?: string) {
    return request({
      url: `${BASE_URL}/edit/getTableEditRule`,
      method: "get",
      params: { pageCode, bizIdentityCode },
    });
  }
  static getTableSubmitRule(pageCode: string, bizIdentityCode?: string) {
    return request({
      url: `${BASE_URL}/verify/getTableVerifyRule`,
      method: "get",
      params: { pageCode, bizIdentityCode },
    });
  }
  static getTableAllRule(tableId: string) {
    return request({
      url: `${BASE_URL}/getFourRule`,
      method: "get",
      params: { tableId },
    });
  }
}

export default TableRuleAPI;
