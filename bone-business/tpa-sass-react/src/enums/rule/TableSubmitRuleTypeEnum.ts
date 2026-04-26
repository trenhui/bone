// 表格提交校验规则类型
export enum TableSubmitRuleType {
  IN_ROW = 1,
  CROSS_TABLE = 2,
}

export const TableSubmitRuleTypeLabel = {
  [TableSubmitRuleType.IN_ROW]: "当前表格行内多字段校验规则",
  [TableSubmitRuleType.CROSS_TABLE]: "当前表格行字段与其他表格行字段的校验规则",
};

export const TableSubmitRuleTypeOptions = [
  {
    label: TableSubmitRuleTypeLabel[TableSubmitRuleType.IN_ROW],
    value: TableSubmitRuleType.IN_ROW,
  },
  {
    label: TableSubmitRuleTypeLabel[TableSubmitRuleType.CROSS_TABLE],
    value: TableSubmitRuleType.CROSS_TABLE,
  },
];

export const getTableSubmitRuleTypeLabel = (type: TableSubmitRuleType) => {
  return TableSubmitRuleTypeLabel[type];
};
