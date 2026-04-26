import request from "@/utils/request";

const DICT_BASE_URL = "/page-api/cfg/data";

class PkDictAPI {
  /**
   * 获取字典类型
   * @param data
   * @returns
   */
  static getTypes(data: any) {
    return request<any, any>({
      url: `${DICT_BASE_URL}/types/page`,
      method: "get",
      params: data,
    });
  }

  /**
   * 根据code获取字典类型
   * @param code
   * @returns
   */
  static getTypeByCode(type: number, code: string) {
    return request<any, DictItem>({
      url: `${DICT_BASE_URL}/type`,
      method: "get",
      params: { type, code },
    });
  }

  /**
   * 获取字典项
   * @param data
   * data = {
   *  type: number,
   *  parentCode: string,
   *  name: string,
   *  pageNum: number,
   *  pageSize: number,
   * }
   * @returns
   */
  static getByType(data: any) {
    return request<any, DictPageVO>({
      url: `${DICT_BASE_URL}/page`,
      method: "get",
      params: data,
    });
  }

  /**
   * 根据code获取字典项
   * @param type
   * @param codeList
   * @returns
   */
  static getByCode(type: number, dictType: string, codeList: string[]) {
    return request<any, DictItem[]>({
      url: `${DICT_BASE_URL}/listByCode`,
      method: "post",
      data: {
        type,
        rootCode: dictType,
        codeList,
      },
    });
  }

  /**
   * 批量获取字典项
   * @param paramList
   * @returns
   */
  static batchGetByType(
    paramList: { type: number; parentCode: string }[],
    pageNum: number = 1,
    pageSize: number = 50
  ) {
    return request<any, DictBatchVO[]>({
      url: `${DICT_BASE_URL}/batch`,
      method: "post",
      data: { pageNum, pageSize, paramList },
    });
  }
}

export default PkDictAPI;

export interface DictPageVO {
  totalSize: number;
  pageSize: number;
  pageNum: number;
  rows: DictItem[];
}

export interface DictItem {
  type: number;
  code: string;
  name: string;
  extraProperty: Record<string, string>;
}

export interface DictBatchVO {
  type: number;
  parentCode: string;
  data: DictItem[];
  pageNum: number;
  pageSize: number;
  totalSize: number;
}
