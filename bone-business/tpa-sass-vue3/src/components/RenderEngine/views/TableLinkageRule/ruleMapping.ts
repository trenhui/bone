import { TableLinkageRuleEnum } from "@/enums/rule/TableLinkageRuleEnum";

const componentMap = {
  [TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: defineAsyncComponent(
    () => import("./components/InRowNumericMatchRule.vue")
  ),

  [TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: defineAsyncComponent(
    () => import("./components/CrossTableNumericMatchRule.vue")
  ),
};

export const getRuleComponent = (rule: TableLinkageRuleEnum) => {
  return componentMap[rule];
};
