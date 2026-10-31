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
}

/** 减人/恢复请求。 */
export interface InsuredStateReq {
  remark?: string;
}

/** 规则开关展示元数据（唯一真源，避免多处各写一份文案）。 */
export const POLICY_CONFIG_STATUS_META: Record<PolicyConfigStatus, { color: string; label: string }> = {
  INACTIVE: { color: 'default', label: '未启用' },
  ACTIVE: { color: 'green', label: '启用中' },
  PAUSE: { color: 'orange', label: '停用中' },
};

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

  /** 某保单下的额度账户列表。 */
  listQuotaAccounts(policyNo: string): Promise<ApiResponse<QuotaAccountView[]>> {
    return client.get<never, ApiResponse<QuotaAccountView[]>>('/quota-accounts', {
      params: { policyNo },
    });
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
};

/** 把后端分页信封的 total 归一成 number（后端 Long 序列化为字符串）。 */
export function toTotal(result: PageResult<unknown> | undefined): number {
  return normalizeTotal(result?.total);
}
