/**
 * 界面设计器版本快照（P2 · 对标 BONE 业务对象界面设计器原型）。
 *
 * 为什么本地快照而非服务端：
 * - 设计期试错频繁，快照用于「快速回滚到上一版可用布局」；
 * - 服务端化（团队级共享）属于 v1.1 范畴，本地先兜底，协议 Gate 逻辑可平移。
 *
 * 纪律（沿用原型）：
 * - 上限 30 份，超出丢弃最旧；
 * - 恢复前跑协议 Gate（error 级自检问题则阻断恢复），避免把一份坏 schema 顶上来。
 */
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';

const KEY_PREFIX = 'bone-designer-snapshots-';

export interface DesignerSnapshot {
  id: string;
  ts: number;
  entityCode: string;
  label: string;
  views: number;
  schema: BoneUiSchema;
}

export function loadSnaps(entityId: string): DesignerSnapshot[] {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + entityId);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? (arr as DesignerSnapshot[]) : [];
  } catch {
    return [];
  }
}

export function saveSnap(entityId: string, snap: DesignerSnapshot): DesignerSnapshot[] {
  const list = loadSnaps(entityId);
  list.unshift(snap);
  const capped = list.slice(0, 30);
  try {
    localStorage.setItem(KEY_PREFIX + entityId, JSON.stringify(capped));
  } catch {
    /* localStorage 不可用（隐私模式等）：静默降级，仅本次会话有效 */
  }
  return capped;
}

export function removeSnap(entityId: string, id: string): DesignerSnapshot[] {
  const list = loadSnaps(entityId).filter((s) => s.id !== id);
  try {
    localStorage.setItem(KEY_PREFIX + entityId, JSON.stringify(list));
  } catch {
    /* ignore */
  }
  return list;
}

export function fmtSnapTime(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
