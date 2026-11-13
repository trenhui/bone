/**
 * 全局命令面板的结果构造（纯函数，可单测）。
 *
 * 业界依据
 * --------
 * - SAP Fiori shell bar 常驻 enterprise search：一个词找到「应用」或「数据」，而不是记菜单位置；
 *   1-1-3 规则要求核心任务 ≤3 屏。
 * - Nielsen #7 Flexibility and efficiency of use：⌘K / `/` 是给熟练用户的 accelerator，
 *   必须**叠加**在可见菜单之上，不能替代鼠标路径。
 * - Nielsen #3 User control and freedom：Esc 退出、随时取消；#9 错误恢复：无结果给下一步而不是空白。
 *
 * 安全边界（关键）
 * ----------------
 * 菜单结果**只来自 `menuConfig`** —— 它已由 `GET /iam/menus/current` 按租户+权限码过滤。
 * 命令面板绝不硬编码任何业务路径来「补一个方便入口」：那等于在 IAM 之外造第二份可见性真源，
 * 会让只读账号看到一个点了就 403 的菜单。案件直达项只做 URL 编码，**合法性判定的唯一真源**
 * 在 claim-app 的 `workbench/caseDeepLinks.ts::parseCaseLocation`（fail-closed），shell 不重复判定。
 */
import type { ShellMenuItem } from '../shellContext';

export type CommandGroup = 'case' | 'menu';

export interface CommandItem {
  id: string;
  group: CommandGroup;
  label: string;
  description: string;
  /** shell 的 navigate 目标（pathname + hash，hash 内可带 query）。 */
  target: string;
}

export interface MenuLeaf {
  key: string;
  label: string;
  path: string;
  hash?: string;
}

export const MAX_COMMAND_RESULTS = 12;
/** 案件 ID 直达的展示启发式（不是安全判定；目标页会再次 fail-closed 校验）。 */
const NUMERIC_ID = /^\d{1,32}$/;

/** 只收有 path 的叶子；目录节点（有 children）是分组，不是可导航入口。 */
export function flattenMenuLeaves(items: readonly ShellMenuItem[] | null | undefined): MenuLeaf[] {
  const out: MenuLeaf[] = [];
  const walk = (nodes: readonly ShellMenuItem[] | null | undefined): void => {
    for (const node of nodes ?? []) {
      if (node.children && node.children.length > 0) {
        walk(node.children);
        continue;
      }
      if (!node.path) continue;
      out.push({ key: node.key, label: node.label, path: node.path, hash: node.hash });
    }
  };
  walk(items);
  return out;
}

export function menuTarget(leaf: MenuLeaf): string {
  return leaf.hash ? `${leaf.path}#${leaf.hash}` : leaf.path;
}

/**
 * 构造结果：精确案件 ID 直达优先，其次名称匹配的可见菜单。
 * 无结果时返回空数组，由 UI 给出文字引导（**不生成硬编码业务链接**）。
 */
export function buildCommandItems(
  menuConfig: readonly ShellMenuItem[] | null | undefined,
  rawKeyword: string | null | undefined,
): CommandItem[] {
  const keyword = (rawKeyword ?? '').trim().slice(0, 64);
  const leaves = flattenMenuLeaves(menuConfig);
  const items: CommandItem[] = [];

  if (NUMERIC_ID.test(keyword)) {
    items.push({
      id: `case:${keyword}`,
      group: 'case',
      label: `打开案件 ${keyword}`,
      description: '直达案件 360 概览（案件 ID 合法性由目标页复核）',
      target: `/claim#/case?claimId=${encodeURIComponent(keyword)}&tab=overview`,
    });
  }

  const lower = keyword.toLowerCase();
  const matched = lower
    ? leaves.filter((l) => l.label.toLowerCase().includes(lower) || (l.hash ?? '').toLowerCase().includes(lower))
    : leaves;

  for (const leaf of matched.slice(0, MAX_COMMAND_RESULTS)) {
    items.push({
      id: `menu:${leaf.key}`,
      group: 'menu',
      label: leaf.label,
      description: menuTarget(leaf),
      target: menuTarget(leaf),
    });
  }
  return items.slice(0, MAX_COMMAND_RESULTS);
}
