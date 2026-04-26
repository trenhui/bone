import request from "@/utils/request";

const FILE_BASE_URL = "/data-api";

class FileAPI {
  /**
   * 上传文件
   * @param data
   * @returns
   */
  static uploadFile(data: FormData, url?: string) {
    return request({
      url: FILE_BASE_URL + (url || "/tpa/file/upload"),
      method: "post",
      data,
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  /**
   * 批量上传文件url
   * @param data
   * @returns
   */
  static uploadBatchFile(data: any) {
    return request<any, any>({
      url: FILE_BASE_URL + "/tpa/file/uploadbatch",
      method: "post",
      data,
    });
  }

  /**
   * 获取模板
   * @param id
   * @returns
   */
  static getTemplate(id: string) {
    return request<any, string>({
      url: FILE_BASE_URL + "/tpa/file/template",
      method: "get",
      params: { id },
    });
  }

  /**
   * 查询导入记录
   * @param data
   * @returns
   */
  static queryUploadRecordList(data: any) {
    return request<any, PagedResult<IUploadRecord>>({
      url: FILE_BASE_URL + "/tpa/file/query",
      method: "post",
      data,
    });
  }

  /**
   * 获取导入记录
   * @param id
   * @returns
   */
  static getRecord(id: string, type: string, tenantId: string) {
    return request<any, any>({
      url: FILE_BASE_URL + "/tpa/file/getRecord",
      method: "get",
      params: { id, type, tenantId },
    });
  }
}

export default FileAPI;

export interface PagedResult<T> {
  data: T[];
  totalCount: number;
  pageSize: number;
  totalPage: number;
  currPage: number;
}

export interface IUploadRecord {
  id: string;
  tenantId: string;
  createTime: string;
  createBy: number;
  updateTime: string;
  updateBy: number;
  result: string;
  fileType: string;
  fileName: string;
  checkType: string;
  filePath: string;
  uploadScene: string;
}
