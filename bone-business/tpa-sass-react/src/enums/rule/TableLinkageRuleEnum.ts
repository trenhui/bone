export enum TableLinkageRuleEnum {
  IN_ROW_NUMERIC_MATCH_RULE = 1,
  CROSS_TABLE_NUMERIC_MATCH_RULE = 2,
}

export const TableLinkageRuleOptions = {
  [TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: {
    label: `当前表格行 <strong>数字文本字段</strong> 设置为 <strong>其他数字文本字段</strong> 通过 <strong>指定函数</strong> 得出的 <strong>值</strong>`,
    value: TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE,
  },
  [TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: {
    label: `目标表格 对应行 <strong>数字文本字段</strong> 设置为 </br> <strong>当前表格</strong> 对应行 <strong>数字文本字段</strong> 通过 <strong>指定函数</strong> 得出的 <strong>值</strong>`,
    value: TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE,
  },
};
