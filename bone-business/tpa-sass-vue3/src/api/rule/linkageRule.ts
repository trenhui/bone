import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { PropertyOrValueEnum } from "@/enums/rule/PropertyOrValueEnum";
import { RuleVerifyTypeEnum } from "@/enums/rule/RuleVerifyTypeEnum";
import { ValueTypeEnum } from "@/enums/rule/ValueTypeEnum";
import request from "@/utils/request";

const BASE_URL = "/page-api/cfg";

class LinkageRuleAPI {
  static getRuleByPageCode(code: string, bizIdentityCode: string = "") {
    return request<any, LinkageRule[]>({
      url: `${BASE_URL}/fieldLinkageRule/getRuleByPageCodeAndBizIdentityCode?pageCode=${code}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
  static updateRule(data: LinkageRule) {
    return request({
      url: `${BASE_URL}/fieldLinkageRule/update`,
      method: "post",
      data,
    });
  }
  static createRule(data: LinkageRule) {
    return request({
      url: `${BASE_URL}/fieldLinkageRule/create`,
      method: "post",
      data,
    });
  }
  static getRuleByFieldId(fieldId: string) {
    return request<any, LinkageRule[]>({
      url: `${BASE_URL}/fieldLinkageRule/getRuleByFieldId?fieldId=${fieldId}`,
      method: "get",
    });
  }

  /**
   * 获取当前字段主导的字段-表格联动规则
   * @param fieldId
   * @returns
   */
  static getFieldTableRuleByFieldId(fieldId: string) {
    return request<any, FieldTableRule[]>({
      url: `${BASE_URL}/fieldTableRule/getRuleByFieldId?fieldId=${fieldId}`,
      method: "get",
    });
  }

  /**
   * 创建字段-表格联动规则
   * @param data
   * @returns
   */
  static createFieldTableRule(data: FieldTableRule) {
    return request({
      url: `${BASE_URL}/fieldTableRule/create`,
      method: "post",
      data,
    });
  }

  /**
   * 更新字段-表格联动规则
   * @param data
   * @returns
   */
  static updateFieldTableRule(data: FieldTableRule) {
    return request({
      url: `${BASE_URL}/fieldTableRule/update`,
      method: "post",
      data,
    });
  }

  /**
   * 删除字段-表格联动规则
   * @param ruleId
   * @returns
   */
  static deleteFieldTableRule(ruleId: string) {
    return request({
      url: `${BASE_URL}/fieldTableRule/deleteById?ruleId=${ruleId}`,
      method: "get",
    });
  }

  /**
   * 获取指定页面的字段-表格联动规则接口，get请求， /fieldTableRule/getRuleByPageCodeAndBizIdentityCode，参数：pageCode、bizIdentityCode
   * @param pageCode
   * @param bizIdentityCode
   * @returns
   */
  static getFieldTableRule(pageCode: string, bizIdentityCode: string) {
    return request<any, FieldTableRule[]>({
      url: `${BASE_URL}/fieldTableRule/getRuleByPageCodeAndBizIdentityCode?pageCode=${pageCode}&bizIdentityCode=${bizIdentityCode}`,
      method: "get",
    });
  }
}

export default LinkageRuleAPI;

/** 字段字段联动规则响应 */
export interface LinkageRule {
  id: string;
  fieldId: string;
  componentType: BaseCompType;
  sourceOperator: OperatorEnum;
  sourceValueType: ValueTypeEnum;
  sourceValue: any;
  sourceValueCn: string;
  targetFields: any[];
  propertyOrValue: PropertyOrValueEnum;
  targetFieldPropertyName: string;
  targetFieldPropertyValue: any;
  targetOperator: OperatorEnum;
  targetValueType: ValueTypeEnum;
  targetValue: any;
  targetValueCn: string;
  errorPrompt: string;
  genericOrExclusive: number;
  status: number;
  bizName: string;
  modelName: string;
  verifyType: RuleVerifyTypeEnum;

  /** 描述 前端展示用*/
  describe: string;
}

/** 字段表格联动规则响应 */
export interface FieldTableRule {
  id: string;
  /**当前字段id*/
  fieldId: string;
  /**当前字段*/
  currentField: any;
  /**当前字段操作符*/
  sourceOperator: OperatorEnum;
  /**0:固定值 1:动态值*/
  sourceValueType: ValueTypeEnum;
  /**固定值文本或动态字段id*/
  sourceValue: any;
  /**固定值文本*/
  sourceValueCn: string;
  /**目标表格id*/
  tableId: string;
  /**目标表格*/
  targetTable: any;
  /**目标表格属性名*/
  attributeName: string;
  /**目标表格属性值*/
  attributeValue: any;
  /**状态 0:禁用 1:启用*/
  status: number;
  /**规则性质 0:通用规则 1:专属规则*/
  genericOrExclusive: number;
  /**业务模型*/
  modelName: string;
}
