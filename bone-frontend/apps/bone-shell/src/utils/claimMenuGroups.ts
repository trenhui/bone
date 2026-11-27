/**
 * 智能理赔侧栏「四空间」常驻分组映射（**纯展示层**，不参与权限与可见性判定）。
 *
 * <p>为什么按 hash 常量而不是按菜单名/id：菜单名会随文案调整漂移，后端节点 id 跨环境不固定，
 * 只有微应用内 hash 与 `routes.tsx` 的稳定路由契约一一对应。未知 hash 一律落到「未分组」，
 * **绝不因为归类失败而丢弃菜单项**（单导航探针的「无功能不可达」断言会立刻判红）。
 *
 * <p>为什么四组恰好是声明顺序上的**连续区段**：单导航探针要求「菜单顺序 = routes.tsx 声明
 * 顺序」。若按语义把某条菜单跨段搬动，视觉顺序就会和真源不一致。故边界按现有链路切分：
 * 作业 1–8 / 查询 9–10 / 配置 11–13 / 治理 14–18，分组只加标题、不重排任何一项。
 * （2026-11-22：AI 治理 / 运营分析看板由工作台深链提升为正式菜单，接入治理组末尾；
 *  2026-11-25：案件签收 order_no=20 接入治理组末尾。）
 *
 * <p>业界依据：NN/g 隐藏导航实验显示隐藏后使用率 48–50% → 27%、任务时间至少 +39%，
 * 因此这里只用 `type: 'group'` 常驻标题，不用折叠 SubMenu；SAP Fiori Spaces/Pages/Sections
 * 也是「常驻壳内分组 + 应用仍可直接点击」的形态。
 */

export const CLAIM_NAV_GROUP_TITLES = ['作业', '查询', '配置', '治理'] as const;
export type ClaimNavGroupTitle = (typeof CLAIM_NAV_GROUP_TITLES)[number];

export const UNGROUPED_TITLE = '未分组';
export type ClaimNavGroup = ClaimNavGroupTitle | typeof UNGROUPED_TITLE;

/** 渲染顺序：与 routes.tsx 声明顺序一致，保证「分组不改变菜单顺序」。 */
export const CLAIM_NAV_GROUP_ORDER: readonly ClaimNavGroup[] = [
  ...CLAIM_NAV_GROUP_TITLES, UNGROUPED_TITLE,
];

/** 18 条可见理赔菜单 hash → 四空间。新增菜单必须同批登记，否则落「未分组」而非消失。 */
export const CLAIM_MENU_GROUP_BY_HASH: Readonly<Record<string, ClaimNavGroupTitle>> = {
  // 作业（1–8）：逐单流转的单案作业链路
  '/pre-exam': '作业',
  '/review': '作业',
  '/adjust': '作业',
  '/evidence': '作业',
  '/fund': '作业',
  '/appeal': '作业',
  '/suspend': '作业',
  '/inspection': '作业',
  // 查询（9–10）：员工自助提交与进度查询
  '/submit': '查询',
  '/progress': '查询',
  // 配置（11–13）：运营配置与基础目录（改一次影响未来多案）
  '/policy': '配置',
  '/quota': '配置',
  '/library': '配置',
  // 治理（14–18）：多保司主数据 + AI 治理 + 运营分析看板 + 案件签收
  '/insurers': '治理',
  '/mappings': '治理',
  '/ai-governance': '治理',
  '/analytics': '治理',
  '/sign': '治理',
};

/** SynapClaim 规范内嵌菜单页前缀（无 hash）。与 App.tsx / claim-app sclaimRoutes.ts 一致。 */
export const CLAIM_MENU_PREFIX = '/sclaim/claim';

export interface ClaimMenuGroupable {
  hash?: string | null;
  path?: string | null;
}

/**
 * 取菜单项的「逻辑短路径」（如 `/fund`）作为分组键，兼容两代形态：
 *  - SynapClaim 规范：`path=/sclaim/claim/fund`、无 hash ⇒ 剥离前缀得 `/fund`；
 *  - 历史节点：`hash=/fund` 或 `path=/claim#/fund`。
 * 分组表 CLAIM_MENU_GROUP_BY_HASH 仍以短路径为键，故两种形态共用同一张表。
 */
export function claimHashOf(item: ClaimMenuGroupable): string | null {
  const path = item.path ?? '';
  if (path.startsWith(`${CLAIM_MENU_PREFIX}/`)) {
    const tail = path.slice(CLAIM_MENU_PREFIX.length); // '/fund'
    return tail && tail !== '/' ? tail : null;
  }
  const raw = item.hash ?? (path.includes('#') ? path.split('#')[1] : null);
  if (!raw) return null;
  return raw.startsWith('/') ? raw : `/${raw}`;
}

/**
 * 只有理赔域的可导航叶子参与分组；其它域与按钮权限点保持原样。
 *
 * 新形态认 `path` 以 `/sclaim/claim/` 开头且能取到逻辑短路径；
 * 历史形态认 `parseMenuPath('/claim#/review')` 拆出的 `path === '/claim'` + 非空 hash。
 */
export function isClaimMenuLeaf(item: ClaimMenuGroupable): boolean {
  if (item.path?.startsWith(`${CLAIM_MENU_PREFIX}/`)) return claimHashOf(item) !== null;
  return item.path === '/claim' && claimHashOf(item) !== null;
}

/** 未登记 hash 显式落「未分组」，保留原有可点性与权限判定。 */
export function groupForClaimHash(hash: string | null | undefined): ClaimNavGroup {
  if (!hash) return UNGROUPED_TITLE;
  const normalized = hash.startsWith('/') ? hash : `/${hash}`;
  return CLAIM_MENU_GROUP_BY_HASH[normalized] ?? UNGROUPED_TITLE;
}

export interface ClaimMenuGroup<T> {
  title: ClaimNavGroup;
  items: T[];
}

/**
 * 分组但不排序：按 `CLAIM_NAV_GROUP_ORDER` 输出非空组，组内保持输入顺序。
 * 调用方应断言展平结果与原数组逐项相等（见 claimMenuGroups.test.ts）。
 */
export function groupClaimMenuItems<T extends ClaimMenuGroupable>(
  items: T[],
): ClaimMenuGroup<T>[] {
  const buckets = new Map<ClaimNavGroup, T[]>();
  for (const item of items) {
    const title = groupForClaimHash(claimHashOf(item));
    const bucket = buckets.get(title) ?? [];
    bucket.push(item);
    buckets.set(title, bucket);
  }
  return CLAIM_NAV_GROUP_ORDER
    .filter((title) => (buckets.get(title)?.length ?? 0) > 0)
    .map((title) => ({ title, items: buckets.get(title) ?? [] }));
}
