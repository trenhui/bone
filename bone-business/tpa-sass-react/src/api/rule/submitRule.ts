import { FunctionTypeEnum } from "@/enums/rule/FunctionTypeEnum";
import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import request from "@/utils/request";
import { RuleStatusEnum } from "@/enums/rule/RuleStatusEnum";
import { RuleVerifyTypeEnum } from "@/enums/rule/RuleVerifyTypeEnum";

const BASE_URL = "/page-api/cfg/submitRule";

class SubmitRuleAPI {
  static createSubmitRule(data: SubmitRule) {
    return request.post(`${BASE_URL}/create`, data);
  }
  static getSubmitRuleByPage(pageCode: string, bizIdentityCode: string = "") {
    return request.get(`${BASE_URL}/getRuleByPage`, {
      params: {
        pageCode,
        bizIdentityCode,
      },
    });
  }
  static updateSubmitRule(data: SubmitRule) {
    return request.post(`${BASE_URL}/update`, data);
  }
}

export default SubmitRuleAPI;

export interface SubmitRule {
  id: string;
  fieldIdList: any[];
  functionType: FunctionTypeEnum;
  function: FunctionEnum;
  operator: string;
  valueType: string;
  value: string;
  status: RuleStatusEnum;
  errorPrompt: string;
  pageCode: string;
  bizIdentityCode: string;
  verifyType: RuleVerifyTypeEnum;
}
