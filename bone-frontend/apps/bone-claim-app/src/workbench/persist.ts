/**
 * 把本地工作台事件异步写回后端（AI 建议曝光 / 人工裁决回流）。
 *
 * <p>为什么单独成文件，而不是塞进 telemetry.ts
 * ----------------------------------------------
 * telemetry.ts 是**纯函数 + 事件总线**，被 workbench.test.ts 直接单测，绝不能引入网络依赖；
 * 写库是副作用，放在这里后 telemetry 的「事件只生成、不声称落库」语义保持不变。
 * 后端就绪后，只在 `emitWorkbenchEvent` 之后追加一次 `persistWorkbenchEvent` 调用即可，页面零改动。
 *
 * <p>fail-closed 设计
 * -------------------
 *  - 写库失败（网络/鉴权/后端 4xx）只返回 { ok:false }，**绝不**把本地事件标记为已落库；
 *  - 后端对同 eventId 幂等重放返回 replayed=true，前端据此提示「已存在同事件」而非「重复计数」；
 *  - 事件体到请求体的映射严格裁剪：金额、决策结论、任何未知字段都不会被转发（与后端 @JsonAnySetter 双保险）。
 */

import { aiFeedbackApi } from '../services/aiFeedback';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { WorkbenchEvent } from './telemetry';

export interface PersistResult {
  ok: boolean;
  /** 是否真实落库（replayed 时也为 true，但属于幂等命中）。 */
  persisted: boolean;
  /** 同 eventId 已存在、本次为幂等重放。 */
  replayed?: boolean;
  error?: string;
}

function toPayload(event: WorkbenchEvent): Record<string, unknown> {
  const isImpression = event.type === 'ai_suggestion_impression';
  const payload: Record<string, unknown> = {
    eventId: event.eventId,
    eventType: isImpression ? 'IMPRESSION' : 'DISPOSITION',
    traceId: event.traceId,
    involvement: event.involvement,
    scene: event.scene ?? 'case',
  };
  if (!isImpression) {
    const d = event as Extract<WorkbenchEvent, { type: 'ai_suggestion_disposition' }>;
    payload.disposition = d.disposition;
    if (d.reasonCode) payload.reasonCode = d.reasonCode;
    if (d.comment) payload.comment = d.comment;
    if (d.modifiedFields && d.modifiedFields.length > 0) {
      payload.modifiedFields = [...d.modifiedFields];
    }
  }
  return payload;
}

export async function persistWorkbenchEvent(event: WorkbenchEvent): Promise<PersistResult> {
  if (typeof window === 'undefined') {
    return { ok: false, persisted: false, error: 'no-window' };
  }
  try {
    const res = await aiFeedbackApi.record(event.claimId, toPayload(event));
    if (res && res.code === 200 && res.data) {
      return { ok: true, persisted: true, replayed: res.data.replayed };
    }
    return { ok: false, persisted: false, error: `code=${res?.code}:${res?.message ?? ''}` };
  } catch (e) {
    return { ok: false, persisted: false, error: resolveErrorMessage(e, 'AI 回流写库失败') };
  }
}
