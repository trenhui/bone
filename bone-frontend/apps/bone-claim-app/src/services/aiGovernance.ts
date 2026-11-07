import { apiCall, createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

/** AI 治理只读契约：后端 5 个 `/api/v1/claim-ops` GET 端点。 */
export interface AiCapabilityDeclaration {
  capabilityId: string;
  state: string;
  reason: string;
  remediation: string;
}

export interface ReadinessSnapshot {
  riskTier: string;
  autoApproveEnabled: boolean;
  capabilities: AiCapabilityDeclaration[];
  blockers: string[];
  deploymentGatesPassed: boolean | null;
  scopeNote: string;
}

export interface AiRuntimeHealth {
  windowFrom: string;
  windowTo: string;
  nodesScanned: number;
  truncated: boolean;
  aiParticipated: number;
  aiStubbed: number;
  aiModelUnproven: number;
  aiDegraded: number;
  aiUndetermined: number;
  unparsableOutputs: number;
  participationRate: number | string | null;
  degradedByReason: Record<string, number>;
  attributionCoverage: number | string | null;
  capabilityMissing: number;
  capabilityByReason: Record<string, number>;
  knowledgeBasisByReason: Record<string, number>;
  knowledgeBasisUnattributed: number;
  aiUnverifiableLocators: number;
  aiLocatorUnattributed: number;
  note: string;
}

export interface CalibrationReport {
  sampleCount: number;
  bucketCount: number;
  expectedCalibrationError: number | string | null;
  brierScore: number | string | null;
  currentThreshold: number | string | null;
  recommendedThreshold: number | string | null;
  coveredByRecommended: number;
  verdict: string;
  basis: string;
  buckets: CalibrationBucket[];
  slices: CalibrationSlice[];
}

export interface AiCalibrationReport {
  requestedLimit: number;
  fetchedTasks: number;
  sampleCount: number;
  unparseableSamples: number;
  calibration: CalibrationReport;
  effectiveMinSamples: number;
  effectiveMinSamplesPerSlice: number;
}

export interface RolloutSnapshot {
  stage: string;
  rolloutPct: number;
  errorRateTarget: number | string | null;
  minInspectedSamples: number;
  observationDays: number;
  active: boolean;
  inspectedCount: number;
  diffCount: number;
  errorRate: number | string | null;
  remainingRatio: number | string | null;
  sampleSufficient: boolean;
  truncated: boolean;
  verdict: string;
  nextStage: string;
  promotionReady: boolean;
  promotionBlockers: string[];
}

export interface RolloutDrillReport {
  currentStage: string;
  rollbackTargetStage: string;
  atFloor: boolean;
  autoSettlementAfterRollback: boolean | null;
  modelParticipationRequiredAfterRollback: boolean | null;
  integrityIntact: boolean | null;
  mutations: number;
  forbiddenMutations: string[];
  basis: string;
}

export interface ProviderProbeReport {
  verdict: string | null;
  configured: boolean;
  reachable: boolean;
  model: string | null;
  provenance: string | null;
  latencyMs: number;
  reason: string | null;
  action: string | null;
  note: string | null;
}

export interface CalibrationBucket {
  bucketFrom: number | string | null;
  bucketTo: number | string | null;
  sampleCount: number;
  meanConfidence: number | string | null;
  accuracy: number | string | null;
  gap: number | string | null;
}

export interface CalibrationSlice {
  sliceId: string;
  sampleCount: number;
  brierScore: number | string | null;
  accuracy: number | string | null;
}

export type GovernanceTone = 'success' | 'warning' | 'error' | 'neutral';

export interface StatusMeta {
  label: string;
  tone: GovernanceTone;
  detail: string;
}

export const CALIBRATION_VERDICTS = [
  'NO_EVIDENCE',
  'UNTRUSTED_EVIDENCE',
  'INSUFFICIENT_SAMPLES',
  'SLICE_INCOMPLETE',
  'NO_SAFE_THRESHOLD',
  'THRESHOLD_PROPOSED',
] as const;

export const ROLLOUT_STAGES = ['SHADOW', 'ASSISTED', 'CANARY', 'AUTOMATED'] as const;

const VERDICT_LABEL: Record<string, { label: string; tone: GovernanceTone; detail: string }> = {
  NO_EVIDENCE: { label: '无盲标证据', tone: 'warning', detail: '冻结集无独立盲标配对样本，不得宣称模型已校准。' },
  UNTRUSTED_EVIDENCE: { label: '证据不可信', tone: 'error', detail: '样本来源非盲标或冻结集未隔离，整批拒绝。' },
  INSUFFICIENT_SAMPLES: { label: '样本不足', tone: 'warning', detail: '样本量低于下限，ECE/Brier 不具统计意义。' },
  SLICE_INCOMPLETE: { label: '切片覆盖不全', tone: 'warning', detail: '阈值不得跨切片推广。' },
  NO_SAFE_THRESHOLD: { label: '无安全阈值', tone: 'error', detail: '目标错误率下反解不出阈值，不得自动放量。' },
  THRESHOLD_PROPOSED: { label: '阈值建议已产出', tone: 'success', detail: '仅为离线建议，须独立验证与放量门禁批准后才生效。' },
};

const STAGE_LABEL: Record<string, { label: string; tone: GovernanceTone; detail: string }> = {
  SHADOW: { label: '影子模式', tone: 'neutral', detail: 'AI 结论不参与决策，不自动放款。' },
  ASSISTED: { label: '辅助模式', tone: 'warning', detail: 'AI 仅出建议，人工最终决策，不自动放款。' },
  CANARY: { label: '灰度', tone: 'warning', detail: '限定范围内允许自动出金，受错误率预算约束。' },
  AUTOMATED: { label: '自动', tone: 'success', detail: '允许自动出金，须持续满足错误率预算与复验要求。' },
};

const CAPABILITY_LABEL: Record<string, { label: string; tone: GovernanceTone }> = {
  READY: { label: '已就绪', tone: 'success' },
  LOCAL_STUB: { label: '本地替身', tone: 'error' },
  NOT_CONFIGURED: { label: '未配置', tone: 'warning' },
  UNKNOWN: { label: '未知', tone: 'warning' },
};

const ROLLOUT_VERDICT_LABEL: Record<string, { label: string; tone: GovernanceTone }> = {
  EVIDENCE_TRUNCATED: { label: '证据被截断', tone: 'warning' },
  NO_EVIDENCE: { label: '无证据', tone: 'warning' },
  INSUFFICIENT_SAMPLES: { label: '样本不足', tone: 'warning' },
  OVER_TARGET: { label: '超出错误率目标', tone: 'error' },
  WITHIN_BUDGET: { label: '在预算内', tone: 'success' },
};

/** 三态门禁：false 不得显示达标，null 不得降级成 false 或 0。 */
export function gatesMeta(passed: boolean | null | undefined): StatusMeta {
  if (passed === true) {
    return { label: '已达标', tone: 'success', detail: '部署门禁全部通过。' };
  }
  if (passed === false) {
    return { label: '未达标', tone: 'error', detail: '存在未解除的门禁项，不得宣称 L3。' };
  }
  return { label: '未测过', tone: 'warning', detail: '后端未返回门禁判定，不得按达标或 0 处理。' };
}

/** 数值缺失显示占位符；0 必须保留为 0，字符串枚举/时间原样显示，不猜测其语义。 */
export function metricText(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—（无数据）';
  if (typeof value === 'string') return value;
  if (!Number.isFinite(value)) return `无法解析：${String(value)}`;
  return String(value);
}

function numeric(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

/** 参与率：null 表示分母为 0，0 表示模型未上场，两者不可混写。 */
export function participationMeta(rate: number | string | null | undefined): StatusMeta {
  const n = numeric(rate);
  if (n === null) {
    return { label: '无可计算样本', tone: 'warning', detail: '分母为 0，后端返回 null；不得写成 0%。' };
  }
  if (n === 0) {
    return { label: '模型未参与', tone: 'error', detail: '本窗口内无真实模型参与节点，不得据此判定 AI 已参与决策。' };
  }
  if (n < 0 || n > 1) {
    return { label: `越界值：${String(rate)}`, tone: 'error', detail: '参与率超出 [0,1]，不得乐观映射。' };
  }
  return { label: `${(n * 100).toFixed(2)}%`, tone: 'success', detail: '按已落库决策轨迹节点统计。' };
}

/** 未知校准结论码降级显示原始码并告警。 */
export function verdictMeta(code: string | null | undefined): StatusMeta {
  if (code && VERDICT_LABEL[code]) return { ...VERDICT_LABEL[code] };
  const raw = code === null || code === undefined || code === '' ? '(空)' : String(code);
  return { label: `未登记结论：${raw}`, tone: 'warning', detail: '后端返回未登记结论码，不得按已校准处理。' };
}

/** SHADOW/ASSISTED 不得写成「已自动放款」。 */
export function stageMeta(stage: string | null | undefined): StatusMeta {
  if (stage && STAGE_LABEL[stage]) return { ...STAGE_LABEL[stage] };
  const raw = stage === null || stage === undefined || stage === '' ? '(空)' : String(stage);
  return { label: `未登记档位：${raw}`, tone: 'warning', detail: '未知档位按最保守口径处理，不得假定可自动出金。' };
}

export function capabilityStateMeta(state: string | null | undefined): StatusMeta {
  if (state && CAPABILITY_LABEL[state]) {
    return { ...CAPABILITY_LABEL[state], detail: `能力自报状态：${state}` };
  }
  const raw = state === null || state === undefined || state === '' ? '(空)' : String(state);
  return { label: `未登记状态：${raw}`, tone: 'warning', detail: '未知能力状态不得按已就绪处理。' };
}

export function rolloutVerdictMeta(code: string | null | undefined): StatusMeta {
  if (code && ROLLOUT_VERDICT_LABEL[code]) {
    return { ...ROLLOUT_VERDICT_LABEL[code], detail: `放量预算结论：${code}` };
  }
  const raw = code === null || code === undefined || code === '' ? '(空)' : String(code);
  return { label: `未登记结论：${raw}`, tone: 'warning', detail: '未知预算结论不得按达标处理。' };
}

/** 回滚演练：false 必须显示「不自动出金」，null 不得当成 false。 */
const PROBE_VERDICT_LABEL: Record<string, { label: string; tone: GovernanceTone }> = {
  NOT_CONFIGURED: { label: '未配置真实 provider', tone: 'warning' },
  REACHABLE: { label: '链路可达', tone: 'success' },
  MOCK_NOT_ALLOWED: { label: '拿到网关替身响应', tone: 'error' },
  OUTPUT_CONTRACT_VIOLATION: { label: '上游返回非合法 JSON', tone: 'error' },
  PROVIDER_TIMEOUT: { label: '上游超时', tone: 'warning' },
  GATEWAY_ERROR: { label: '网关异常', tone: 'error' },
};

/** 探活判词：REACHABLE 只表示本次调用可达，不得写成「模型判断准确」。 */
export function probeMeta(
  report: ProviderProbeReport | null | undefined,
  error?: string | null,
): StatusMeta {
  if (error) {
    return { label: '未测过', tone: 'warning', detail: `探活请求失败：${error}；不得假定链路可用。` };
  }
  if (!report) {
    return { label: '未测过', tone: 'warning', detail: '尚未点击探活；本端点每次真实消耗一次 provider 用量，不做自动轮询。' };
  }
  const code = report.verdict;
  const known = code ? PROBE_VERDICT_LABEL[code] : undefined;
  if (!known) {
    const raw = code === null || code === undefined || code === '' ? '(空)' : String(code);
    return { label: `未登记判词：${raw}`, tone: 'warning', detail: '后端返回未登记判词，按最保守口径处理，不得按连通处理。' };
  }
  const detail = code === 'REACHABLE'
    ? '只证明本次调用可达且返回合法 JSON，不证明模型判断准确；准确率见评测样本 agreementRate。'
    : (report.action ?? `判词：${code}`);
  return { label: known.label, tone: known.tone, detail };
}

/** 校准明细是否可渲染：禁止态或明细为空一律不画曲线，不编造数值。 */
export function calibrationEvidenceMeta(
  report: AiCalibrationReport | null | undefined,
): StatusMeta & { renderable: boolean; buckets: CalibrationBucket[]; slices: CalibrationSlice[] } {
  const c = report?.calibration;
  const buckets = Array.isArray(c?.buckets) ? c.buckets : [];
  const slices = Array.isArray(c?.slices) ? c.slices : [];
  const forbidden = ['NO_EVIDENCE', 'UNTRUSTED_EVIDENCE', 'INSUFFICIENT_SAMPLES',
    'SLICE_INCOMPLETE', 'NO_SAFE_THRESHOLD'];
  if (!report || !c) {
    return { label: '无校准证据', tone: 'warning', detail: '校准端点未返回数据，不得渲染分桶/切片曲线。',
      renderable: false, buckets, slices };
  }
  if (forbidden.includes(c.verdict)) {
    return { label: verdictMeta(c.verdict).label, tone: 'warning',
      detail: `${verdictMeta(c.verdict).detail} 本状态下不渲染分桶/切片曲线。`,
      renderable: false, buckets, slices };
  }
  if (buckets.length === 0 && slices.length === 0) {
    return { label: '无校准明细', tone: 'warning', detail: '后端未返回分桶或切片明细，不得编造曲线。',
      renderable: false, buckets, slices };
  }
  return { label: '校准明细可查', tone: 'success', detail: `分桶 ${buckets.length} 个、切片 ${slices.length} 个，数值原样展示。`,
    renderable: true, buckets, slices };
}

export function drillMeta(autoSettlement: boolean | null | undefined): StatusMeta {
  if (autoSettlement === false) {
    return { label: '回滚后不自动出金', tone: 'success', detail: '演练确认回滚后自动出金被关闭。' };
  }
  if (autoSettlement === true) {
    return { label: '回滚后仍自动出金', tone: 'error', detail: '违反回滚不变量，须立即排查。' };
  }
  return { label: '未测过', tone: 'warning', detail: '演练未返回该判定，不得假定已关闭自动出金。' };
}

/**
 * 证据窗口单一真源：以 UTC 00:00 为日界，返回 [to-30天, to]，to 为下一个 UTC 日界
 * （覆盖当日全天）。同一 UTC 日内多次调用结果恒定，避免前后端相差数秒导致分母漂移；
 * 长度恰 30 天，严格小于端点的 31 天上限。E2E 的 EVIDENCE_WINDOW 使用同一算法。
 * 字符串统一为秒级 ISO（`...Z`，不带毫秒）：后端 windowFrom/windowTo 是 Jackson 序列化的
 * `Instant`，带 `.000` 会与回显差 4 个字符，逐字一致断言必须靠同一份规范串，而不是比较时再归一化。
 */
export function governanceWindow(): { from: string; to: string } {
  const dayMs = 24 * 60 * 60 * 1000;
  const toMs = Math.ceil(Date.now() / dayMs) * dayMs;
  const isoSecond = (ms: number) => new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z');
  return {
    from: isoSecond(toMs - 30 * dayMs),
    to: isoSecond(toMs),
  };
}

const opsClient = createApiClient('/api/v1/claim-ops');

export const aiGovernanceApi = {
  readiness(): Promise<ReadinessSnapshot> {
    return apiCall(opsClient.get<never, ApiResponse<ReadinessSnapshot>>('/ai-readiness'));
  },
  runtimeHealth(from: string, to: string): Promise<AiRuntimeHealth> {
    return apiCall(opsClient.get<never, ApiResponse<AiRuntimeHealth>>(
      '/ai-runtime-health', { params: { from, to } },
    ));
  },
  calibration(): Promise<AiCalibrationReport> {
    return apiCall(opsClient.get<never, ApiResponse<AiCalibrationReport>>('/ai-calibration/report'));
  },
  rollout(from: string, to: string): Promise<RolloutSnapshot> {
    return apiCall(opsClient.get<never, ApiResponse<RolloutSnapshot>>(
      '/ai-rollout', { params: { from, to } },
    ));
  },
  drill(): Promise<RolloutDrillReport> {
    return apiCall(opsClient.get<never, ApiResponse<RolloutDrillReport>>('/ai-rollout/drill'));
  },
  /** 仅由用户显式点击触发；调用方不得放进默认加载或轮询。 */
  providerProbe(): Promise<ProviderProbeReport> {
    return apiCall(opsClient.get<never, ApiResponse<ProviderProbeReport>>('/ai-provider-probe'));
  },
};
