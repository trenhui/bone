/**
 * SynapClaim（智能理赔系统）URL 规范唯一真源。
 *
 * 规范：/{系统缩写}/{模块}/{页面}
 * --------------------------------
 * - 系统缩写统一 `sclaim`（SynapClaim），不采用宽泛的 `claim`，也不采用描述渲染形态的
 *   `standalone`；系统级前缀可统一用于前端路由、菜单、权限资源标识等命名体系。
 * - 模块用清晰稳定的英文业务名（`claim` / `policy` / `review` / `rule` / `ocr` /
 *   `task` / `agent` / `model`），不人为缩写成难懂的字母组合。
 * - 页面统一 `list` / `detail` / `create` / `edit`；全小写、必要时短横线分隔。
 * - 不保留任何历史别名：旧 URL 直接失效，防止「同一语义两处配置源」。
 *
 * 独立新窗口详情页率先按本规范落地。微应用内嵌路由仍受 qiankun HashRouter 约束
 * （`/claim#/xxx`），本文件只负责「浏览器地址栏 pathname」这一层，二者不互相覆盖。
 */

import { isSafeClaimId, normalizeTab } from './caseDeepLinks';
import { isSafePolicyNo } from './policyDeepLinks';

/** 系统缩写：SynapClaim。 */
export const SCLAIM_SYSTEM = 'sclaim';

/** 模块名（清晰英文业务名，不做二次缩写）。 */
export const SCLAIM_MODULE = {
  CLAIM: 'claim',
  POLICY: 'policy',
} as const;

/** 页面标识（统一命名）。 */
export const SCLAIM_PAGE = {
  LIST: 'list',
  DETAIL: 'detail',
  CREATE: 'create',
  EDIT: 'edit',
} as const;

/** 构造规范路径：/sclaim/{module}/{page}。 */
export function buildSclaimPath(module: string, page: string): string {
  return `/${SCLAIM_SYSTEM}/${module}/${page}`;
}

/** 新窗口独立详情页的规范路径（浏览器地址栏 pathname，不含 query）。 */
export const STANDALONE_PATHS = {
  CLAIM_DETAIL: buildSclaimPath(SCLAIM_MODULE.CLAIM, SCLAIM_PAGE.DETAIL),
  POLICY_DETAIL: buildSclaimPath(SCLAIM_MODULE.POLICY, SCLAIM_PAGE.DETAIL),
} as const;

/** 所有独立页路径（供 shell activeRule / MICRO_APP_BASES 共用）。 */
export const ALL_STANDALONE_PATHS: readonly string[] = Object.values(STANDALONE_PATHS);

/**
 * 判断 pathname 是否处于无壳独立页模式。
 * 按完整路径段匹配（`/sclaim/claim/detail-x` 不算），避免前缀误判。
 */
export function isStandalonePath(pathname: string | null | undefined): boolean {
  if (!pathname) return false;
  return ALL_STANDALONE_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/**
 * 独立页模式下，详情页应从浏览器地址栏 query 读参数——HashRouter 的 `useSearchParams`
 * 读的是 hash 内的 query（`/#/case?...`），在规范 path（无 hash）下恒为空。
 */
export function standaloneSearch(): string {
  return isStandalonePath(window.location.pathname) ? window.location.search : '';
}

/**
 * 案件详情规范 URL：`/sclaim/claim/detail?claimId=...&tab=...`。
 * 非法 ID 返回 null（fail-closed），调用方降级为纯文本。校验复用深链真源，避免两处正则漂移。
 */
export function buildClaimDetailHref(claimId: string, tab: string = 'overview'): string | null {
  if (!isSafeClaimId(claimId)) return null;
  const params = new URLSearchParams({ claimId: claimId.trim(), tab: normalizeTab(tab) });
  return `${window.location.origin}${STANDALONE_PATHS.CLAIM_DETAIL}?${params.toString()}`;
}

/**
 * 保单详情规范 URL：`/sclaim/policy/detail?policyNo=...`。
 * 非法保单号返回 null（fail-closed）。
 */
export function buildPolicyDetailHref(policyNo: string): string | null {
  if (!isSafePolicyNo(policyNo)) return null;
  const params = new URLSearchParams({ policyNo: policyNo.trim() });
  return `${window.location.origin}${STANDALONE_PATHS.POLICY_DETAIL}?${params.toString()}`;
}
