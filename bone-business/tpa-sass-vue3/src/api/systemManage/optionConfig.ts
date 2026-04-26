import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/optionSet";

class OptionConfigAPI {
  static getOptionList(pageNum: number, pageSize: number) {
    return request<any, any>({
      url: `${BASE_URL}/getSetList`,
      method: "get",
      params: {
        pageNum,
        pageSize,
      },
    });
  }

  static getOptionDetail(id: string) {
    return request<any, IOptionDetail>({
      url: `${BASE_URL}/getSetDetail`,
      method: "get",
      params: {
        optionSetId: id,
      },
    });
  }

  static getOptionValueList(
    optionSetId: string,
    pageNum: number,
    pageSize: number,
    optionCode: string = "",
    optionName: string = ""
  ) {
    return request<any, IOptionValue>({
      url: `${BASE_URL}/valuePage`,
      method: "get",
      params: { optionSetId, pageNum, pageSize, optionCode, optionName },
    });
  }

  static deleteOptionValue(valueId: string) {
    return request({
      url: `${BASE_URL}/deleteValue`,
      method: "get",
      params: { valueId },
    });
  }

  static updateOptionValue(data: IOptionValueItem) {
    return request({
      url: `${BASE_URL}/updateValue`,
      method: "post",
      data: {
        valueId: data.id,
        valueName: data.name,
        extraProperty: data.extraProperty,
        status: data.status,
      },
    });
  }

  static addOptionValue(optionSetId: string, data: IOptionValueItem) {
    return request({
      url: `${BASE_URL}/addValue`,
      method: "post",
      data: {
        optionSetId,
        valueName: data.name,
        valueCode: data.code,
        extraProperty: data.extraProperty,
        status: data.status,
      },
    });
  }

  static updateOptionSet(data: {
    optionSetId: string;
    setDesc?: string;
    extraPropertyKey?: string;
  }) {
    return request({
      url: `${BASE_URL}/updateOptionSet`,
      method: "post",
      data: data,
    });
  }

  static createOption(data: ICreateOption) {
    return request({
      url: `${BASE_URL}/addOptionSet`,
      method: "post",
      data,
    });
  }
  static addOptionSetVersion(id: string, description: string) {
    return request({
      url: `${BASE_URL}/addOptionSetVersion`,
      method: "post",
      data: {
        optionSetId: id,
        description,
      },
    });
  }
  static getOptionSetVersionList(id: string) {
    return request<any, IOptionSetVersion[]>({
      url: `${BASE_URL}/getSetVersionList`,
      method: "get",
      params: {
        optionSetId: id,
      },
    });
  }

  static deleteOptionSet(optionSetId: string) {
    return request({
      url: `${BASE_URL}/delete`,
      method: "get",
      params: {
        optionSetId,
      },
    });
  }

  static getFieldLinkedDisplayRule(
    selectFieldId: string,
    datasourceType: number,
    datasourceCode: string
  ) {
    return request<any, any>({
      url: `${BASE_URL}/getFieldLinkedDisplayRule`,
      method: "get",
      params: { selectFieldId, datasourceType, datasourceCode },
    });
  }

  static updateFieldLinkedDisplayRule(data: IUpdateLinkedOptionField) {
    return request<any, any>({
      url: `${BASE_URL}/updateFieldLinkedDisplayRule`,
      method: "post",
      data,
    });
  }

  static uploadFile(data: FormData) {
    return request({
      url: "/page-api/cfg/uploadcommon/createUploadTask",
      method: "post",
      data,
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  }

  static getTemplate(id: string) {
    return request<any, string>({
      url: "/page-api/cfg/uploadcommon/getTemplate",
      method: "get",
      params: { id },
    });
  }
}

export default OptionConfigAPI;

export interface ICreateOption {
  optionSetId: string;
  setName: string;
  setCode: string;
  setDesc: string;
  lastVersion: string;
  extraPropertyKey?: string;
  valueList: ICreateOptionValue[];

  extraPropertyKeyArr?: string[];
}

export interface ICreateOptionValue {
  valueId?: string;
  valueCode: string;
  valueName: string;
  // 0: 禁用 1: 启用
  valueEnable: number;
  extraProperty?: string;

  // 仅前端使用
  extraPropertyObj?: Record<string, string>;
  _tempId?: string;
  isAdding?: boolean;
  isUpdating?: boolean;
}

export interface IOptionSetVersion {
  id: number;
  optionSetId: number;
  optionSetName: string;
  description: string;
  version: string;
  createBy: string;
  createTime: string;
}

export interface IUpdateLinkedOptionField {
  selectFieldId: string;
  datasourceType: number;
  datasourceCode: string;
  entryList: IEntryList[];
}

export interface IEntryList {
  type: number; // 0:扩展字段 1:脚本字段
  extraProperty: string;
  fieldId: string;
  script?: string;
}

export interface IOptionDetail {
  id: string;
  name: string;
  code: string;
  desc: string;
  useScope: number;
  status: number;
  latestVersion: string;
  extraPropertyKey: string[];
}

export interface IOptionValue {
  extraPropertyKey?: string;
  extraPropertyKeyArr?: string[];
  optionValueInfo: {
    pageNum: number;
    pageSize: number;
    totalSize: number;
    rows: IOptionValueItem[];
  };
}

export interface IOptionValueItem {
  id: string;
  code: string;
  name: string;
  extraProperty?: string;
  extraPropertyObj?: Record<string, string>;
  status: number;

  // 仅前端使用
  isAdding?: boolean;
  isUpdating?: boolean;
}
