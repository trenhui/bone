import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import { normalizeTotal } from '@bone/shared-utils';

/**
 * 保单 / 计划 / 险种 / 额度配置 API（后端 `synap-claim`，`/api/v1/claim-config/**`）。
 *
 * <p><b>为什么独立于 `claimApi.ts`</b>：后者是**理赔作业面**（报案、核赔、结算），
 * 本文件是**运营配置面**（保单规则、计划、额度）。两者的权限码、变更节奏、失败影响都不同
 * （作业面错一次影响一件赔案，配置面错一次影响所有未来赔案），混在一个文件里会让
 * "谁能改规则"与"谁能处理赔案"两件事看不出边界。
 *
 * <p><b>ID 一律 string</b>：后端 `Long` 全局序列化为 JSON 字符串（雪花 ID 18~19 位超出
 * JS Number 上限 2^53，按 number 声明会**静默截断末位**，回传后端即 404 且无任何 JS 报错）。
 */

const client = createApiClient('/api/v1/claim-config');

/** 规则开关三态。 */
export type PolicyConfigStatus = 'INACTIVE' | 'ACTIVE' | 'PAUSE';

/** 计划/险种状态。 */
export type ConfigStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';

/** 责任形式。 */
export type LiabilityType = 'REIMBURSEMENT' | 'FIXED_AMOUNT' | 'ALLOWANCE';

/** 额度变更类型（保全记录）。 */
export type QuotaChangeType = 'INIT' | 'ADJUST' | 'REMOVE' | 'RESTORE';

/** 额度维度。 */
export type QuotaType = 'PERSONAL' | 'LIABILITY_CAP' | 'LIABILITY_PERSONAL';

export interface PolicyView {
  policyNo: string;
  policyName: string;
  insurerName: string;
  branchName: string;
  insureName: string;
  bizIdentityCode: string;
  productType: string;
  /** 保单类型：GROUP 团体 / INDIVIDUAL 个人（历史数据可能为空，展示时按团体处理）。 */
  policyType: string;
  effectiveStart: string;
  effectiveEnd: string;
  waitingPeriod: number;
  policyVersion: string;
  /** 被保人清单（假名化 ID 的 JSON 数组字符串；不含明文证件）。 */
  insuredSnapshot: string;
  configStatus: PolicyConfigStatus;
  stale: boolean;
  createdAt: string;
  updatedAt: string;
  /**
   * 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）。
   *
   * <p><b>类型是 string 而非 number</b>：后端版本号是 {@code Long}，平台按「Long 全局序列化为
   * JSON 字符串」下发（如 {@code "1"}）。声明成 number 会让 TS 撒谎——真去比较大小就会踩
   * 「字符串被隐式转数字」的坑。这里保持与下发形态一致，**只透传、不做数值运算**。
   */
  version?: string;
}

export interface PlanView {
  /** 计划 ID（雪花 ID，序列化为 string）。 */
  planId: string;
  policyNo: string;
  planCode: string;
  planName: string;
  planLimit: number;
  waitingPeriod: number;
  versionNo: string;
  status: ConfigStatus;
  createdAt: string;
  updatedAt: string;
  /**
   * 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）。
   *
   * <p><b>类型是 string 而非 number</b>：后端版本号是 {@code Long}，平台按「Long 全局序列化为
   * JSON 字符串」下发（如 {@code "1"}）。声明成 number 会让 TS 撒谎——真去比较大小就会踩
   * 「字符串被隐式转数字」的坑。这里保持与下发形态一致，**只透传、不做数值运算**。
   */
  version?: string;
}

export interface CoverageView {
  coverageId: string;
  planId: string;
  coverageCode: string;
  coverageName: string;
  coverageLimit: number;
  liabilityType: LiabilityType;
  status: ConfigStatus;
  createdAt: string;
  updatedAt: string;
  /**
   * 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）。
   *
   * <p><b>类型是 string 而非 number</b>：后端版本号是 {@code Long}，平台按「Long 全局序列化为
   * JSON 字符串」下发（如 {@code "1"}）。声明成 number 会让 TS 撒谎——真去比较大小就会踩
   * 「字符串被隐式转数字」的坑。这里保持与下发形态一致，**只透传、不做数值运算**。
   */
  version?: string;
}

export interface QuotaAccountView {
  quotaAccountId: string;
  insuredIdHash: string;
  policyNo: string;
  planId: string;
  quotaYear: number;
  quotaType: QuotaType;
  liabilityUuid: string;
  /** 初始额度。 */
  initial: number;
  /** 已扣减（理算已确认）。 */
  claimed: number;
  /** 已冻结（理算未确认）。 */
  frozen: number;
  /** 可用额度 = initial − claimed − frozen。 */
  available: number;
  deductibleUsed: number;
  capUsed: number;
  /** 是否已减人（减人后不再参与额度累计）。 */
  removed: boolean;
  updatedAt: string;
  /**
   * 乐观锁版本号（编辑提交时原样带回，用于并发冲突检测）。
   *
   * <p><b>类型是 string 而非 number</b>：后端版本号是 {@code Long}，平台按「Long 全局序列化为
   * JSON 字符串」下发（如 {@code "1"}）。声明成 number 会让 TS 撒谎——真去比较大小就会踩
   * 「字符串被隐式转数字」的坑。这里保持与下发形态一致，**只透传、不做数值运算**。
   */
  version?: string;
}

/** 公账（责任额度池）汇总：LIABILITY_CAP 账户按责任 UUID 聚合的池级口径。 */
export interface PublicQuotaSummaryView {
  /** 责任 UUID（理算按它占用/归还额度池）。 */
  liabilityUuid: string;
  policyNo: string;
  /** 计划 ID（雪花 ID 按平台约定序列化为字符串）。 */
  planId: string;
  /** 账户数（= 覆盖的被保人数）。 */
  accountCount: number;
  /** 已减人账户数。 */
  removedCount: number;
  initialTotal: number;
  claimedTotal: number;
  frozenTotal: number;
  availableTotal: number;
}

export interface QuotaChangeView {
  id: string;
  quotaAccountId: string;
  policyNo: string;
  insuredIdHash: string;
  changeType: QuotaChangeType;
  /** 变更前初始额度（绝对值快照）。 */
  beforeInitial: number;
  /** 变更后初始额度（绝对值快照）。 */
  afterInitial: number;
  operatorId: string;
  operatorName: string;
  remark: string;
  occurredAt: string;
}

/** 分页信封（后端 `PageResult`）。 */
export interface PageResult<T> {
  records: T[];
  /** 后端 Long 全局序列化为字符串 ⇒ 必须经 {@link normalizeTotal} 归一。 */
  total: string | number;
  page: number;
  size: number;
}

/** 新建保单请求。 */
export interface CreatePolicyReq {
  policyNo: string;
  policyName?: string;
  insurerName?: string;
  branchName?: string;
  insureName?: string;
  bizIdentityCode?: string;
  productType?: string;
  /** 保单类型：GROUP 团体 / INDIVIDUAL 个人（缺省团体）。 */
  policyType?: string;
  effectiveStart: string;
  effectiveEnd: string;
  waitingPeriod?: number;
  policyVersion?: string;
  /** 被保人清单：假名化 ID 数组（后端接受 JSON 数组字符串或数组）。 */
  insuredSnapshot?: string | string[];
}

/** 新建计划请求。 */
export interface CreatePlanReq {
  policyNo: string;
  planCode: string;
  planName?: string;
  planLimit?: number;
  waitingPeriod?: number;
  versionNo?: string;
}

/** 新建险种请求。 */
export interface CreateCoverageReq {
  planId: string;
  coverageCode: string;
  coverageName?: string;
  coverageLimit?: number;
  liabilityType?: LiabilityType;
}

/** 编辑保单请求（不改动保单号与规则开关）。 */
export interface UpdatePolicyReq {
  policyName?: string;
  insurerName?: string;
  branchName?: string;
  insureName?: string;
  bizIdentityCode?: string;
  productType?: string;
  /** 保单类型：GROUP 团体 / INDIVIDUAL 个人（缺省团体）。 */
  policyType?: string;
  effectiveStart: string;
  effectiveEnd: string;
  waitingPeriod?: number;
  policyVersion?: string;
  /** 被保人清单：假名化 ID 数组（后端接受 JSON 数组字符串或数组）。 */
  insuredSnapshot?: string | string[];
  /**
   * 乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）。
   * 后端是 {@code Long}，平台按「Long 全局序列化为 JSON 字符串」下发 ⇒ 用 string 承接，只透传。
   */
  version?: string;
}

/** 编辑计划请求（不改动计划编码与状态）。 */
export interface UpdatePlanReq {
  planName?: string;
  planLimit?: number;
  waitingPeriod?: number;
  versionNo?: string;
  /**
   * 乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）。
   * 后端是 {@code Long}，平台按「Long 全局序列化为 JSON 字符串」下发 ⇒ 用 string 承接，只透传。
   */
  version?: string;
}

/** 编辑险种请求（不改动险种编码与状态）。 */
export interface UpdateCoverageReq {
  coverageName?: string;
  coverageLimit?: number;
  liabilityType?: LiabilityType;
  /**
   * 乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）。
   * 后端是 {@code Long}，平台按「Long 全局序列化为 JSON 字符串」下发 ⇒ 用 string 承接，只透传。
   */
  version?: string;
}

/** 开户请求。 */
export interface OpenQuotaReq {
  insuredIdHash: string;
  policyNo: string;
  planId: string;
  quotaYear?: number;
  quotaType?: QuotaType;
  liabilityUuid?: string;
  quotaInitial: number;
  remark?: string;
}

/** 调额请求。 */
export interface AdjustQuotaReq {
  newInitial: number;
  remark?: string;
  /**
   * 乐观锁版本号（来自账户列表，提交时原样带回用于并发冲突检测）。
   * 后端是 {@code Long}，平台按「Long 全局序列化为 JSON 字符串」下发 ⇒ 用 string 承接，只透传。
   */
  version?: string;
}

/** 减人/恢复请求。 */
export interface InsuredStateReq {
  remark?: string;
  /**
   * 乐观锁版本号（来自账户列表，提交时原样带回用于并发冲突检测）。
   * 后端是 {@code Long}，平台按「Long 全局序列化为 JSON 字符串」下发 ⇒ 用 string 承接，只透传。
   */
  version?: string;
}

/** 责任视图（配置面读模型）。金额类字段均为字符串：后端 BigDecimal 序列化为纯文本，前端只展示/回传不运算。 */
export interface LiabilityView {
  liabilityId: string;
  liabilityUuid: string;
  liabilityVersion: string;
  planId: string;
  /** 所属保障/险种 ID（四级建模：保单→方案→保障→责任）。 */
  coverageId: string;
  liabilityCode: string;
  liabilityType: LiabilityType;
  payPercent: string;
  liabilityDeduct: string;
  liabilityLimit: string;
  invoiceDeduct: string;
  adjustDeduct: string;
  dailyLimit: string;
  dailyLimitBasis: string;
  limitMode: string;
  limitHospitalCodes: string;
  limitDiagnosisCodes: string;
  catalogPolicy: string;
  catalogCode: string;
  socialOnly: number;
  waitingPeriod: number;
  status: ConfigStatus;
}

/** 新建责任请求。 */
export interface CreateLiabilityReq {
  planId: string;
  /** 所属保障/险种 ID（四级建模：保单→方案→保障→责任）。 */
  coverageId?: string;
  liabilityCode: string;
  liabilityType?: LiabilityType;
  payPercent?: string;
  liabilityDeduct?: string;
  liabilityLimit?: string;
  invoiceDeduct?: string;
  adjustDeduct?: string;
  dailyLimit?: string;
  dailyLimitBasis?: string;
  limitMode?: string;
  limitHospitalCodes?: string;
  limitDiagnosisCodes?: string;
  catalogPolicy?: string;
  catalogCode?: string;
  socialOnly?: number;
  waitingPeriod?: number;
  status?: ConfigStatus;
}

/** 编辑责任请求（空字段沿用原值；ACTIVE 责任将生成新版本）。 */
export interface UpdateLiabilityReq {
  liabilityCode?: string;
  /** 所属保障/险种 ID（编辑时可改归属，与编辑目标同 UUID 的新版本草稿）。 */
  coverageId?: string;
  liabilityType?: LiabilityType;
  payPercent?: string;
  liabilityDeduct?: string;
  liabilityLimit?: string;
  invoiceDeduct?: string;
  adjustDeduct?: string;
  dailyLimit?: string;
  dailyLimitBasis?: string;
  limitMode?: string;
  limitHospitalCodes?: string;
  limitDiagnosisCodes?: string;
  catalogPolicy?: string;
  catalogCode?: string;
  socialOnly?: number;
  waitingPeriod?: number;
  /**
   * 解除保障归属（把责任退回"方案级"）。
   *
   * <p>为什么需要独立标志位：JSON 里 `coverageId: null` 与"没传该字段"在反序列化后都是 null，
   * 后端无法区分「清空归属」与「本次不改归属」。只依赖 coverageId 会让"清空"被静默忽略——
   * 界面上已经清掉、库里还是旧保障，属于最典型的"配了不生效"。
   */
  detachCoverage?: boolean;
}

/** 规则开关展示元数据（唯一真源，避免多处各写一份文案）。 */
export const POLICY_CONFIG_STATUS_META: Record<PolicyConfigStatus, { color: string; label: string }> = {
  INACTIVE: { color: 'default', label: '未启用' },
  ACTIVE: { color: 'green', label: '启用中' },
  PAUSE: { color: 'orange', label: '停用中' },
};

/**
 * 保单类型展示元数据。
 *
 * <p>历史数据 policyType 可能为空 ⇒ 按团体（GROUP）展示，与后端建单缺省口径一致。
 */
export const POLICY_TYPE_META: Record<string, { color: string; label: string }> = {
  GROUP: { color: 'blue', label: '团体' },
  INDIVIDUAL: { color: 'purple', label: '个人' },
};

/** 保单类型展示（空值按团体处理）。 */
export const policyTypeLabel = (v?: string | null): { color: string; label: string } =>
  POLICY_TYPE_META[v || 'GROUP'] ?? POLICY_TYPE_META.GROUP;

/** 计划/险种状态展示元数据。 */
export const CONFIG_STATUS_META: Record<ConfigStatus, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACTIVE: { color: 'green', label: '已发布' },
  RETIRED: { color: 'red', label: '已下线' },
};

/** 额度变更类型展示元数据。 */
export const QUOTA_CHANGE_TYPE_META: Record<QuotaChangeType, { color: string; label: string }> = {
  INIT: { color: 'blue', label: '开户' },
  ADJUST: { color: 'gold', label: '调额' },
  REMOVE: { color: 'red', label: '减人' },
  RESTORE: { color: 'green', label: '恢复' },
};

/** 被保人清单展示：把 JSON 数组串拆成可读列表。 */
export function parseInsuredSnapshot(snapshot?: string): string[] {
  if (!snapshot) return [];
  try {
    const parsed = JSON.parse(snapshot);
    return Array.isArray(parsed) ? parsed.map((v) => String(v)) : [];
  } catch {
    // 清单不是合法 JSON 数组时按逗号分隔兜底（不抛错：展示层不该因脏数据白屏）
    return snapshot
      .replace(/^\[|\]$/g, '')
      .split(',')
      .map((v) => v.trim().replace(/^"|"$/g, ''))
      .filter(Boolean);
  }
}

export const claimConfigApi = {
  /** 保单列表（分页；keyword 匹配保单号/名称，configStatus 过滤规则开关）。 */
  listPolicies(params: {
    keyword?: string;
    configStatus?: PolicyConfigStatus;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<PolicyView>>> {
    return client.get<never, ApiResponse<PageResult<PolicyView>>>('/policies', { params });
  },

  /** 保单详情。 */
  getPolicy(policyNo: string): Promise<ApiResponse<PolicyView>> {
    return client.get<never, ApiResponse<PolicyView>>(`/policies/${encodeURIComponent(policyNo)}`);
  },

  /**
   * 导出保单列表（CSV，UTF-8 BOM）。
   *
   * <p><b>为什么返回 Blob 而不是走通用 client</b>：`responseType: 'blob'` 时响应拦截器
   * 返回的 `response.data` 就是 Blob 本体（非 ApiResponse 信封），下载逻辑在
   * `downloadBlob` 里统一处理。
   */
  exportPolicies(params: { keyword?: string; configStatus?: PolicyConfigStatus }): Promise<Blob> {
    return client.get<never, Blob>('/policies/export', { params, responseType: 'blob' });
  },

  /** 导出某保单下的计划列表（CSV）。 */
  exportPlans(policyNo: string): Promise<Blob> {
    return client.get<never, Blob>('/plans/export', {
      params: { policyNo },
      responseType: 'blob',
    });
  },

  /** 新建保单。 */
  createPolicy(req: CreatePolicyReq): Promise<ApiResponse<PolicyView>> {
    return client.post<CreatePolicyReq, ApiResponse<PolicyView>>('/policies', req);
  },

  /** 启用保单责任（INACTIVE/PAUSE → ACTIVE）。 */
  activatePolicy(policyNo: string): Promise<ApiResponse<PolicyView>> {
    return client.post<never, ApiResponse<PolicyView>>(
      `/policies/${encodeURIComponent(policyNo)}/activate`,
    );
  },

  /** 停用保单责任（ACTIVE → PAUSE）。 */
  pausePolicy(policyNo: string): Promise<ApiResponse<PolicyView>> {
    return client.post<never, ApiResponse<PolicyView>>(
      `/policies/${encodeURIComponent(policyNo)}/pause`,
    );
  },

  /** 某保单下的计划列表。 */
  listPlans(policyNo: string): Promise<ApiResponse<PlanView[]>> {
    return client.get<never, ApiResponse<PlanView[]>>('/plans', { params: { policyNo } });
  },

  /** 新建计划（草稿态）。 */
  createPlan(req: CreatePlanReq): Promise<ApiResponse<PlanView>> {
    return client.post<CreatePlanReq, ApiResponse<PlanView>>('/plans', req);
  },

  /** 发布计划（DRAFT → ACTIVE）。 */
  publishPlan(planId: string): Promise<ApiResponse<PlanView>> {
    return client.post<never, ApiResponse<PlanView>>(`/plans/${encodeURIComponent(planId)}/publish`);
  },

  /** 下线计划。 */
  retirePlan(planId: string): Promise<ApiResponse<PlanView>> {
    return client.post<never, ApiResponse<PlanView>>(`/plans/${encodeURIComponent(planId)}/retire`);
  },

  /** 某计划下的险种列表。 */
  listCoverages(planId: string): Promise<ApiResponse<CoverageView[]>> {
    return client.get<never, ApiResponse<CoverageView[]>>('/coverages', { params: { planId } });
  },

  /** 新建险种（草稿态）。 */
  createCoverage(req: CreateCoverageReq): Promise<ApiResponse<CoverageView>> {
    return client.post<CreateCoverageReq, ApiResponse<CoverageView>>('/coverages', req);
  },

  /** 编辑保单（不改动保单号与规则开关；启用中不可改承保期间）。 */
  updatePolicy(policyNo: string, req: UpdatePolicyReq): Promise<ApiResponse<PolicyView>> {
    return client.put<UpdatePolicyReq, ApiResponse<PolicyView>>(
      `/policies/${encodeURIComponent(policyNo)}`,
      req,
    );
  },

  /** 编辑计划（不改动计划编码与状态）。 */
  updatePlan(planId: string, req: UpdatePlanReq): Promise<ApiResponse<PlanView>> {
    return client.put<UpdatePlanReq, ApiResponse<PlanView>>(`/plans/${encodeURIComponent(planId)}`, req);
  },

  /** 编辑险种（不改动险种编码与状态）。 */
  updateCoverage(coverageId: string, req: UpdateCoverageReq): Promise<ApiResponse<CoverageView>> {
    return client.put<UpdateCoverageReq, ApiResponse<CoverageView>>(
      `/coverages/${encodeURIComponent(coverageId)}`,
      req,
    );
  },

  /** 发布险种（DRAFT → ACTIVE）：与计划同语义的二级发布。 */
  publishCoverage(coverageId: string): Promise<ApiResponse<CoverageView>> {
    return client.post<never, ApiResponse<CoverageView>>(
      `/coverages/${encodeURIComponent(coverageId)}/publish`,
    );
  },

  /** 下线险种（ACTIVE/DRAFT → RETIRED）。 */
  retireCoverage(coverageId: string): Promise<ApiResponse<CoverageView>> {
    return client.post<never, ApiResponse<CoverageView>>(
      `/coverages/${encodeURIComponent(coverageId)}/retire`,
    );
  },

  /** 某保单下的额度账户列表（分页 + 筛选）。 */
  listQuotaAccounts(params: {
    policyNo: string;
    /** 被保人假名化 ID 关键字（模糊匹配）。 */
    keyword?: string;
    /** 额度维度过滤。 */
    quotaType?: QuotaType;
    /** 减人状态过滤：true=已减人 false=正常，缺省不过滤。 */
    removed?: boolean;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<QuotaAccountView>>> {
    return client.get<never, ApiResponse<PageResult<QuotaAccountView>>>('/quota-accounts', {
      params,
    });
  },

  /**
   * 导出额度账户（CSV）：与列表同一套筛选条件、全量不分页。
   *
   * <p><b>口径保证</b>：后端导出复用列表的 Criteria，"页面所见 = 导出所得"；
   * 若前端自行翻页拼接导出，翻页间数据变动会导致漏行/重行。
   */
  exportQuotaAccounts(params: {
    policyNo: string;
    keyword?: string;
    quotaType?: QuotaType;
    removed?: boolean;
  }): Promise<Blob> {
    return client.get<never, Blob>('/quota-accounts/export', {
      params,
      responseType: 'blob',
    });
  },

  /**
   * 公账（责任额度池）汇总：LIABILITY_CAP 账户按责任 UUID 聚合。
   * 池级口径（"这项责任今年总共还能赔多少"），供公账视图顶部对账用。
   */
  async publicSummary(policyNo: string): Promise<ApiResponse<PublicQuotaSummaryView[]>> {
    return client.get<never, ApiResponse<PublicQuotaSummaryView[]>>(
      '/quota-accounts/public-summary',
      { params: { policyNo } },
    );
  },

  /** 某被保人在某保单下的额度账户列表。 */
  listQuotaAccountsByInsured(
    policyNo: string,
    insuredIdHash: string,
  ): Promise<ApiResponse<QuotaAccountView[]>> {
    return client.get<never, ApiResponse<QuotaAccountView[]>>('/quota-accounts/by-insured', {
      params: { policyNo, insuredIdHash },
    });
  },

  /** 某额度账户的保全记录（谁、何时、从多少改到多少）。 */
  listQuotaChanges(quotaAccountId: string): Promise<ApiResponse<QuotaChangeView[]>> {
    return client.get<never, ApiResponse<QuotaChangeView[]>>(
      `/quota-accounts/${encodeURIComponent(quotaAccountId)}/changes`,
    );
  },

  /** 开户（初始化个人额度）；同键重复调用幂等返回既有账户。 */
  openQuotaAccount(req: OpenQuotaReq): Promise<ApiResponse<QuotaAccountView>> {
    return client.post<OpenQuotaReq, ApiResponse<QuotaAccountView>>('/quota-accounts', req);
  },

  /** 调额；下调不得低于已占用额度（已赔 + 已冻结）。 */
  adjustQuota(
    quotaAccountId: string,
    req: AdjustQuotaReq,
  ): Promise<ApiResponse<QuotaAccountView>> {
    return client.post<AdjustQuotaReq, ApiResponse<QuotaAccountView>>(
      `/quota-accounts/${encodeURIComponent(quotaAccountId)}/adjust`,
      req,
    );
  },

  /** 减人（不再参与额度累计）。 */
  removeInsured(
    quotaAccountId: string,
    req: InsuredStateReq,
  ): Promise<ApiResponse<QuotaAccountView>> {
    return client.post<InsuredStateReq, ApiResponse<QuotaAccountView>>(
      `/quota-accounts/${encodeURIComponent(quotaAccountId)}/remove`,
      req,
    );
  },

  /** 恢复（撤销减人）。 */
  restoreInsured(
    quotaAccountId: string,
    req: InsuredStateReq,
  ): Promise<ApiResponse<QuotaAccountView>> {
    return client.post<InsuredStateReq, ApiResponse<QuotaAccountView>>(
      `/quota-accounts/${encodeURIComponent(quotaAccountId)}/restore`,
      req,
    );
  },

  /** 按计划列出责任（含各版本）。 */
  listLiabilities(planId: string): Promise<ApiResponse<LiabilityView[]>> {
    return client.get<never, ApiResponse<LiabilityView[]>>(
      `/liabilities/plan/${encodeURIComponent(planId)}`,
    );
  },

  /** 新建责任（草稿态，版本 v1）。 */
  createLiability(req: CreateLiabilityReq): Promise<ApiResponse<LiabilityView>> {
    return client.post<CreateLiabilityReq, ApiResponse<LiabilityView>>('/liabilities', req);
  },

  /** 编辑责任（ACTIVE 生成新版本，DRAFT 原地编辑；空字段沿用原值）。 */
  updateLiability(
    liabilityId: string,
    req: UpdateLiabilityReq,
  ): Promise<ApiResponse<LiabilityView>> {
    return client.put<UpdateLiabilityReq, ApiResponse<LiabilityView>>(
      `/liabilities/${encodeURIComponent(liabilityId)}`,
      req,
    );
  },

  /** 发布责任（DRAFT → ACTIVE；同 UUID 旧 ACTIVE 版本自动下线）。 */
  publishLiability(liabilityId: string): Promise<ApiResponse<LiabilityView>> {
    return client.post<never, ApiResponse<LiabilityView>>(
      `/liabilities/${encodeURIComponent(liabilityId)}/publish`,
    );
  },

  /** 下线责任（ACTIVE/DRAFT → RETIRED）。 */
  retireLiability(liabilityId: string): Promise<ApiResponse<LiabilityView>> {
    return client.post<never, ApiResponse<LiabilityView>>(
      `/liabilities/${encodeURIComponent(liabilityId)}/retire`,
    );
  },
};

/** 把后端分页信封的 total 归一成 number（后端 Long 序列化为字符串）。 */
export function toTotal(result: PageResult<unknown> | undefined): number {
  return normalizeTotal(result?.total);
}
