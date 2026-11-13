/**
 * 案件 360 · 深链与 Tab 的唯一真源（纯函数，可单测）。
 *
 * 为什么 URL 契约是 `/case?claimId=...&tab=...` 而不是 `/case/:claimId`
 * ------------------------------------------------------------------
 * 原方案写的是 path 参数，但仓库既有真源是 `useClaimIdParam.ts:29` 的 `?claimId=`：
 * 6 个单案页全部从 query 读取。若案件 360 另用 path 参数，就会出现**同一语义两处配置源**
 * （项目已反复踩过），且 6 个现有页面组件一个都复用不了。故统一沿用 query 契约，
 * 并把「生成 / 解析 / 校验」全部收口到本文件 —— 将来真要换成 path 形式，只改一处。
 *
 * 业界依据：Guidewire ClaimCenter / Duck Creek 把 tasks、documents、decision points、
 * event history 绑到同一个 case record；Nielsen #6「识别优于回忆」要求用户不必记住 19 位 ID，
 * URL 里带着 claimId 才可能分享、回退、进通知与客服工单。
 *
 * 安全：claimId 只接受 [A-Za-z0-9_-]{1,64}，含 / ? # 空格等一律拒绝生成链接，
 * 防止把用户输入拼进 URL 造成路径穿越 / 参数注入。
 */

export const CASE_TABS = [
  'overview', 'material', 'adjust', 'fund', 'appeal', 'suspend', 'inspection', 'trace',
] as const;

export type CaseTab = (typeof CASE_TABS)[number];

export interface CaseTabMeta {
  label: string;
  /** 目标作业页（微应用内 hash 路由）；overview 留在案件 360 自身。 */
  targetPath: string;
  description: string;
  /** 承载受保护动作（禁批量 / 需人工终审）。 */
  guarded?: boolean;
}

export const CASE_TAB_META: Record<CaseTab, CaseTabMeta> = {
  overview: { label: '概览', targetPath: '/case', description: '状态时间轴、关键字段、SLA 与当前责任人' },
  material: { label: '材料与 OCR', targetPath: '/evidence', description: '单证列表、OCR 状态与置信度、补件' },
  adjust: { label: '理算归因', targetPath: '/adjust', description: '理算明细、控额链路、字段 lineage' },
  fund: { label: '资金与结算', targetPath: '/fund', description: '结算单状态机、额度、对账 Timeline', guarded: true },
  appeal: { label: '申诉复议', targetPath: '/appeal', description: '复议结论与五要素解释' },
  suspend: { label: '挂起 / 恢复', targetPath: '/suspend', description: '5 类挂起；FRAUD 仅风控岗可恢复', guarded: true },
  inspection: { label: '后督质检', targetPath: '/inspection', description: '四维结论、DIFF、抽检记录', guarded: true },
  trace: { label: '决策与 trace', targetPath: '/review', description: '决策图轨迹回放、规则命中、AI 结论卡' },
};

/** 作业页 pathname → 对应 Tab：让 6 个 CaseIdBar 页无需改动即可获得「回案件 360」。 */
export const PATH_TAB: Record<string, CaseTab> = {
  '/evidence': 'material',
  '/adjust': 'adjust',
  '/fund': 'fund',
  '/appeal': 'appeal',
  '/suspend': 'suspend',
  '/inspection': 'inspection',
  '/review': 'trace',
  '/pre-exam': 'overview',
};

export function isSafeClaimId(value: string | null | undefined): boolean {
  if (!value) return false;
  return /^[A-Za-z0-9_-]{1,64}$/.test(value.trim());
}

/**
 * 「像不像案件 ID」的业务判定，与上面的 URL 安全性判定**解耦**。
 *
 * <p>`isSafeClaimId` 只保证字符串可安全拼进 URL（允许字母/下划线/连字符），它会把
 * 「E2E-POLICY-0001」这类保单号、带字母的报案号也判为 true。若拿它决定「直接跳案件 360
 * 还是走后端跨键检索」，用户在工作台检索框输入保单号就会被当成 claimId 拼进
 * `/claims/{id}` ⇒ 后端 400，整条跨键检索失效。
 *
 * <p>故「直接跳案件 360」只认**纯数字且长度 ≥15**（雪花号量级，如 776102367333449728）；
 * 11 位手机号、带字母的保单号/报案号一律回退后端 `/claims/search` 跨键检索。
 */
export function isClaimIdLike(value: string | null | undefined): boolean {
  if (!value) return false;
  return /^\d{15,}$/.test(value.trim());
}

/** 未知 tab 不允许静默变成任意路径：一律回退 overview，并可由调用方提示。 */
export function normalizeTab(value: string | null | undefined): CaseTab {
  return CASE_TABS.includes(value as CaseTab) ? (value as CaseTab) : 'overview';
}

export function tabForPathname(pathname: string | null | undefined): CaseTab {
  if (!pathname) return 'overview';
  return PATH_TAB[pathname.replace(/\/+$/, '')] ?? 'overview';
}

/** 从作业页跳到案件 360（保留当前环节为默认 Tab）。 */
export function buildCase360Link(claimId: string, tab: CaseTab = 'overview'): string | null {
  if (!isSafeClaimId(claimId)) return null;
  return `/case?claimId=${encodeURIComponent(claimId.trim())}&tab=${normalizeTab(tab)}`;
}

/**
 * 从案件 360 跳到具体作业页，**自动带 claimId**（第一次让 navigate() 携带案件上下文）。
 * overview 没有独立作业页，返回案件 360 自身。
 */
export function buildCaseDeepLink(claimId: string, tab: CaseTab = 'overview'): string | null {
  if (!isSafeClaimId(claimId)) return null;
  const safeTab = normalizeTab(tab);
  const target = CASE_TAB_META[safeTab].targetPath;
  const id = encodeURIComponent(claimId.trim());
  return safeTab === 'overview'
    ? `/case?claimId=${id}&tab=overview`
    : `${target}?claimId=${id}`;
}

export interface CaseLocation {
  claimId: string | null;
  tab: CaseTab;
  /** 解析出来的 tab 是否是非法值回退的结果（界面应提示而非假装没事）。 */
  tabRecovered: boolean;
}

/** 解析 `?claimId=&tab=`；非法 ID 不抛异常，返回 null 让页面渲染空态。 */
export function parseCaseLocation(search: string | null | undefined): CaseLocation {
  const params = new URLSearchParams(search ?? '');
  const rawId = params.get('claimId');
  const rawTab = params.get('tab');
  return {
    claimId: isSafeClaimId(rawId) ? rawId!.trim() : null,
    tab: normalizeTab(rawTab),
    tabRecovered: Boolean(rawTab) && !CASE_TABS.includes(rawTab as CaseTab),
  };
}
