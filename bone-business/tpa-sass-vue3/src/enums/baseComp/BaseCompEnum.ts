/**
 * 基础组件类型
 */
export enum BaseCompType {
  Input = "Input",
  InputNum = "InputNum",
  SelectDrop = "SelectDrop",
  SelectCtrl = "SelectCtrl",
  DateTime = "DateTime",
  DateRange = "DateRange",
}

/**
 * 基础组件类型名称
 */
export enum BaseCompTypeNames {
  Input = "单行输入",
  InputNum = "数字输入",
  SelectDrop = "下拉选择",
  SelectCtrl = "联动选择",
  DateTime = "日期时间",
  DateRange = "日期期间",
}

export const BaseCompTypeOptions = [
  { label: BaseCompTypeNames.Input, value: BaseCompType.Input },
  { label: BaseCompTypeNames.InputNum, value: BaseCompType.InputNum },
  { label: BaseCompTypeNames.SelectDrop, value: BaseCompType.SelectDrop },
  { label: BaseCompTypeNames.SelectCtrl, value: BaseCompType.SelectCtrl },
  { label: BaseCompTypeNames.DateTime, value: BaseCompType.DateTime },
  { label: BaseCompTypeNames.DateRange, value: BaseCompType.DateRange },
];

export const getBaseCompTypeName = (type: string | BaseCompTypeNames) => {
  return BaseCompTypeNames[type as keyof typeof BaseCompTypeNames];
};

export const isBaseCompType = (type: string): type is BaseCompType => {
  return Object.values(BaseCompType).includes(type as BaseCompType);
};
