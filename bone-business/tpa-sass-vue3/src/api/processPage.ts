import request from "@/utils/request";
import { UploadComponent } from "@/api/upload";

const PROCESS_PAGE_BASE_URL = "/page-api/cfg/processPage";

class ProcessPageAPI {
  static getProcessListPage(code: string, bizIdentityCode: string = "") {
    return request<any, ProcessListConfig>({
      url: `${PROCESS_PAGE_BASE_URL}/getProcessListPage?code=${code}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static updateProcessListPage(data: UpdateProcessListConfig) {
    return request({
      url: `${PROCESS_PAGE_BASE_URL}/updateProcessListPage`,
      method: "post",
      data,
    });
  }

  // 仅用于签收详情
  static getProcessDetailPage(bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${PROCESS_PAGE_BASE_URL}/getProcessDetailPage`,
      method: "get",
      params: {
        bizIdentityCode,
      },
    });
  }

  //仅用于初审详情
  static getPrecheckDetailPage(bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${PROCESS_PAGE_BASE_URL}/getFirstAuditDetailPage`,
      method: "get",
      params: {
        code: "firstAuditDetail",
        bizIdentityCode,
      },
    });
  }
  static updatePrecheckDetailPage(data: UpdatePrecheckDetailPage) {
    return request({
      url: `${PROCESS_PAGE_BASE_URL}/updateFirstAuditDetailPage`,
      method: "post",
      data,
    });
  }

  static getNewSignPage(displayMode: string, bizIdentityCode: string = "") {
    return request<any, any>({
      url: `${PROCESS_PAGE_BASE_URL}/getNewSignPage`,
      method: "get",
      params: {
        displayMode,
        code: "newSign",
        bizIdentityCode,
      },
    });
  }
}

export default ProcessPageAPI;

// 用于列表配置
export interface UpdateProcessListConfig {
  /** 页面ID */
  pageId: string;

  /** 页面介绍 */
  desc: string;

  /** 是否开启页头,0:否,1:是 */
  enablePageHead: number;
  /** 页头字段信息 */
  pageHeadFieldList: any[];

  /** 是否开启tab页,0:否,1:是 */
  enableTab: number;
  /** tab页条件列表 */
  tabConditionList: TabCondition[];

  /** 数据范围,1:全局,2:根据账号,3:根据字段 */
  dataRange: number;

  /** 表格ID */
  tableId: string;

  /** 是否开启搜索栏,0:否,1:是 */
  enableSearch: number;
  /** 表格搜索字段 */
  searchFieldList: any[];
}

export interface ProcessListConfig {
  /** 页面数据模型 */
  modelNameList: string[];

  /** 页面介绍说明 */
  pageBaseInfo: {
    id: string;
    code: string;
    name: string;
    description: string;
  };

  /** 页头字段信息 */
  pageHead: {
    enablePageHead: number;
    pageHeadField?: any[];
    pageHeadModelId?: string;
    modelCode?: string;
  };

  /** 数据范围,1:全局,2:根据账号,3:根据字段 */
  dataRange: number;

  /** 是否开启tab页,0:否,1:是 */
  enableTab: number;
  /** tab页条件列表 */
  tabConditionList: TabCondition[];

  /** 页面主体信息 */
  pageBody: any;

  /** 导入组件 */
  uploadComponentList?: UploadComponent[];

  /** 页面主体数据模型ID */
  tableId: string;
}

export interface TabCondition {
  title: string;
  fieldId: string;
  field?: any;
  fieldValue: string;
  isEditing?: boolean;
  isNew?: boolean;
}

export interface PrecheckDetailConfig {
  id: string;
  name: string;
  description: string;
  modelNameList: string[];
  pageHead: {
    enablePageHead: number;
    pageHeadField?: any[];
    pageHeadModelId?: string;
    modelCode?: string;
  };
  /** 打开详情弹窗,0:否,1:是 */
  openDetail: number;
  /** 初审标准规范 */
  specification: string;
  /** 清晰提示文案 */
  tip: string;
  /** 影像清晰码值 */
  imageQuality: number;
  /** 影像分类码值 */
  imageType: number;
}

export interface UpdatePrecheckDetailPage {
  id: string;
  /** 页面介绍说明 */
  description: string;
  /** 是否开启页头,0:否,1:是 */
  enablePageHead: number;
  /** 页头字段信息 */
  pageHeadFieldList: any[];
  /** 打开详情弹窗,0:否,1:是 */
  openDetail: number;
  /** 初审标准规范 */
  specification: string;
  /** 清晰提示文案 */
  tip: string;
  /** 影像清晰码值 */
  imageQuality: number;
  /** 影像分类码值 */
  imageType: number;
}
