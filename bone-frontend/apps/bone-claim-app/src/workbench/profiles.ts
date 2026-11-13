/**
 * 我的工作台 · 信息架构与「能力画像」的唯一真源（纯函数，可单测，无 React / 无网络）。
 *
 * 为什么是「能力画像」而不是「角色」
 * ----------------------------------
 * 2026-11-12 全仓取证：仓库里**没有任何理赔业务角色码**。`bone-init.sql:281-285` 只有
 * SUPER_ADMIN / USER / TENANT_ADMIN_demo，`scripts/seed/uat/gen.py:239-252` 是 12 个平台通用
 * DEMO_* 角色（财务专员、审计员、只读访客…），`GET /menus/current` 也只判权限码不判角色。
 * 因此「登录后按角色渲染工作台」若照字面实现，只能拿到一个永远为空的数组 —— 典型的
 * 「配了不生效」。P0 改为：**权限码能力画像**（写权限码是有区分度的唯一运行时信号），
 * 角色码作为未来 IAM 下发业务角色后的可选优先信号。两者都不改变后端鉴权。
 *
 * 业界依据
 * --------
 * - SAP Fiori Spaces & Pages：Space=工作域 / Page=任务 / Section=主题；官方配额
 *   1 space per role、1–5 pages、2–5 sections/page、3–7 apps/section、≤25 apps/page。
 *   本文件把「四空间」落成一个 My Home page 的 4 个 Section，并用 assertFioriQuota 把配额
 *   变成可执行断言，而不是写在文档里的口号。
 * - Fiori 五大原则 Role-based / Simple(1-1-3)：首页只放当前画像相关任务，不重列 15 条菜单。
 * - Nielsen #6 Recognition rather than recall：卡片文字写「待我审核」而不是菜单名「核赔工作台」。
 *
 * 安全边界（务必保持）
 * --------------------
 * 本文件所有过滤**只做展示层裁剪**，不是权限判定。任何卡片被隐藏，其 path 仍可被直接访问，
 * 由后端 @PreAuthorize 与 row_policies 兜底；前端不得据此放宽任何按钮或接口调用。
 */

import type { ClaimStatusName } from '../services/types';
import { PERMISSIONS } from '../constants/permissions';

export type SpaceId = 'work' | 'find' | 'configure' | 'govern';

export interface WorkbenchSpace {
  id: SpaceId;
  label: string;
  /** 一句话说明变更节奏与影响半径（Fiori：Space 按工作域而非技术模块划分）。 */
  description: string;
}

/** 四个空间 = My Home 页的 4 个 Section；顺序即默认展示顺序。 */
export const SPACES: readonly WorkbenchSpace[] = [
  { id: 'work', label: '作业 Work', description: '日频 · 改一次影响一件赔案' },
  { id: 'find', label: '查询 Find', description: '日频 · 只读跨案检索' },
  { id: 'configure', label: '配置 Configure', description: '低频 · 改一次影响该保单所有未来赔案' },
  { id: 'govern', label: '治理 Govern', description: '低频 · 模型与质量专业角色' },
];

export interface WorkbenchCard {
  id: string;
  /** 卡片标题写「今天要办什么事」，不写系统模块名（Nielsen #6）。 */
  label: string;
  description: string;
  space: SpaceId;
  /** 微应用内 hash 路由，可带 query；由 buildCaseDeepLink / navigate 使用。 */
  path: string;
  /** 展示所需权限码：任一命中即显示；空 = 所有登录用户可见。**仅展示裁剪，不是鉴权。** */
  requiresAny?: readonly string[];
  /** 只允许这些画像看到（治理面的专业角色收敛）。 */
  personaAllow?: readonly PersonaId[];
  /**
   * 受保护动作卡：禁批量、禁自动采纳、必须逐案人工终审。
   * 对齐方案 §4.4 与 §6.3：拒赔、调账、支付、恢复 FRAUD 挂起、≥1 万复核。
   */
  guarded?: boolean;
  /** 计数来源；none = 纯入口卡，不显示数字（避免用 0 冒充真实指标）。 */
  metric: 'reviewTasks' | 'claimPage' | 'none';
  /** claimPage 计数用的状态集。 */
  statuses?: readonly ClaimStatusName[];
  /** AI 主动推送占位（L3）。P0 只展示「待接入」，不伪造推送数。 */
  aiPushed?: boolean;
}

/** 全部卡片（20 张，落在 Fiori ≤25 apps/page 配额内）。 */
export const CARDS: readonly WorkbenchCard[] = [
  // ---- 作业 Work（7）----
  { id: 'todo-review', label: '待我审核', description: '领取并按队列逐案核赔；拒赔与资金动作不可批量', space: 'work', path: '/review', requiresAny: [PERMISSIONS.DECISION_WRITE], guarded: true, metric: 'reviewTasks' },
  { id: 'todo-preexam', label: '待初审 / 待分配', description: '新受理案件的材料齐套与核定金额', space: 'work', path: '/pre-exam', requiresAny: [PERMISSIONS.CLAIMS_WRITE], metric: 'claimPage', statuses: ['ACCEPTED', 'PENDING_PRE_EXAM', 'PRE_EXAMINING', 'AUTO_PRE_EXAMINING'] },
  { id: 'todo-dual-review', label: '大额待双人复核', description: '≥1 万复核：提出人 ≠ 审批人，逐案确认', space: 'work', path: '/review', requiresAny: [PERMISSIONS.REVIEW_WRITE], guarded: true, metric: 'claimPage', statuses: ['REVIEWING'] },
  { id: 'todo-fund', label: '待支付 / 对账差异', description: '结算单与两段式幂等支付，逐案确认', space: 'work', path: '/fund', requiresAny: [PERMISSIONS.SETTLEMENT_WRITE], guarded: true, metric: 'none' },
  { id: 'todo-inspection', label: '后督质检 DIFF', description: '人工抽检 + AI 质检差异队列，只出 DIFF 不改原案结论', space: 'work', path: '/inspection', requiresAny: [PERMISSIONS.INSPECTION_WRITE], guarded: true, metric: 'none' },
  { id: 'todo-appeal', label: '申诉与挂起', description: '复议结论与 5 类挂起；FRAUD 恢复仅风控岗', space: 'work', path: '/appeal', requiresAny: [PERMISSIONS.APPEAL_WRITE], metric: 'none' },
  { id: 'case-360', label: '案件 360', description: '一个 claimId 看全材料 / 理算 / 资金 / 决策轨迹', space: 'work', path: '/case', metric: 'none' },
  // ---- 查询 Find（5）----
  { id: 'global-search', label: '全局检索（⌘K）', description: '按案件 ID / 保单号直达，菜单按名称检索', space: 'find', path: '/workbench?panel=search', metric: 'none' },
  { id: 'saved-views', label: '保存视图', description: '我的待办 / 高风险 / 临近 SLA 等预置视图（P1 落库）', space: 'find', path: '/workbench?panel=views', metric: 'none' },
  { id: 'recent-cases', label: '最近访问', description: '浏览器会话内最近打开过的案件，一键回到案件 360', space: 'find', path: '/workbench?panel=recent', metric: 'none' },
  { id: 'progress', label: '理赔进度', description: '按案件 ID 查询进度与决策解释', space: 'find', path: '/progress', metric: 'none' },
  { id: 'submit', label: '理赔申请', description: '发起新申请；提交后可直接进入案件 360', space: 'find', path: '/submit', metric: 'none' },
  // ---- 配置 Configure（5）----
  { id: 'policy', label: '保单管理', description: '计划 / 险种 / 责任从保单下钻配置（0093 收敛形态）', space: 'configure', path: '/policy', requiresAny: [PERMISSIONS.POLICY_READ], metric: 'none' },
  { id: 'quota', label: '额度管理', description: '个人额度与公账巡检', space: 'configure', path: '/quota', requiresAny: [PERMISSIONS.QUOTA_READ], metric: 'none' },
  { id: 'library', label: '基础目录库', description: '目录主数据与版本', space: 'configure', path: '/library', requiresAny: [PERMISSIONS.CLAIMS_READ], metric: 'none' },
  { id: 'insurers', label: '承保主体', description: '多保司主体信息', space: 'configure', path: '/insurers', requiresAny: [PERMISSIONS.CLAIMS_READ], metric: 'none' },
  { id: 'mappings', label: '主数据映射', description: '保司码 → 标准码 crosswalk 与未命中处置', space: 'configure', path: '/mappings', requiresAny: [PERMISSIONS.CLAIMS_READ], metric: 'none' },
  // ---- 治理 Govern（3）----
  { id: 'ai-governance', label: 'AI 治理', description: '校准 / provider / 漂移 / 模型卡', space: 'govern', path: '/ai-governance', personaAllow: ['admin', 'reviewer', 'inspection', 'operator'], metric: 'none' },
  { id: 'blind-labels', label: '盲标工作台', description: '金标准标注；**禁区**：不展示 aiSuggestion，防反馈环污染', space: 'govern', path: '/blind-labels', personaAllow: ['admin', 'reviewer', 'inspection'], metric: 'none' },
  { id: 'ai-disposition', label: '建议采纳回流', description: '曝光 → 采纳 / 驳回 / 修改漏斗；驳回必填受控原因码', space: 'govern', path: '/workbench?panel=ai', personaAllow: ['admin', 'reviewer', 'inspection', 'operator'], metric: 'none' },
];

export type PersonaId =
  | 'admin' | 'adjuster' | 'reviewer' | 'finance' | 'inspection'
  | 'risk' | 'operator' | 'employee' | 'readonly';

export interface PersonaMeta {
  label: string;
  summary: string;
  /** 首页优先展示的 Section 顺序（其余 Section 仍可见，只是排在后面 —— 不隐藏能力）。 */
  focusSpaces: readonly SpaceId[];
}

export const PERSONA_META: Record<PersonaId, PersonaMeta> = {
  admin: { label: '管理员 / IT', summary: '系统健康、角色与工作台配置、菜单审计', focusSpaces: ['govern', 'configure', 'work', 'find'] },
  adjuster: { label: '核赔员', summary: '待我审核、风险标记、已达封顶、今日已审', focusSpaces: ['work', 'find'] },
  reviewer: { label: '复核主管', summary: '≥1 万复核、调账审批、主管抽检', focusSpaces: ['work', 'find', 'govern'] },
  finance: { label: '资金 · 财务对账', summary: '待支付、支付失败、对账差异、额度预警', focusSpaces: ['work', 'find', 'configure'] },
  inspection: { label: '后督 · 质检', summary: '人工抽检 ≥3% 与 100% AI 质检 DIFF 队列', focusSpaces: ['work', 'govern', 'find'] },
  risk: { label: '风控岗', summary: 'FRAUD 挂起待恢复、团伙线索、高置信风险复核', focusSpaces: ['work', 'find', 'govern'] },
  operator: { label: 'TPA 运营 / 保司管理员', summary: '配置变更、映射未命中、规则健康度', focusSpaces: ['configure', 'find', 'govern'] },
  employee: { label: '员工（自助）', summary: '我的申请、待补件、进度时间轴、决策解释', focusSpaces: ['find', 'work'] },
  readonly: { label: '只读 / 审计', summary: '只读审计视图，不承载任何决策动作', focusSpaces: ['find', 'govern'] },
};

/**
 * 已存在的平台角色码 → 画像。只登记**仓库里真实存在**的码，禁止编造业务角色。
 * 未来 IAM 下发业务角色码时，只需在此表追加一条，能力画像自动让位。
 */
export const ROLE_PERSONA: Record<string, PersonaId> = {
  SUPER_ADMIN: 'admin',
  TENANT_ADMIN_demo: 'operator',
  DEMO_TENANT_OWNER: 'operator',
  DEMO_FINANCE: 'finance',
  DEMO_AUDITOR: 'readonly',
  DEMO_READONLY: 'readonly',
};

interface CapabilityRule {
  persona: PersonaId;
  /** 命中任一码即采用；规则**有序**，先命中先赢，避免多码账号画像漂移。 */
  anyOf: readonly string[];
  reason: string;
}

/**
 * 能力画像规则：专门岗位优先于通用岗位，风控/后督优先于核赔，配置写优先于案件写。
 * 用写权限码而非读权限码 —— 0078 已把 15 条菜单的读码统一，读码无区分度。
 */
export const CAPABILITY_RULES: readonly CapabilityRule[] = [
  { persona: 'inspection', anyOf: [PERMISSIONS.INSPECTION_WRITE], reason: '持有后督写权限（最专门的岗位信号）' },
  { persona: 'finance', anyOf: [PERMISSIONS.SETTLEMENT_WRITE], reason: '持有结算写权限' },
  { persona: 'risk', anyOf: [PERMISSIONS.APPEAL_WRITE], reason: '持有申诉/挂起写权限' },
  { persona: 'reviewer', anyOf: [PERMISSIONS.REVIEW_WRITE], reason: '持有复核写权限（大额双人复核）' },
  { persona: 'adjuster', anyOf: [PERMISSIONS.DECISION_WRITE], reason: '持有核赔写权限' },
  { persona: 'operator', anyOf: [PERMISSIONS.POLICY_WRITE, PERMISSIONS.QUOTA_WRITE, PERMISSIONS.MAPPING_DISPOSE], reason: '持有配置写权限而无作业写权限' },
  { persona: 'employee', anyOf: [PERMISSIONS.CLAIMS_WRITE], reason: '仅持有案件写权限（发起/分案）' },
];

/**
 * Fiori Spaces & Pages 官方配额：2–5 sections/page、3–7 apps/section、≤25 apps/page。
 *
 * **只把上限作为护栏**：下限是「内容充实度建议」，不是正确性判据。只读/员工画像天然只有
 * 1 个稀疏 Section，若把 minSections=2 / minCardsPerSection=3 写成硬断言，正常降级会被判红，
 * 用户随后就会关掉整条护栏 —— 与项目「误报比漏报更致命」的教训一致。真正要防的是
 * 「卡片越加越多，首页重新变成第二份 15 条平铺菜单」这种膨胀回归。
 */
export const FIORI_QUOTA = {
  /** 建议下限（仅展示，不参与判红）。 */
  recommendedMinSections: 2,
  recommendedMinCardsPerSection: 3,
  maxSections: 5,
  maxCardsPerSection: 7,
  maxCardsPerPage: 25,
} as const;

export interface WorkbenchSection extends WorkbenchSpace {
  cards: readonly WorkbenchCard[];
}

export interface WorkbenchProfile {
  persona: PersonaId;
  label: string;
  summary: string;
  /** 画像来源，供界面上的「为什么看到这些卡片」诊断卡展示。 */
  source: 'role' | 'capability' | 'fallback';
  reason: string;
  roleCodes: readonly string[];
  permissionCodes: readonly string[];
  sections: readonly WorkbenchSection[];
  /** 受保护卡片 id：UI 据此禁用批量与自动采纳。 */
  guardedCardIds: readonly string[];
}

function normalize(values: readonly string[] | null | undefined): string[] {
  return (values ?? []).map((v) => String(v).trim()).filter(Boolean);
}

/** 纯判定：角色码优先 → 能力码有序规则 → 兜底画像。 */
export function resolvePersona(
  roleCodes: readonly string[] | null | undefined,
  permissionCodes: readonly string[] | null | undefined,
): { persona: PersonaId; source: WorkbenchProfile['source']; reason: string } {
  const roles = normalize(roleCodes);
  const codes = new Set(normalize(permissionCodes));
  for (const role of roles) {
    const mapped = ROLE_PERSONA[role];
    if (mapped) return { persona: mapped, source: 'role', reason: `平台角色码 ${role} 命中登记映射` };
  }
  if (codes.has('*')) return { persona: 'admin', source: 'capability', reason: '持有通配权限码 *' };
  for (const rule of CAPABILITY_RULES) {
    if (rule.anyOf.some((code) => codes.has(code))) {
      return { persona: rule.persona, source: 'capability', reason: rule.reason };
    }
  }
  const hasClaimRead = [PERMISSIONS.CLAIMS_READ, PERMISSIONS.POLICY_READ, PERMISSIONS.QUOTA_READ].some((c) => codes.has(c));
  if (hasClaimRead) {
    return { persona: 'employee', source: 'fallback', reason: '仅有理赔读权限，无任何作业写权限（员工自助/只读视角）' };
  }
  return { persona: 'readonly', source: 'fallback', reason: '无理赔权限码，降级为最小只读画像（不隐藏系统入口，鉴权仍由后端强制）' };
}

/** 构造完整工作台画像：卡片裁剪、Section 排序、受保护动作集合。 */
export function resolveWorkbenchProfile(
  roleCodes: readonly string[] | null | undefined,
  permissionCodes: readonly string[] | null | undefined,
): WorkbenchProfile {
  const roles = normalize(roleCodes);
  const codes = normalize(permissionCodes);
  const codeSet = new Set(codes);
  const { persona, source, reason } = resolvePersona(roles, codes);
  const meta = PERSONA_META[persona];

  const visible = CARDS.filter((card) => {
    if (card.personaAllow && !card.personaAllow.includes(persona)) return false;
    if (card.requiresAny && card.requiresAny.length > 0 && !card.requiresAny.some((c) => codeSet.has(c))) return false;
    return true;
  });

  const order = [...meta.focusSpaces, ...SPACES.map((s) => s.id).filter((id) => !meta.focusSpaces.includes(id))];
  const sections: WorkbenchSection[] = order
    .map((id) => SPACES.find((s) => s.id === id))
    .filter((s): s is WorkbenchSpace => Boolean(s))
    .map((s) => ({ ...s, cards: visible.filter((c) => c.space === s.id) }))
    .filter((s) => s.cards.length > 0);

  return {
    persona,
    label: meta.label,
    summary: meta.summary,
    source,
    reason,
    roleCodes: roles,
    permissionCodes: codes,
    sections,
    guardedCardIds: visible.filter((c) => c.guarded).map((c) => c.id),
  };
}

/** 配额护栏：返回全部违规描述，空数组 = 通过。供单测与运行时诊断调用。 */
export function assertFioriQuota(sections: readonly WorkbenchSection[]): string[] {
  const problems: string[] = [];
  if (sections.length === 0) problems.push('没有任何 Section：工作台为空页，须至少保留查询入口');
  if (sections.length > FIORI_QUOTA.maxSections) problems.push(`Section 数 ${sections.length} > ${FIORI_QUOTA.maxSections}`);
  for (const s of sections) {
    if (s.cards.length > FIORI_QUOTA.maxCardsPerSection) problems.push(`Section ${s.id} 卡片 ${s.cards.length} > ${FIORI_QUOTA.maxCardsPerSection}：首页正在退化成第二份菜单`);
  }
  const total = sections.reduce((n, s) => n + s.cards.length, 0);
  if (total > FIORI_QUOTA.maxCardsPerPage) problems.push(`整页卡片 ${total} > ${FIORI_QUOTA.maxCardsPerPage}`);
  return problems;
}
