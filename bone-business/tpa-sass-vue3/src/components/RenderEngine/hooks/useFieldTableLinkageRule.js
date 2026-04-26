import { isUnaryOperator } from "@/enums/rule/OperatorEnum";
import { isValidOperator } from "@/enums/rule/LinkageRuleMapping";
import { createComparator } from "../modules/linkageRule/utils/comparatorGenerator";
import { getComparedValue } from "../utils/dataUtil";
import EventBus from "@/utils/eventBus";
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

  return comparator.compare(value, comparedValue, sourceOperator);
};

const excuteRule = (
  targetTableId,
  attributeName,
  attributeValue,
  componentManager
) => {
  const store = useComponentPropertyStoreHook();
  store.updateProperty(targetTableId, attributeName, attributeValue);
};

export const useFieldTableLinkageRule = (
  compType,
  rules,
  componentManager,
  dataManager
) => {
  const handleFieldTableLinkageRules = (value) => {
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
          //如果满足条件，则设置目标表的属性值
          const { targetTable, attributeName, attributeValue } = rule;
          excuteRule(
            targetTable.id,
            attributeName,
            attributeValue,
            componentManager
          );
        }
      }
    }
  };

  return { handleFieldTableLinkageRules };
};
