
import { ValueTypeEnum } from "@/enums/rule/ValueTypeEnum";

export const getValueInData = (field: any, dataManager: any) => {
  return dataManager.getByJp(field.dataBinding);
};

export const setValueInData = (field: any, newVal: any, dataManager: any) => {
  dataManager.setByJp(field.dataBinding, newVal);
};

export const getComparedValue = (
  valueType: ValueTypeEnum,
  value: any,
  dataManager: any
) => {
  return valueType === ValueTypeEnum.fixed
    ? value
    : getValueInData(value, dataManager);
};
