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

/** 作业开关：NEED 需生成 / NOT_NEED 无需生成。 */
export type DocFlag = 'NEED' | 'NOT_NEED';

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
  /** 理赔申请书作业开关：NEED 需生成 / NOT_NEED 无需生成。 */
  docClaimApply: DocFlag;
  /** 理赔通知单作业开关：NEED 需生成 / NOT_NEED 无需生成。 */
  docClaimNotice: DocFlag;
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

// ===================== 基础目录库（0094） =====================

/**
 * 医院目录项。socialFlag：1=社保定点 / 0=非定点（理算按它过滤"社保内医院"类规则）。
 * hospitalLevel：一甲/二甲/三甲/三特 等租户自定义等级。
 */
export interface HospitalView {
  id: string;
  hospitalCode: string;
  hospitalName: string;
  hospitalLevel?: string;
  socialFlag?: number;
  province?: string;
  city?: string;
}

/** 疾病目录项。icdCategory：ICD 大类（如 ICD-10 章节码），供筛选与展示。 */
export interface DiseaseView {
  id: string;
  diseaseCode: string;
  diseaseName: string;
  icdCategory?: string;
}

/** 药品目录项。medicalType：甲类/乙类/丙类 等医保分类。 */
export interface DrugView {
  id: string;
  drugCode: string;
  drugName: string;
  socialFlag?: number;
  medicalType?: string;
}

/** 费用类别目录项（床位费/检查费/手术费/药品费…；责任费用规则按 feeCode 挂载）。 */
export interface FeeCategoryView {
  id: string;
  feeCode: string;
  feeName: string;
  sortNo?: number;
}

export interface HospitalCommand {
  code: string;
  name: string;
  level?: string;
  socialFlag?: number;
  province?: string;
  city?: string;
}

export interface DiseaseCommand {
  code: string;
  name: string;
  icdCategory?: string;
}

export interface DrugCommand {
  code: string;
  name: string;
  socialFlag?: number;
  medicalType?: string;
}

export interface FeeCategoryCommand {
  code: string;
  name: string;
  sortNo?: number;
}

/** 责任费用类别规则（一条责任 × 一个费用类别的赔付口径）。金额为字符串（同 LiabilityView 口径）。 */
export interface FeeRuleView {
  id: string;
  feeCode: string;
  feeName: string;
  /** 赔付比例（0-100 的数字文本）。 */
  payPercent: string;
  /** 单次限额（空/0 = 不限）。 */
  limitAmount: string;
  /** 1=仅社保内 / 0=不限。 */
  socialOnly: number;
}

/** 保存费用规则的单条项（全量替换语义）。 */
export interface FeeRuleItem {
  feeCode: string;
  payPercent?: string;
  limitAmount?: string;
  socialOnly?: number;
}

/** 保存费用规则请求：items 即该责任的最终规则集（后端先软删旧行再插入）。 */
export interface SaveFeeRulesReq {
  items: FeeRuleItem[];
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
  /** 理赔申请书作业开关（缺省 NEED）。 */
  docClaimApply?: DocFlag;
  /** 理赔通知单作业开关（缺省 NEED）。 */
  docClaimNotice?: DocFlag;
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
  /** 理赔申请书作业开关（不传沿用原值）。 */
  docClaimApply?: DocFlag;
  /** 理赔通知单作业开关（不传沿用原值）。 */
  docClaimNotice?: DocFlag;
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

  // ===================== 基础目录库（0094） =====================

  /** 医院库分页列表（keyword 模糊匹配编码/名称）。 */
  listHospitals(params: {
    keyword?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<HospitalView>>> {
    return client.get<never, ApiResponse<PageResult<HospitalView>>>('/libraries/hospitals', {
      params,
    });
  },

  /** 新建医院目录项（同租户同编码唯一）。 */
  createHospital(req: HospitalCommand): Promise<ApiResponse<HospitalView>> {
    return client.post<HospitalCommand, ApiResponse<HospitalView>>('/libraries/hospitals', req);
  },

  /** 编辑医院目录项。 */
  updateHospital(id: string, req: HospitalCommand): Promise<ApiResponse<HospitalView>> {
    return client.put<HospitalCommand, ApiResponse<HospitalView>>(
      `/libraries/hospitals/${encodeURIComponent(id)}`,
      req,
    );
  },

  /** 疾病库分页列表。 */
  listDiseases(params: {
    keyword?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<DiseaseView>>> {
    return client.get<never, ApiResponse<PageResult<DiseaseView>>>('/libraries/diseases', {
      params,
    });
  },

  /** 新建疾病目录项。 */
  createDisease(req: DiseaseCommand): Promise<ApiResponse<DiseaseView>> {
    return client.post<DiseaseCommand, ApiResponse<DiseaseView>>('/libraries/diseases', req);
  },

  /** 编辑疾病目录项。 */
  updateDisease(id: string, req: DiseaseCommand): Promise<ApiResponse<DiseaseView>> {
    return client.put<DiseaseCommand, ApiResponse<DiseaseView>>(
      `/libraries/diseases/${encodeURIComponent(id)}`,
      req,
    );
  },

  /** 药品库分页列表。 */
  listDrugs(params: {
    keyword?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<DrugView>>> {
    return client.get<never, ApiResponse<PageResult<DrugView>>>('/libraries/drugs', { params });
  },

  /** 新建药品目录项。 */
  createDrug(req: DrugCommand): Promise<ApiResponse<DrugView>> {
    return client.post<DrugCommand, ApiResponse<DrugView>>('/libraries/drugs', req);
  },

  /** 编辑药品目录项。 */
  updateDrug(id: string, req: DrugCommand): Promise<ApiResponse<DrugView>> {
    return client.put<DrugCommand, ApiResponse<DrugView>>(
      `/libraries/drugs/${encodeURIComponent(id)}`,
      req,
    );
  },

  /** 费用类别分页列表。 */
  listFeeCategories(params: {
    keyword?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<FeeCategoryView>>> {
    return client.get<never, ApiResponse<PageResult<FeeCategoryView>>>('/libraries/fee-categories', {
      params,
    });
  },

  /** 费用类别全量列表（按 sortNo 升序；供费用规则下拉等场景）。 */
  listAllFeeCategories(): Promise<ApiResponse<FeeCategoryView[]>> {
    return client.get<never, ApiResponse<FeeCategoryView[]>>('/libraries/fee-categories/all');
  },

  /** 新建费用类别。 */
  createFeeCategory(req: FeeCategoryCommand): Promise<ApiResponse<FeeCategoryView>> {
    return client.post<FeeCategoryCommand, ApiResponse<FeeCategoryView>>(
      '/libraries/fee-categories',
      req,
    );
  },

  /** 编辑费用类别。 */
  updateFeeCategory(id: string, req: FeeCategoryCommand): Promise<ApiResponse<FeeCategoryView>> {
    return client.put<FeeCategoryCommand, ApiResponse<FeeCategoryView>>(
      `/libraries/fee-categories/${encodeURIComponent(id)}`,
      req,
    );
  },

  // ===================== 责任费用类别规则（0094） =====================

  /** 某责任的费用类别规则列表（全量）。 */
  listFeeRules(liabilityId: string): Promise<ApiResponse<FeeRuleView[]>> {
    return client.get<never, ApiResponse<FeeRuleView[]>>(
      `/liabilities/${encodeURIComponent(liabilityId)}/fee-rules`,
    );
  },

  /** 保存某责任的费用类别规则（全量替换：先软删旧行再插入新行）。 */
  saveFeeRules(liabilityId: string, req: SaveFeeRulesReq): Promise<ApiResponse<FeeRuleView[]>> {
    return client.put<SaveFeeRulesReq, ApiResponse<FeeRuleView[]>>(
      `/liabilities/${encodeURIComponent(liabilityId)}/fee-rules`,
      req,
    );
  },
};

// ===================== 多保司主数据映射（承保主体 / 保司码表 / 码值映射 / 未命中队列） =====================
// 后端 `CodeMappingController`，挂在 `/api/v1/claim-config/mappings`。
//
// <p><b>为什么需要这一族接口</b>：保单上的 `insurer_name` 此前只是自由文本，无法作为映射键——同一家保司被写成
// "永诚财险" / "永诚财产保险股份有限公司" 时映射会分裂成两套。承保主体（insurer）是受控替代：保单通过
// `bizIdentityCode` 引用 `insurer_code`；不同保司对同一主数据（医院/疾病/药品/费用类别）的码值、名称、标准不同，
// 由「保司码 → 标准码」映射层消化，责任规则只认标准码。

/** 主数据域（与后端 `MasterDataDomain` 对齐）。 */
export type MappingDomain = 'HOSPITAL' | 'DISEASE' | 'DRUG' | 'FEE_CATEGORY' | 'CERT_TYPE';

/** 映射类型（与后端 `MappingType` 对齐）。 */
export type MappingType = 'EXACT' | 'SYNONYM' | 'GROUP';

/** 匹配规则（与后端 `MatchRule` 对齐）。 */
export type MatchRule =
  | 'CODE_EQUAL'
  | 'CREDIT_CODE'
  | 'NAME_NORM'
  | 'FUZZY'
  | 'MANUAL'
  | 'AI_SUGGEST';

/** 映射来源（与后端 `MappingSource` 对齐）。 */
export type MappingSource = 'MANUAL' | 'IMPORT' | 'SUGGEST_CONFIRMED';

/** 承保主体类型（与后端 `InsurerType` 对齐）。 */
export type InsurerType = 'DIRECT' | 'BROKER' | 'AGENT';

/** 承保主体状态（与后端 `InsurerStatus` 对齐）。 */
export type InsurerStatus = 'ACTIVE' | 'DISABLED' | 'TERMINATED';

/** 承保主体视图。 */
export interface InsurerView {
  id: string;
  insurerCode: string;
  insurerName: string;
  insurerShortName?: string;
  insurerType?: InsurerType;
  creditCode?: string;
  parentCode?: string;
  status?: InsurerStatus;
}

/** 码值映射视图（保司码 → 标准码）。 */
export interface MappingView {
  id: string;
  insurerCode: string;
  domain: MappingDomain;
  insurerItemCode: string;
  disambiguationKey?: string;
  standardCode: string;
  status: string;
  active: boolean;
  versionNo?: string;
}

/** 未命中队列项（收单侧遇保司码无映射时入队，供运营补映射）。 */
export interface MissView {
  id: string;
  insurerCode: string;
  domain: MappingDomain;
  rawCode: string;
  rawName?: string;
  occurCount: number;
  reopenCount: number;
  status: string;
  lastCaseNo?: string;
  resolvedMappingId?: string;
}

/** 保司码表项（这家保司怎么叫某条主数据）。 */
export interface CodeItemView {
  id: string;
  insurerCode: string;
  domain: MappingDomain;
  itemCode: string;
  itemName: string;
  nameNormalized?: string;
  status?: string;
}

/** 主数据域展示标签（前端单选用，与库内 `domain` 码值一一对应）。 */
export const DOMAIN_LABEL: Record<MappingDomain, string> = {
  HOSPITAL: '医院',
  DISEASE: '疾病',
  DRUG: '药品',
  FEE_CATEGORY: '费用类别',
  CERT_TYPE: '证件类型',
};

/** 承保主体类型展示标签。 */
export const INSURER_TYPE_LABEL: Record<InsurerType, string> = {
  DIRECT: '直保',
  BROKER: '经纪',
  AGENT: '代理',
};

/** 承保主体状态展示标签。 */
export const INSURER_STATUS_LABEL: Record<InsurerStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
  TERMINATED: '终止合作',
};

export interface RegisterInsurerReq {
  insurerCode: string;
  insurerName: string;
  insurerShortName?: string;
  insurerType?: InsurerType;
  creditCode?: string;
  parentCode?: string;
}

export interface PublishMappingReq {
  insurerCode: string;
  domain: MappingDomain;
  insurerItemCode: string;
  disambiguationKey?: string;
  standardCode: string;
  mappingType?: MappingType;
  matchRule?: MatchRule;
  matchScore?: number;
  disambiguationJson?: string;
  source?: MappingSource;
  versionNo?: string;
  reviewerId?: string;
}

export interface UpsertCodeItemReq {
  insurerCode: string;
  domain: MappingDomain;
  itemCode: string;
  itemName: string;
  extraJson?: string;
  sourceBatchNo?: string;
}

export const mappingApi = {
  /** 注册承保主体。 */
  registerInsurer(req: RegisterInsurerReq): Promise<ApiResponse<InsurerView>> {
    return client.post<RegisterInsurerReq, ApiResponse<InsurerView>>('/mappings/insurers', req);
  },

  /** 承保主体分页列表（keyword 模糊匹配编码/名称）。 */
  listInsurers(params: { keyword?: string; page?: number; size?: number }): Promise<
    ApiResponse<PageResult<InsurerView>>
  > {
    return client.get<never, ApiResponse<PageResult<InsurerView>>>('/mappings/insurers', { params });
  },

  /** 维护保司码表项（同键 upsert）。 */
  upsertCodeItem(req: UpsertCodeItemReq): Promise<ApiResponse<CodeItemView>> {
    return client.post<UpsertCodeItemReq, ApiResponse<CodeItemView>>('/mappings/code-items', req);
  },

  /** 保司码表项分页列表。 */
  listCodeItems(params: {
    insurerCode?: string;
    domain?: MappingDomain;
    keyword?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<CodeItemView>>> {
    return client.get<never, ApiResponse<PageResult<CodeItemView>>>('/mappings/code-items', {
      params,
    });
  },

  /** 发布一条映射（同键旧行自动失效）。 */
  publishMapping(req: PublishMappingReq): Promise<ApiResponse<MappingView>> {
    return client.post<PublishMappingReq, ApiResponse<MappingView>>('/mappings', req);
  },

  /** 当前生效映射分页。 */
  listMappings(params: {
    insurerCode?: string;
    domain?: MappingDomain;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<MappingView>>> {
    return client.get<never, ApiResponse<PageResult<MappingView>>>('/mappings', { params });
  },

  /** 停用映射（保留历史行）。 */
  disableMapping(id: string, reasonCode?: string): Promise<ApiResponse<MappingView>> {
    return client.post<unknown, ApiResponse<MappingView>>(
      `/mappings/${encodeURIComponent(id)}/disable`,
      null,
      { params: { reasonCode } },
    );
  },

  /** 未命中队列分页。 */
  listMisses(params: {
    status?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResult<MissView>>> {
    return client.get<never, ApiResponse<PageResult<MissView>>>('/mappings/misses', { params });
  },

  /** 处置未命中：建立映射并关闭队列项。 */
  resolveMiss(id: string, req: PublishMappingReq): Promise<ApiResponse<MissView>> {
    return client.post<PublishMappingReq, ApiResponse<MissView>>(
      `/mappings/misses/${encodeURIComponent(id)}/resolve`,
      req,
    );
  },

  /** 忽略未命中（运营判定该码无需映射）。 */
  ignoreMiss(id: string, reason: string): Promise<ApiResponse<MissView>> {
    return client.post<unknown, ApiResponse<MissView>>(
      `/mappings/misses/${encodeURIComponent(id)}/ignore`,
      null,
      { params: { reason } },
    );
  },

  /** 解析保司码到标准码（试算；未命中会入队）。 */
  resolve(req: {
    insurerCode: string;
    domain: MappingDomain;
    rawCode: string;
    rawName?: string;
    disambiguationKey?: string;
    caseNo?: string;
  }): Promise<
    ApiResponse<{ status: string; standardCode?: string; mappingId?: string; reasonCode?: string; missRecorded: boolean }>
  > {
    return client.post<
      unknown,
      ApiResponse<{
        status: string;
        standardCode?: string;
        mappingId?: string;
        reasonCode?: string;
        missRecorded: boolean;
      }>
    >('/mappings/resolve', req);
  },
};

/** 把后端分页信封的 total 归一成 number（后端 Long 序列化为字符串）。 */
export function toTotal(result: PageResult<unknown> | undefined): number {
  return normalizeTotal(result?.total);
}
