/**
 * AI 建议「曝光 → 采纳 / 驳回 / 修改」的事件层（纯函数 + 浏览器事件总线）。
 *
 * 为什么是「纯事件总线」+「独立写库」
 * ------------------------
 * P0 阶段（2026-11-12）取证：理赔业务后端**当时没有** AI 建议采纳端点。平台网关不转发
 * `/api/ai/v1/**`，通用 `HitlController` 调不到；前端若发一个"看起来成功"的 POST，就是拿 404
 * 冒充回流成功 —— 正是项目反复踩的「配了不生效 / 假绿」。因此把曝光/裁决先做成一等的、
 * 结构化的、可测的浏览器事件，32 例单测锁定其纯总线语义。
 *
 * **v2.1（2026-11-12 同轮）**：理赔域后端契约已补齐并真机联调通过。本文件保持纯函数事件层
 * 不变（既有单测零改动），`persisted: false` 仅表示该事件对象自身不是落库真源；
 * 真实写库由独立的 `persistWorkbenchEvent`（`workbench/persist.ts`）异步完成，
 * 写库失败只返回 `{ ok:false }`、绝不把本地事件标记为已落库。后端端点就绪后无需动本文件。
 *
 * 业界依据
 * --------
 * - Insurance Thought Leadership 2026：**AI 建议采纳率是可持续成功的领先指标**，
 *   「用户绕开系统」是早期预警；必须建立捕获边角案例的反馈机制。
 * - AmasaTech / Duck Creek 指导：分阶段上线须先达到 **90% human-AI agreement rate**
 *   再启用 full STP；跳过该步会因严重度误分类带来 **15–20% 返工增长**。
 *   ⇒ 采纳率的分母必须是**曝光**次数，不是决策次数，否则口径被美化。
 * - Five Sigma 四级自主度：capability（模型能做什么）与 permission（允许它做什么）是两个旋钮；
 *   本层只记录人的裁决，不因事件而扩大任何自动生效范围。
 * - NAIC Model Bulletin（2026 中已有 24 州采纳）：每条 AI 影响的不利决定都要可解释、可审计、
 *   有人类复核 ⇒ reject/modify 强制受控原因码，自由文本不能替代。
 */

import { isSafeClaimId } from './caseDeepLinks';

/** 与后端 `bone-ai-orchestration/.../hitl/ReasonType.java:17-22` 逐字对齐；**不得随意扩充**。 */
export const REASON_TYPES = [
  'RULE_MISS', 'CONTEXT_MISS', 'SUGGESTION_WRONG', 'POLICY_CONFLICT',
] as const;

export type ReasonType = (typeof REASON_TYPES)[number];

export const REASON_LABELS: Record<ReasonType, string> = {
  RULE_MISS: '规则漏判（应命中而未命中）',
  CONTEXT_MISS: '上下文缺失（判定所需字段未到位）',
  SUGGESTION_WRONG: '建议本身错误',
  POLICY_CONFLICT: '与既有策略冲突',
};

export type AiDisposition = 'ADOPT' | 'REJECT' | 'MODIFY';

/** 浏览器事件名：治理面可订阅，未来后端接线也读同一批事件。 */
export const WORKBENCH_EVENT = 'bone:workbench:event';

export interface WorkbenchEventBase {
  schemaVersion: 1;
  /** 客户端生成的幂等键（UUID），与后端 `event_id` 一一对应；重放返回首次结果。 */
  eventId: string;
  /** ISO 时间戳。 */
  at: string;
  claimId: string;
  traceId: string;
  /** AI 参与四态之一；UNDETERMINED 不得被当成「AI 未参与」。 */
  involvement: string;
  /** 产生事件的场景；盲标禁区拒绝一切回流。 */
  scene: 'case' | 'review' | 'blind_label';
  /** **关键**：本地事件只在浏览器内生成；落库由 `persistWorkbenchEvent` 异步完成，不在此篡改。 */
  persisted: false;
  backendContract: 'pending';
}

/** 生成事件幂等键：优先用 Web Crypto，退化到 time+random 也不影响校验语义。 */
function genEventId(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  return `evt-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export interface ImpressionEvent extends WorkbenchEventBase {
  type: 'ai_suggestion_impression';
}

export interface DispositionEvent extends WorkbenchEventBase {
  type: 'ai_suggestion_disposition';
  disposition: AiDisposition;
  /** reject / modify 必填，且必须来自 REASON_TYPES 受控字典。 */
  reasonCode?: ReasonType;
  /** 人工备注，仅展示用，不参与结构化回流、不可代替 reasonCode。 */
  comment?: string;
  /** modify 时记录被修改的字段名（只记字段名，不记金额值）。 */
  modifiedFields?: readonly string[];
}

export type WorkbenchEvent = ImpressionEvent | DispositionEvent;

export interface EventContext {
  claimId: string;
  traceId?: string;
  involvement?: string;
  /** 盲标工作台等禁区：不得曝光 AI 建议，防反馈环污染金标准。 */
  scene?: 'case' | 'review' | 'blind_label';
}

export type BuildResult<T> = { ok: true; event: T } | { ok: false; error: string };

function base(ctx: EventContext): { value: WorkbenchEventBase } | { error: string } {
  if (!isSafeClaimId(ctx.claimId)) {
    return { error: '案件 ID 非法：拒绝生成埋点事件（防把用户输入拼进审计链）' };
  }
  return {
    value: {
      schemaVersion: 1,
      eventId: genEventId(),
      at: new Date().toISOString(),
      claimId: ctx.claimId.trim(),
      traceId: (ctx.traceId ?? '').trim(),
      involvement: (ctx.involvement ?? 'UNDETERMINED').trim() || 'UNDETERMINED',
      scene: (ctx.scene ?? 'case') as WorkbenchEventBase['scene'],
      persisted: false,
      backendContract: 'pending',
    },
  };
}

/** 曝光事件：采纳率的分母。盲标禁区直接拒绝，不生成事件。 */
export function buildImpressionEvent(ctx: EventContext): BuildResult<ImpressionEvent> {
  if (ctx.scene === 'blind_label') {
    return { ok: false, error: '盲标金标准禁区：不得曝光 aiSuggestion，防反馈环污染' };
  }
  const built = base(ctx);
  if ('error' in built) return { ok: false, error: built.error };
  return { ok: true, event: { ...built.value, type: 'ai_suggestion_impression' } };
}

export interface DispositionContext extends EventContext {
  disposition: AiDisposition;
  reasonCode?: string;
  comment?: string;
  modifiedFields?: readonly string[];
}

/** 裁决事件：reject/modify 强制受控原因码；事件体永远不含金额（AI 不碰钱）。 */
export function buildDispositionEvent(ctx: DispositionContext): BuildResult<DispositionEvent> {
  if (ctx.scene === 'blind_label') {
    return { ok: false, error: '盲标金标准禁区：不得在标注面产生 AI 裁决回流' };
  }
  const built = base(ctx);
  if ('error' in built) return { ok: false, error: built.error };

  const needsReason = ctx.disposition === 'REJECT' || ctx.disposition === 'MODIFY';
  if (needsReason && !ctx.reasonCode) {
    return { ok: false, error: `${ctx.disposition} 必须选择受控原因码（ReasonType），自由文本不可替代` };
  }
  if (ctx.reasonCode && !REASON_TYPES.includes(ctx.reasonCode as ReasonType)) {
    return { ok: false, error: `未登记的原因码 ${ctx.reasonCode}：不得写进回流管道` };
  }

  const event: DispositionEvent = {
    ...built.value,
    type: 'ai_suggestion_disposition',
    disposition: ctx.disposition,
  };
  if (ctx.reasonCode) event.reasonCode = ctx.reasonCode as ReasonType;
  if (ctx.comment) event.comment = ctx.comment.slice(0, 200);
  if (ctx.modifiedFields && ctx.modifiedFields.length > 0) event.modifiedFields = ctx.modifiedFields.slice(0, 20);
  return { ok: true, event };
}

const MAX_BUFFER = 50;
const buffer: WorkbenchEvent[] = [];

/** 最近事件（副本），供工作台「建议采纳回流」面板显示本地样本；不代表服务端统计。 */
export function recentWorkbenchEvents(): WorkbenchEvent[] {
  return buffer.slice();
}

/** 广播事件。SSR / 单测无 window 时仍写入本地缓冲，但返回 false。 */
export function emitWorkbenchEvent(event: WorkbenchEvent): boolean {
  buffer.push(event);
  if (buffer.length > MAX_BUFFER) buffer.splice(0, buffer.length - MAX_BUFFER);
  if (typeof window === 'undefined') return false;
  window.dispatchEvent(new CustomEvent(WORKBENCH_EVENT, { detail: event }));
  return true;
}

export function subscribeWorkbenchEvents(listener: (event: WorkbenchEvent) => void): () => void {
  if (typeof window === 'undefined') return () => {};
  const handler = (e: Event) => listener((e as CustomEvent<WorkbenchEvent>).detail);
  window.addEventListener(WORKBENCH_EVENT, handler);
  return () => window.removeEventListener(WORKBENCH_EVENT, handler);
}

/** 仅测试用：清空本地缓冲。 */
export function resetWorkbenchEvents(): void {
  buffer.splice(0, buffer.length);
}
