/**
 * 建模工作台「继续建模」（最近编辑实体）——localStorage 记录（2b F1，批次1 无后端依赖）。
 *
 * 记录键：bone-mw-recent，结构 [{id, code, displayName, appId, moduleId, ts}]，最多 8 条。
 * EntityDetail 打开实体时 touch；工作台首页渲染 Top5，点击直达详情。
 */
export interface RecentEntityItem {
  id: number;
  code: string;
  displayName: string;
  appId?: string;
  moduleId?: string;
  ts: number;
}

const KEY = 'bone-mw-recent';
const MAX = 8;

export function listRecent(): RecentEntityItem[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const list = JSON.parse(raw) as RecentEntityItem[];
    return Array.isArray(list) ? list.filter((x) => x && x.id) : [];
  } catch {
    return [];
  }
}

export function touchRecent(item: Omit<RecentEntityItem, 'ts'>): void {
  try {
    const rest = listRecent().filter((x) => x.id !== item.id);
    const next = [{ ...item, ts: Date.now() }, ...rest].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // localStorage 不可用（隐私模式等）时静默降级：最近编辑只是体验增强
  }
}

export function recentPath(item: RecentEntityItem): string {
  if (item.appId && item.moduleId) {
    return `/apps/${item.appId}/modules/${item.moduleId}/entities/${item.id}`;
  }
  return `/entities/${item.id}`;
}
