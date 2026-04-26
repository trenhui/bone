import { TableSubmitRuleEnum } from "@/enums/rule/TableSubmitRuleEnum";

const componentMap = {
  [TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE]: defineAsyncComponent(
    () => import("./components/InRowNumericMatchRule.vue")
  ),

  [TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE]: defineAsyncComponent(
    () => import("./components/CrossTableNumericMatchRule.vue")
  ),
};

export const getRuleComponent = (rule: TableSubmitRuleEnum) => {
  return componentMap[rule];
};
