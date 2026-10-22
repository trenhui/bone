/**
 * 栅格布局真源（协议 props.span，24 栏制）——预览与运行时共用同一计算。
 *
 * 背景（2026-10-18 诊断 P0-1）：栅格宽度编辑器写入 props.span，但协议白名单曾无此项
 * （lint 报 ERROR、保存被拦），且预览只兑现 span>=24 的整行、运行时不读 span——
 * 「配了不兑现」的死控件。本模块把 span 语义收敛为单一实现：
 * 设计器预览（SchemaPreview）与运行时表单（BoneDynamicForm）都从这里取栅格样式，
 * 双渲染器漂移从此有机器可判的真源（改一头另一头不变 ⇒ 一致性探针可抓）。
 *
 * 口径（与编辑器选项一致）：24=整行；12=半行（缺省，保持历史双栏行为）；
 * 8=1/3 行；6=1/4 行。越界值（<1 或 >24、非数）按缺省 12 收敛，不猜渲染。
 * CHILD_TABLE / ATTACHMENT / TIMELINE 恒整行（窄栏放不下表格/附件/时间线）。
 */

import type { CSSProperties } from 'react';
import type { Control } from '@bone/ui-schema';

/** 协议栅格总栏数（props.span 的上界） */
export const GRID_COLUMNS = 24;

/** 缺省跨度：半行（保持历史「固定双栏」视觉，旧 schema 无 span 时行为不变） */
export const DEFAULT_SPAN = 12;

/** 恒整行的控件类型（窄栏放不下；与预览历史口径一致） */
const FULL_WIDTH_KINDS: ReadonlySet<string> = new Set(['CHILD_TABLE', 'ATTACHMENT', 'TIMELINE']);

/** 控件跨度（1-24）；越界/缺失收敛为 DEFAULT_SPAN */
export function spanOf(control: Control): number {
  const raw = control.props?.span;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    const n = Math.floor(raw);
    if (n >= 1 && n <= GRID_COLUMNS) return n;
  }
  return DEFAULT_SPAN;
}

/** 是否整行控件：span=24 或表格/附件/时间线 */
export function isFullWidthControl(control: Control): boolean {
  return spanOf(control) >= GRID_COLUMNS || FULL_WIDTH_KINDS.has(control.controlKind ?? '');
}

/** 栅格容器样式（24 栏等宽）；预览分区与运行时 CardSection 共用 */
export const GRID_CONTAINER_STYLE: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: `repeat(${GRID_COLUMNS}, minmax(0, 1fr))`,
  gap: '14px 18px',
};

/**
 * 单项栅格样式：整行 → `1 / -1`；否则 `span N`。
 * 调用方在 split 分栏模式下不套用（分栏为单列堆叠，栅格无意义）。
 */
export function gridItemStyle(control: Control): CSSProperties {
  return { gridColumn: isFullWidthControl(control) ? '1 / -1' : `span ${spanOf(control)}` };
}
