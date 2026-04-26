export enum TableSubmitRuleEnum {
  IN_ROW_NUMERIC_MATCH_RULE = 1,
  CROSS_TABLE_NUMERIC_MATCH_RULE = 2,
}

export const TableSubmitRuleOptions = {
  [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: {
    label: `当前表格行 <strong>数字文本字段</strong> 通过 <strong>指定函数</strong> 得出的 <strong>值</strong> 应当 <strong>如何</strong>`,
    value: TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE,
  },
  [TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: {
    label: `目标表格 对应行 <strong>数字文本字段</strong> 和 <strong>当前表格</strong> 对应行 <strong>数字文本字段</strong> 通过 <strong>指定函数</strong> 得出的 <strong>值</strong> 应当 <strong>如何</strong>`,
    value: TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE,
  },
};
