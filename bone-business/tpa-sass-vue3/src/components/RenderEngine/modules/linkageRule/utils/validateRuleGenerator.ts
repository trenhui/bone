import { createComparator } from "./comparatorGenerator";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { OperatorEnum, isUnaryOperator } from "@/enums";
import { ValueTypeEnum } from "@/enums";
import { getValueInData, getComparedValue } from "../../../utils/dataUtil";
import { RuleVerifyTypeEnum } from "@/enums/rule/RuleVerifyTypeEnum";
import { getRuleVerifyTypeShortLabel } from "@/enums/rule/RuleVerifyTypeEnum";

/**
 * 创建校验规则
 * @param sourceCompType 源组件类型
 * @param sourceOperator 源操作符
 * @param sourceValueType 源值类型
 * @param sourceValue 源比较值
 * @param targetField 目标字段
 * @param targetOperator 目标操作符
 * @param targetValueType 目标值类型
 * @param targetValue 目标比较值
 * @param trigger 触发条件
 * @param errorMessage 错误提示
 * @param dataManager 数据管理器
 * @param title 源字段标题
 * @param verifyType 校验类型
 * @returns 校验规则
 */
export function createRule(
  sourceCompType: BaseCompType,
  sourceOperator: OperatorEnum,
  sourceValueType: ValueTypeEnum,
  sourceValue: any,
  targetField: any,
  targetOperator: OperatorEnum,
  targetValueType: ValueTypeEnum,
  targetValue: any,
  trigger: string = "blur",
  errorMessage: string = "请检查字段关联的规则是否满足！",
  dataManager: any,
  title: string,
  verifyType: RuleVerifyTypeEnum
) {
  const sourceComparator = createComparator(sourceCompType);
  const targetComparator = createComparator(targetField.componentType);

  return {
    validator: (rule: any, value: any, callback: any) => {
      let sourceComparedValue = undefined;
      if (!isUnaryOperator(sourceOperator)) {
        sourceComparedValue = getComparedValue(
          sourceValueType,
          sourceValue,
          dataManager
        );
      }

      const targetFieldValue = getValueInData(targetField, dataManager);

      let targetComparedValue = undefined;
      if (!isUnaryOperator(targetOperator)) {
        targetComparedValue = getComparedValue(
          targetValueType,
          targetValue,
          dataManager
        );
      }

      if (
        sourceComparator.compare(value, sourceComparedValue, sourceOperator)
      ) {
        if (
          targetComparator.compare(
            targetFieldValue,
            targetComparedValue,
            targetOperator
          )
        ) {
          callback();
        } else {
          callback(
            /**
             * 例如：【强制】出险人姓名：请输入正确的姓名
             */
            new Error(
              `【${getRuleVerifyTypeShortLabel(verifyType)}】${title}：${errorMessage || "请检查字段关联的规则是否满足！"}`
            )
          );
        }
      } else {
        callback();
      }
    },
    trigger: trigger,
  };
}
