import type { Violation } from './types';

/**
 * 违规清单的界面投影。
 *
 * <p>**不要在前端重写校验规则**：后端 `WorkflowDefinitionValidator` 有 20 条 `WF-xxx`，前端若照抄一份，
 * 两份规则必然漂移，且漂移方向不可控——要么「前端放过、后端拒绝」（用户白填一遍），要么更糟
 * 「前端拒绝、后端放过」（用户以为已经修好）。
 *
 * <p>所以前端只做两件事：把后端给的清单**按定位分组**（点一下高亮到画布节点），以及为高频错误码
 * 补一句「怎么办」的提示文案。判据本身永远来自 `POST /definitions/validate`。
 */
export interface ViolationHint {
  /** 一句人话的修复建议；未知错误码时为 null，界面直接展示后端 message。 */
  suggestion: string | null;
}

const SUGGESTIONS: Record<string, string> = {
  'WF-002': '流程必须有且只有一个发起节点；发起节点由系统自动生成，不需要手动添加',
  'WF-003': '流程末尾缺少结束节点，检查最后一条路径是否断头',
  'WF-005': '该节点没有后继，流程会在这里卡死；补一个后续节点或连到结束',
  'WF-006': '条件节点需要至少一条分支 + 一条默认边，且默认边必须放在最后',
  'WF-007': '审批节点必须配置审批人，否则单据无人可审',
  'WF-012': '审批节点必须选择完成方式（单签 / 会签 / 或签 / 比例会签）',
  'WF-013': '指派策略不在支持列表内，请从下拉里重新选择',
  'WF-014': '审批节点只能有一条出边；需要分流请改用「条件分支」节点',
  'WF-018': '抄送节点需要配置抄送对象，否则抄送不会发出',
  'WF-019': '比例会签必须指定「至少 N 人通过」，N 不能小于 1',
  'WF-020': '指派策略缺少必填参数（角色 / 人员 / 字段），运行时会解析不出审批人并挂起',
  'WF-021': '定义 JSON 无法解析，通常是手工编辑出错；请从版本记录里重新打开',
};

/** 按节点编码分组；整图级违规（无 locator）归到 `null` 键。 */
export function groupViolationsByNode(
  violations: Violation[] | undefined | null,
): Map<string | null, Violation[]> {
  const grouped = new Map<string | null, Violation[]>();
  for (const violation of violations ?? []) {
    const key = violation.locator ?? null;
    const list = grouped.get(key) ?? [];
    list.push(violation);
    grouped.set(key, list);
  }
  return grouped;
}

export function hintOf(violation: Violation): ViolationHint {
  return { suggestion: SUGGESTIONS[violation.code] ?? null };
}

/** 是否可发布（后端结论的极简投影，避免界面各处重复写 `violations.length === 0`）。 */
export function isPublishable(violations: Violation[] | undefined | null): boolean {
  return (violations ?? []).length === 0;
}
