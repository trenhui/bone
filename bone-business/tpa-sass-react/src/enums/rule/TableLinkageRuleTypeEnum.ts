// 表格联动规则类型
export enum TableLinkageRuleType {
  // 当前表格行内多字段联动
  IN_ROW = 1,
  // 当前表格行字段影响其他表格行字段的动态规则
  CROSS_TABLE = 2,
}

export const TableLinkageRuleTypeLabel = {
  [TableLinkageRuleType.IN_ROW]: "当前表格行内多字段动态规则",
  [TableLinkageRuleType.CROSS_TABLE]:
    "当前表格行字段影响其他表格行字段的动态规则",
};

export const TableLinkageRuleTypeOptions = [
  {
    label: TableLinkageRuleTypeLabel[TableLinkageRuleType.IN_ROW],
    value: TableLinkageRuleType.IN_ROW,
  },
  {
    label: TableLinkageRuleTypeLabel[TableLinkageRuleType.CROSS_TABLE],
    value: TableLinkageRuleType.CROSS_TABLE,
  },
];

export const getTableLinkageRuleTypeLabel = (
  type: TableLinkageRuleType | number
) => {
  return TableLinkageRuleTypeLabel[type as TableLinkageRuleType];
};
