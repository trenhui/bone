import { apiCall, createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

/**
 * 独立盲标工作台契约：后端 `/api/v1/claim-ops/blind-labels` 5 个端点。
 *
 * <p>生命周期（防反馈环）：派发（载荷过 AI 痕迹禁区）→ 提交（一次性）→ 解盲（AI 建议快照此时才落库）。
 * 解盲前任何视图都不含 AI 侧字段——前端不做任何「AI 建议预览」，这是后端契约决定的，不是 UI 取舍。
 */

export interface BlindLabelTaskView {
  /** 主键与案件 ID 均为后端 Long → JSON 字符串（雪花 ID 超 2^53，number 会被静默截断）。 */
  id: string;
  taskNo: string;
  claimId: string;
  assigneeId: string;
  status: string;
  labelPayloadJson: string;
  payloadHash: string;
  labelResultJson: string | null;
  submittedAt: string | null;
  aiSuggestionJson: string | null;
  agreement: string | null;
  unblindedAt: string | null;
  aiParticipated: boolean;
}

/** 载荷禁区键（与后端 BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS 逐字一致；后端是权威，前端扫描只是提前提示）。 */
export const FORBIDDEN_PAYLOAD_KEYS: readonly string[] = [
  'aiSuggestion',
  'aiConfidence',
  'aiAvailable',
  'aiBasis',
  'confidence',
  'confidenceSource',
  'ruleConfidence',
  'ruleHits',
  'triageReason',
  'decisionResult',
  'decisionTier',
  'modelVersion',
  'fallback',
  'knowledgeBasis',
];

/** 载荷长度上限（与后端 MAX_PAYLOAD_LENGTH 一致）。 */
export const MAX_PAYLOAD_LENGTH = 8000;

export type GovernanceTone = 'success' | 'warning' | 'error' | 'neutral';

export interface StatusMeta {
  label: string;
  tone: GovernanceTone;
  detail: string;
}

const STATUS_LABEL: Record<string, StatusMeta> = {
  PENDING: { label: '待标注', tone: 'warning', detail: '载荷已派发，标注人只看材料引用，不看 AI 侧任何字段。' },
  LABELED: { label: '已提交待解盲', tone: 'neutral', detail: '盲标结论已一次性提交，提交后不可改写；解盲后 AI 快照才落库。' },
  UNBLINDED: { label: '已解盲配对', tone: 'success', detail: 'AI 建议快照与盲标结论配对完成，可作为独立金标准样本。' },
};

/** 任务状态：未登记码按最保守口径展示原文，不猜测语义。 */
export function statusMeta(status: string | null | undefined): StatusMeta {
  if (status && STATUS_LABEL[status]) return { ...STATUS_LABEL[status] };
  const raw = status === null || status === undefined || status === '' ? '(空)' : String(status);
  return { label: `未登记状态：${raw}`, tone: 'warning', detail: '后端返回未登记状态，不得按可提交或已解盲处理。' };
}

const AGREEMENT_LABEL: Record<string, StatusMeta> = {
  PAIRED: { label: 'AI 真实参与（配对成立）', tone: 'success', detail: '解盲时决策轨迹 confidence_source=MODEL，AI 建议快照已入库。' },
  AI_ABSENT: { label: 'AI 未参与（AI_ABSENT）', tone: 'warning', detail: 'AI 建议缺失或非模型置信度来源，样本只能作人工判定参考，不进模型评测。' },
};

/** 配对结论：null（未解盲）与未知码语义不同，不得混写。 */
export function agreementMeta(agreement: string | null | undefined): StatusMeta {
  if (agreement === null || agreement === undefined || agreement === '') {
    return { label: '未解盲', tone: 'neutral', detail: '尚未执行解盲，无配对结论。' };
  }
  if (AGREEMENT_LABEL[agreement]) return { ...AGREEMENT_LABEL[agreement] };
  return { label: `未登记结论：${String(agreement)}`, tone: 'warning', detail: '后端返回未登记配对结论，不得按配对成立处理。' };
}

export interface PayloadScanResult {
  ok: boolean;
  /** 客户端可见的第一批命中键（全部命中时只提示前 3 个，完整裁决以后端 422 为准）。 */
  forbiddenHits: string[];
  /** 空载荷 / 非对象 / 超长等结构性问题；与后端 validatePayload 同口径。 */
  structureError: string | null;
}

/**
 * 载荷禁区扫描（仅 UX 预检）：载荷必须是 JSON 对象、顶层键不得命中 AI 痕迹键、
 * 长度不超上限。后端 validatePayload 是唯一权威——前端放行后仍可能被 422。
 */
export function scanPayload(raw: string | null | undefined): PayloadScanResult {
  const text = raw ?? '';
  if (text.length > MAX_PAYLOAD_LENGTH) {
    return { ok: false, forbiddenHits: [], structureError: `载荷超过 ${MAX_PAYLOAD_LENGTH} 字符上限（当前 ${text.length}）` };
  }
  if (!text.trim()) {
    return { ok: false, forbiddenHits: [], structureError: '载荷不能为空' };
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, forbiddenHits: [], structureError: '载荷必须是合法 JSON 对象' };
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return { ok: false, forbiddenHits: [], structureError: '载荷必须是 JSON 对象（顶层不能是数组或标量）' };
  }
  const keys = Object.keys(parsed as Record<string, unknown>);
  if (keys.length === 0) {
    return { ok: false, forbiddenHits: [], structureError: '载荷不能是空对象' };
  }
  const forbiddenHits = keys.filter((k) => FORBIDDEN_PAYLOAD_KEYS.includes(k));
  if (forbiddenHits.length > 0) {
    return { ok: false, forbiddenHits, structureError: `载荷含 AI 痕迹键，盲标前提被破坏：${forbiddenHits.slice(0, 3).join('、')}` };
  }
  return { ok: true, forbiddenHits: [], structureError: null };
}

/** 结论 JSON 校验：必须为合法 JSON 对象（后端只校验非空，宽松些；前端强约束便于渲染配对视图）。 */
export function scanLabelResult(raw: string | null | undefined): string | null {
  const text = raw ?? '';
  if (!text.trim()) return '结论不能为空';
  try {
    const parsed: unknown = JSON.parse(text);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return '结论必须是 JSON 对象';
    }
    return null;
  } catch {
    return '结论必须是合法 JSON';
  }
}

const opsClient = createApiClient('/api/v1/claim-ops');

export const blindLabelApi = {
  /** 按标注人查最近任务（上限 50）。 */
  list(assigneeId: string): Promise<BlindLabelTaskView[]> {
    return apiCall(opsClient.get<never, ApiResponse<BlindLabelTaskView[]>>(
      '/blind-labels', { params: { assigneeId } },
    ));
  },
  get(id: string): Promise<BlindLabelTaskView> {
    return apiCall(opsClient.get<never, ApiResponse<BlindLabelTaskView>>(`/blind-labels/${id}`));
  },
  dispatch(claimId: string, assigneeId: string, labelPayloadJson: string): Promise<BlindLabelTaskView> {
    return apiCall(opsClient.post<never, ApiResponse<BlindLabelTaskView>>(
      '/blind-labels', { claimId, assigneeId, labelPayloadJson },
    ));
  },
  submit(id: string, labelResultJson: string): Promise<BlindLabelTaskView> {
    return apiCall(opsClient.post<never, ApiResponse<BlindLabelTaskView>>(
      `/blind-labels/${id}/submit`, { labelResultJson },
    ));
  },
  unblind(id: string): Promise<BlindLabelTaskView> {
    return apiCall(opsClient.post<never, ApiResponse<BlindLabelTaskView>>(
      `/blind-labels/${id}/unblind`,
    ));
  },
};
