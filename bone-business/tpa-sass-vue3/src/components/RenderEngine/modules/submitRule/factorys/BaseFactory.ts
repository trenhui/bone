import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";

export interface BaseFactory {
  /**
   * 执行函数
   * @param func 函数类型
   * @param fields 字段列表
   * @returns 执行结果
   */
  execute(func: FunctionEnum, fields: any[]): any;

  /**
   * 比较
   * @param sourceValue 源值
   * @param operator 操作符
   * @param targetValue 比较值
   * @returns 比较结果
   */
  compare(sourceValue: any, operator: OperatorEnum, targetValue: any): boolean;
}
