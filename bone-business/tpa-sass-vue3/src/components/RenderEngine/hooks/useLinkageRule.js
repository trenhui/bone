import { OperatorEnum, isUnaryOperator } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";
import { createComparator } from "../modules/linkageRule/utils/comparatorGenerator";
import { PropertyOrValueEnum } from "@/enums/rule/PropertyOrValueEnum";
import { createRule } from "../modules/linkageRule/utils/validateRuleGenerator";
import { setValueInData, getComparedValue } from "../utils/dataUtil";
import EventBus from "@/utils/eventBus";
import { nextTick } from "vue";
import { useComponentPropertyStoreHook } from "@/store";

const checkRule = (
  value,
  sourceOperator,
  sourceValueType,
  sourceValue,
  baseCompType,
  dataManager
) => {
  if (!isValidOperator(baseCompType, sourceOperator)) {
    return false;
  }

  let comparedValue = undefined;

  //判断是否为单目运算符
  if (!isUnaryOperator(sourceOperator)) {
    comparedValue = getComparedValue(sourceValueType, sourceValue, dataManager);
  }

  const comparator = createComparator(baseCompType);

  console.log(value, comparedValue, sourceOperator);

  return comparator.compare(value, comparedValue, sourceOperator);
};

const excuteRule = (
  targetField,
  propertyOrValue,
  targetFieldPropertyName,
  targetFieldPropertyValue,
  targetOperator,
  targetValueType,
  targetValue,
  componentManager,
  dataManager
) => {
  if (propertyOrValue === PropertyOrValueEnum.property) {
    //处理属性改变的情况
    const store = useComponentPropertyStoreHook();
    store.updateProperty(
      targetField.id,
      targetFieldPropertyName,
      targetFieldPropertyValue
    );
  } else {
    let comparedValue = null;
    //判断是否为单目运算符
    if (!isUnaryOperator(targetOperator)) {
      comparedValue = getComparedValue(
        targetValueType,
        targetValue,
        dataManager
      );
    }

    //处理值改变的情况
    if (
      targetOperator === OperatorEnum.NULL ||
      targetOperator === OperatorEnum.EQ
    ) {
      setValueInData(targetField, comparedValue, dataManager);
      return;
    }
  }
};

export const useLinkageRule = (
  compType,
  rules,
  componentManager,
  dataManager,
  title
) => {
  const validateRules = [];

  const generateValidateRules = () => {
    if (rules && rules.length > 0) {
      for (const rule of rules) {
        const {
          sourceOperator,
          sourceValueType,
          sourceValue,
          targetFields,
          propertyOrValue,
          targetOperator,
          targetValueType,
          targetValue,
          errorPrompt,
          verifyType,
        } = rule;

        if (propertyOrValue === PropertyOrValueEnum.property) {
          continue;
        }

        const validateRule = createRule(
          compType,
          sourceOperator,
          sourceValueType,
          sourceValue,
          targetFields[0],
          targetOperator,
          targetValueType,
          targetValue,
          "blur",
          errorPrompt,
          dataManager,
          title,
          verifyType
        );

        validateRules.push(validateRule);
      }
    }
  };

  generateValidateRules();

  const handleLinkageRules = (value) => {
    if (rules && rules.length > 0) {
      for (const rule of rules) {
        const { sourceOperator, sourceValueType, sourceValue } = rule;

        if (
          checkRule(
            value,
            sourceOperator,
            sourceValueType,
            sourceValue,
            compType,
            dataManager
          )
        ) {
          //如果满足条件，则设置目标字段的值
          const {
            targetFields,
            targetFieldPropertyName,
            targetFieldPropertyValue,
            propertyOrValue,
            targetOperator,
            targetValueType,
            targetValue,
          } = rule;
          for (const item of targetFields) {
            excuteRule(
              item,
              propertyOrValue,
              targetFieldPropertyName,
              targetFieldPropertyValue,
              targetOperator,
              targetValueType,
              targetValue,
              componentManager,
              dataManager
            );
          }
        }
      }
    }
  };

  return { validateRules, handleLinkageRules };
};
