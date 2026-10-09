import type { MetaField } from '../types';
import { parseEnumValues, type EnumOption } from '@bone/shared-types';

/**
 * 界面列投影（预览 ≡ 数据 的单一真源）。
 *
 * 为什么存在：PRD v0.32 §0.6.3 ——「界面预览列 ≡ 发布后数据列」必须由**同一个纯函数**
 * 投影得到，而不是靠两边各自写一遍后靠测试保证。界面 Tab 的预览与运行时数据 Tab 都调用
 * `projectColumns(fields, overlay)`，结构上杜绝漂移。
 *
 * MVP 覆盖存储：localStorage（PRD D2）。v1.1 服务端化（团队级共享）时只需替换
 * `loadOverlay`/`saveOverlay` 的底层实现，投影逻辑不变。
 */

export interface ColumnOverride {
  /** 是否在界面/数据列表中可见 */
  visible: boolean;
  /** 覆盖显示名（缺省回退到字段 displayName） */
  displayName?: string;
  /** 列序（升序；缺省按 sortOrder → 定义顺序） */
  order?: number;
}

export interface UiOverlay {
  /** 覆盖格式版本，结构变更时 +1；版本不符的本地覆盖将被丢弃 */
  version: number;
  /** 以字段 code 为键的覆盖表 */
  overrides: Record<string, ColumnOverride>;
  /** 密度偏好（可选） */
  density?: 'comfortable' | 'compact';
}

/** 密度 → antd Table size（界面预览与数据表共用，避免两处口径漂移） */
export function tableSizeOf(density?: 'comfortable' | 'compact'): 'middle' | 'small' {
  return density === 'compact' ? 'small' : 'middle';
}

export const UI_OVERLAY_VERSION = 2;

/** UI 渲染契约（PRD §5.5）支持的字段类型；其余一律显式降级，禁静默渲染空控件 */
export const SUPPORTED_RENDER_TYPES = new Set([
  'STRING',
  'TEXT',
  'INTEGER',
  'LONG',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'ENUM',
]);

/**
 * 该字段是否有可用控件/列渲染。
 * 未支持类型必须在表单与预览里显式提示字段名 + 类型，而不是渲染成裸 Input 或空白
 * （PRD §5.5「禁止静默渲染空」）。
 */
export function isRenderSupported(field: MetaField): boolean {
  return SUPPORTED_RENDER_TYPES.has((field.type || '').toUpperCase());
}

/** 是否为 ENUM 字段（控件渲染/列投影需特殊处理） */
export function isEnumField(field: MetaField): boolean {
  return (field.type || '').toUpperCase() === 'ENUM';
}

/** 取 ENUM 字段的选项（优先 field.enumValues，兜底空） */
export function enumOptionsOf(field: MetaField): EnumOption[] {
  return parseEnumValues(field.enumValues);
}

export function defaultOverlay(fields: MetaField[]): UiOverlay {
  // 初始顺序与结构 Tab 的字段排序一致（sortOrder → 定义顺序），保证「默认 = 结构顺序」
  const ordered = [...fields].sort(
    (a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999),
  );
  return {
    version: UI_OVERLAY_VERSION,
    overrides: Object.fromEntries(
      ordered.map((f, i) => [f.code, { visible: true, displayName: f.displayName, order: i }]),
    ),
  };
}

/**
 * 列投影：过滤不可见列 → 覆盖显示名 → 按覆盖列序稳定排序。
 * 纯函数 —— 界面预览与运行时数据共用，保证两处列定义一致。
 */
export function projectColumns(fields: MetaField[], overlay?: UiOverlay | null): MetaField[] {
  if (!overlay) return fields;
  const visible: MetaField[] = [];
  for (const f of fields) {
    const ov = overlay.overrides[f.code];
    if (ov && ov.visible === false) continue;
    const displayName = ov?.displayName ?? f.displayName;
    visible.push(displayName === f.displayName ? f : { ...f, displayName });
  }
  // 稳定排序：有 order 的按 order，无 order（新增字段未进覆盖表）排在末尾并保持相对序
  const withIdx = visible.map((f, i) => ({ f, i, order: overlay.overrides[f.code]?.order }));
  withIdx.sort((a, b) => {
    if (a.order != null && b.order != null) return a.order - b.order;
    if (a.order != null) return -1;
    if (b.order != null) return 1;
    return a.i - b.i;
  });
  return withIdx.map((x) => x.f);
}

/**
 * 在覆盖表内交换两个相邻可见列的顺序（↑ / ↓）。
 * 返回新的覆盖表（不修改入参），顺序号重新紧凑化为 0..n-1。
 */
export function reorderOverlay(
  overlay: UiOverlay,
  orderedCodes: string[],
  code: string,
  direction: 'up' | 'down',
): UiOverlay {
  const idx = orderedCodes.indexOf(code);
  const swapWith = direction === 'up' ? idx - 1 : idx + 1;
  if (idx < 0 || swapWith < 0 || swapWith >= orderedCodes.length) return overlay;
  const next = [...orderedCodes];
  [next[idx], next[swapWith]] = [next[swapWith], next[idx]];
  const overrides: Record<string, ColumnOverride> = { ...overlay.overrides };
  next.forEach((c, i) => {
    overrides[c] = { ...(overrides[c] ?? { visible: true }), order: i };
  });
  return { ...overlay, overrides };
}

/**
 * 会话上下文（租户/用户）—— D2 存储隔离定案（PRD §12.2）：
 * 键 = `bone.meta.overlay.{tenantId}.{userId}.{entityCode}`。
 * 来源：bone-shell 登录后写入 `localStorage['bone-user']`（{id, tenantId}），
 * 微应用（qiankun）同源共享该 localStorage。
 * 兜底策略：无会话（开发直连/未登录预览）→ 'anon'，键退化为两段前缀，
 * 与登录态天然隔离，不会串写真实用户的覆盖配置。
 */
function sessionScope(): string {
  try {
    const raw = localStorage.getItem('bone-user');
    if (raw) {
      const u = JSON.parse(raw) as { id?: string | number; tenantId?: string | number };
      const tid = u.tenantId ?? '0';
      const uid = u.id ?? 'anon';
      return `${tid}.${uid}`;
    }
  } catch {
    /* 解析失败按无会话处理 */
  }
  return '0.anon';
}

export function overlayKey(entityCode: string): string {
  return `bone.meta.overlay.${sessionScope()}.${entityCode}`;
}

export function loadOverlay(entityCode: string): UiOverlay | null {
  try {
    const raw = localStorage.getItem(overlayKey(entityCode));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as UiOverlay;
    if (parsed.version !== UI_OVERLAY_VERSION) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveOverlay(entityCode: string, overlay: UiOverlay): void {
  localStorage.setItem(overlayKey(entityCode), JSON.stringify(overlay));
}

export function clearOverlay(entityCode: string): void {
  localStorage.removeItem(overlayKey(entityCode));
}
