import request from "@/utils/request";

const BASE_URL = "/page-api/cfg/field";

class FieldAPI {
  static updateFieldById(data: any) {
    return request({
      url: `${BASE_URL}/updateById`,
      method: "post",
      data,
    });
  }
  static getFieldPropsByFieldSetId(id: any) {
    return request({
      url: `${BASE_URL}/listDefaultByFieldSetId?fieldSetId=${id}`,
      method: "get",
    });
  }
  static getFieldPropsByModelId(id: any) {
    return request({
      url: `${BASE_URL}/listDefaultByModelId?modelId=${id}`,
      method: "get",
    });
  }
  static getFieldPropsById(id: string) {
    return request({
      url: `${BASE_URL}/getById?id=${id}`,
      method: "get",
    });
  }
  static batchUpdateFieldsProps(data: any) {
    return request({
      url: `${BASE_URL}/batchUpdateDefault`,
      method: "post",
      data,
    });
  }
  static getFieldSortsByFieldSetId(id: any) {
    return request({
      url: `${BASE_URL}/listLocationByFieldSetId?fieldSetId=${id}`,
      method: "get",
    });
  }
  static batchUpdateFieldSorts(data: any) {
    return request({
      url: `${BASE_URL}/batchUpdateLocation`,
      method: "post",
      data,
    });
  }
  static createExclusiveField(data: any) {
    return request({
      url: `${BASE_URL}/createExclusiveField`,
      method: "post",
      data,
    });
  }
  static getSameTypeFieldList(fieldId: any) {
    return request({
      url: `${BASE_URL}/getSameTypeFieldList?fieldId=${fieldId}`,
      method: "get",
    });
  }
  static getSamePageFieldList(fieldId: any) {
    return request({
      url: `${BASE_URL}/getSamePageFieldList?fieldId=${fieldId}`,
      method: "get",
    });
  }
  static getSameCompTypeFields(
    componentTypeList: string,
    pageCode: string,
    bizIdentityCode: string = ""
  ) {
    return request.get(`${BASE_URL}/getByPageAndComponentType`, {
      params: {
        componentTypeList,
        pageCode,
        bizIdentityCode,
      },
    });
  }
  static getByModelId(modelId: string) {
    return request.get(`${BASE_URL}/getByModelId?modelId=${modelId}`);
  }
  static getByTableId(tableId: string) {
    return request.get(`${BASE_URL}/getByTableId?tableId=${tableId}`);
  }

  static getFieldDataSource(bizIdentityCode: string) {
    return request.get<any, FieldDataSource[]>(
      `${BASE_URL}/getFieldDataSource?bizIdentityCode=${bizIdentityCode}`
    );
  }

  static setFieldDataSource(data: FieldDataSourceRequest) {
    return request({
      url: `${BASE_URL}/setFieldDataSource`,
      method: "post",
      data,
    });
  }

  static getByFieldIdAndComponentType(data: {
    fieldId: string;
    componentType: string;
  }) {
    return request.get<any, any>(`${BASE_URL}/getByFieldIdAndComponentType`, {
      params: data,
    });
  }

  static getByTableFieldIdAndComponentType(data: {
    fieldId: string;
    componentType: string;
  }) {
    return request.get<any, any>(
      `${BASE_URL}/getByTableFieldIdAndComponentType`,
      {
        params: data,
      }
    );
  }

  static getExclusiveFieldList(bizIdentityCode: string) {
    return request.get<any, ExclusiveField[]>(
      `${BASE_URL}/listExclusiveField?bizIdentityCode=${bizIdentityCode}`
    );
  }

  static getFieldListByDisplay(modelId: string) {
    return request.get<any, any>(
      `${BASE_URL}/getFieldListByDisplay?modelId=${modelId}`
    );
  }

  static updateFieldDisplay(data: any) {
    return request.post<any, any>(`${BASE_URL}/updateFieldDisplay`, data);
  }
}

export default FieldAPI;

export interface FieldDataSource {
  fieldCode: string;
  fieldName: string;
  componentType: string;
  baseSourceType: number | null;
  baseSourceCode: string | null;
  baseSourceName: string | null;
  exclusiveSourceType: number | null;
  exclusiveSourceCode: string | null;
  exclusiveSourceName: string | null;
  extraConfig: ExtraConfig;
}

export interface FieldDataSourceRequest {
  bizIdentityCode: string;
  fieldCode: string;
  dataSourceType: number;
  dataSourceCode: string;
  extraConfig: ExtraConfig;
}

export interface ExtraConfig {
  optionSetOtherTag: boolean;
  optionSetOtherTitle: string;
  optionSetOtherNumberLimited: number;
  optionSetOtherRequired: boolean;
  optionSetOtherMatchValue: string;
}

export interface ExclusiveField {
  id: string;
  modelName: string;
  fieldName: string;
  fieldCode: string;
  componentType: string;
  createTime: string;
  createBy: string;
  updateTime: string;
  updateBy: string;
}
