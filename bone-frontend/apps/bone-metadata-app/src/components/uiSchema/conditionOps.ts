/**
 * 嵌套条件（all / any / not）树操作的纯函数集。
 *
 * 为什么需要：此前嵌套条件只有 JSON 一条路——审核者看不懂、手写易错、
 * 「切回可视化丢结构」不可逆。本模块把协议 UiCondition 的组结构操作收敛为
 * 纯函数，可视化树（ConditionEditor GroupTree）与 JSON 模式共用同一模型，
 * 两种模式可无损互切（对标 Retool/Appsmith 的可视化条件树）。
 *
 * 数据模型即协议形态，无第二表示：
 * - 组：`{ all: [子, 子] }`（全部满足）/ `{ any: [...] }`（任一满足）/ `{ not: 子 }`（取反，单子）
 * - 叶：`{ fieldCode, operator, value, ... }`
 *
 * 深度纪律：MAX_GROUP_DEPTH 与引擎 `MAX_COND_DEPTH`(=3) 对齐——
 * 超限的嵌套在引擎里 fail-closed 不命中（防误配出「永远不生效的规则」）。
 */
import type { UiCondition } from '../../utils/uiSchemaEngine';

/** 组嵌套深度上限（root 组 = 1）。与引擎 MAX_COND_DEPTH 对齐，超限分支引擎 fail-closed */
export const MAX_GROUP_DEPTH = 3;

/** 是否为组（含 not） */
export const isGroup = (c?: UiCondition): boolean => !!c && (!!c.all || !!c.any || !!c.not);

/** 空叶（待配条件：字段/算子/值均由用户补） */
export const emptyLeaf = (): UiCondition => ({ fieldCode: '', operator: 'eq', value: '' });

/** 新建空组（默认 all，带一个待配叶） */
export const emptyGroup = (kind: 'all' | 'any' = 'all'): UiCondition =>
  kind === 'any' ? { any: [emptyLeaf()] } : { all: [emptyLeaf()] };

/** 组类型 */
export const groupKindOf = (c: UiCondition): 'all' | 'any' | 'not' => (c.all ? 'all' : c.any ? 'any' : 'not');

/** 组的子条件列表（not 归一为单元素数组，调用方按 groupKindOf 区分语义） */
export const childrenOf = (c: UiCondition): UiCondition[] => (c.all ?? c.any ?? (c.not ? [c.not] : []));

/**
 * 组嵌套深度（叶 = 0；root 组 = 1；组里套组逐层 +1）。
 * 上限 MAX_GROUP_DEPTH 与引擎 MAX_COND_DEPTH(=3) 对齐：嵌套 N 层组的叶子
 * 在引擎里以深度 N 求值，N>3 的层 fail-closed 不命中——编辑器从源头禁配。
 */
export function depthOf(c?: UiCondition): number {
  if (!isGroup(c)) return 0;
  const kids = childrenOf(c as UiCondition).map(depthOf);
  return 1 + (kids.length ? Math.max(...kids) : 0);
}

/** 组末尾追加子条件（叶或子组）；not 组只承载单子（替换） */
export const addChild = (group: UiCondition, child: UiCondition): UiCondition => {
  const kind = groupKindOf(group);
  if (kind === 'not') return { not: child };
  return kind === 'any' ? { any: [...childrenOf(group), child] } : { all: [...childrenOf(group), child] };
};

/**
 * 删除组内第 index 个子条件。
 * not 组删空 ⇒ 退化为待配叶；普通组删空 ⇒ 同样退化为叶（空组无意义，
 * 留着只会让规则「看似配了实则恒不命中」——lint 的「没有 then」会兜底提醒）。
 */
export const removeChildAt = (group: UiCondition, index: number): UiCondition => {
  const kind = groupKindOf(group);
  if (kind === 'not') return emptyLeaf();
  const next = childrenOf(group).filter((_, i) => i !== index);
  if (!next.length) return emptyLeaf();
  return kind === 'any' ? { any: next } : { all: next };
};

/** 替换组内第 index 个子条件 */
export const updateChildAt = (group: UiCondition, index: number, child: UiCondition): UiCondition => {
  const kind = groupKindOf(group);
  if (kind === 'not') return { not: child };
  const next = childrenOf(group).map((x, i) => (i === index ? child : x));
  return kind === 'any' ? { any: next } : { all: next };
};

/** 切换组类型（all↔any 无损保留全部子条件；切 not 保留第一个） */
export const setGroupKind = (group: UiCondition, kind: 'all' | 'any' | 'not'): UiCondition => {
  if (kind === 'not') return { not: childrenOf(group)[0] ?? emptyLeaf() };
  const kids = childrenOf(group);
  const list = kids.length ? kids : [emptyLeaf()];
  return kind === 'any' ? { any: list } : { all: list };
};

/** 简单条件升级为 all 组（保留原条件为唯一子条件；可逆） */
export const wrapAsGroup = (leaf: UiCondition): UiCondition => ({ all: [leaf] });

/**
 * 组降级为简单条件：仅当「非 not 且只有一个子条件」时无损，返回该子条件；
 * 否则返回 null（调用方不提供拆回入口，避免丢数据）。
 */
export const unwrapGroup = (group: UiCondition): UiCondition | null => {
  if (groupKindOf(group) === 'not') return null;
  const kids = childrenOf(group);
  return kids.length === 1 ? kids[0] : null;
};
