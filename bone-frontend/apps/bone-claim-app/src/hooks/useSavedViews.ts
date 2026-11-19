import { useCallback, useState } from 'react';

/**
 * 保存视图：把一组查询条件（URL 查询串）命名后本地留存，支持收藏与最近访问。
 *
 * <p><b>为什么是本地而非服务端</b>：提案 §5.3 的保存视图属「个人工作台偏好」，与角色权限无关；
 * 服务端持久化需要先有消费方与迁移（项目纪律：新增字段必先找消费方），故一期落在 localStorage，
 * 避免制造「配了不生效」的服务端表。视图只存查询串，不存数据本身，不触碰 PII。
 */
export interface SavedView {
  /** 稳定标识（本地生成，时间戳 + 随机段）。 */
  id: string;
  name: string;
  /** 相对查询串，如 `q=ABC&f_status=43`；用于重建 query 状态。 */
  queryString: string;
  /** 收藏置顶；最近访问另计，不与收藏混用一个语义。 */
  pinned: boolean;
  createdAt: number;
}

export interface SavedViewsController {
  views: SavedView[];
  /** 保存当前查询串为命名视图。同名覆盖（避免重复收藏同一条件造成困惑）。 */
  save: (name: string, queryString: string) => void;
  remove: (id: string) => void;
  togglePin: (id: string) => void;
}

const STORAGE_KEY = 'bone-claim-saved-views-v1';
const MAX_VIEWS = 20;

function loadViews(): SavedView[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    // 逐条校验形状，坏数据整条丢弃而不是让坏对象流进 UI 引发渲染异常。
    return parsed.filter(
      (v): v is SavedView =>
        typeof v === 'object' &&
        v !== null &&
        typeof (v as SavedView).id === 'string' &&
        typeof (v as SavedView).name === 'string' &&
        typeof (v as SavedView).queryString === 'string' &&
        typeof (v as SavedView).pinned === 'boolean' &&
        typeof (v as SavedView).createdAt === 'number',
    );
  } catch {
    return [];
  }
}

function persist(views: SavedView[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(views));
  } catch {
    // 存储不可用（隐私模式 / 配额满）时降级为内存态，不影响当前会话作业。
  }
}

function newId(): string {
  return `v-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function useSavedViews(): SavedViewsController {
  const [views, setViews] = useState<SavedView[]>(() => loadViews());

  const commit = useCallback((next: SavedView[]) => {
    setViews(next);
    persist(next);
  }, []);

  const save = useCallback(
    (name: string, queryString: string) => {
      const trimmed = name.trim();
      if (!trimmed) return;
      commit((() => {
        const current = loadViews();
        const deduped = current.filter((v) => !(v.name === trimmed && v.queryString === queryString));
        // 同名同条件覆盖；容量上限丢弃最旧且未置顶的视图，置顶视图不被静默挤掉。
        const next: SavedView[] = [
          ...deduped,
          { id: newId(), name: trimmed, queryString, pinned: false, createdAt: Date.now() },
        ];
        if (next.length <= MAX_VIEWS) return next;
        const overflow = next.length - MAX_VIEWS;
        let removed = 0;
        return next.filter((v) => {
          if (removed < overflow && !v.pinned) {
            removed += 1;
            return false;
          }
          return true;
        });
      })());
    },
    [commit],
  );

  const remove = useCallback(
    (id: string) => {
      commit(loadViews().filter((v) => v.id !== id));
    },
    [commit],
  );

  const togglePin = useCallback(
    (id: string) => {
      commit(loadViews().map((v) => (v.id === id ? { ...v, pinned: !v.pinned } : v)));
    },
    [commit],
  );

  return { views, save, remove, togglePin };
}
