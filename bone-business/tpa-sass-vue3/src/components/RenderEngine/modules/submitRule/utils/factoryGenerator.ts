import { FunctionTypeEnum } from "@/enums/rule/FunctionTypeEnum";
import { BaseFactory } from "../factorys/BaseFactory";
import { NumberFactory } from "../factorys/NumberFactory";

export const createRuleFactory = (type: FunctionTypeEnum): BaseFactory => {
  switch (type) {
    case FunctionTypeEnum.NUMBER:
      return new NumberFactory();
    default:
      throw new Error(`未知的规则类型【${type}】`);
  }
};
