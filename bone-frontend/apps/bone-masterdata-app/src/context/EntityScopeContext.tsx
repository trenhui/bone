import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { masterDataEntityApi } from '../services/api';
import type { MasterDataEntity } from '../types';
import { useMessage } from '../App';

/**
 * 全局「当前主数据模型」作用域。
 *
 * 背景：分类体系 / 治理看板 / 整改工单等页面此前各自维护一份 entityId，
 * 且部分页面（分类、治理、工单）把它实现成「手输雪花 ID + 加载按钮」——用户无法获知 ID，
 * 页面因此恒为空态。这里把模型选择提升为应用级上下文：一次选择，全站共享，刷新后记忆。
 */
const STORAGE_KEY = 'bone.masterdata.currentEntityId';

interface EntityScopeValue {
  /** 当前租户可见的主数据模型（最多 100 条）。 */
  entities: MasterDataEntity[];
  entitiesLoading: boolean;
  /** 当前选中的模型 ID；列表为空时为 undefined。 */
  entityId?: string;
  currentEntity?: MasterDataEntity;
  setEntityId: (id?: string) => void;
  /** 重新拉取模型列表（新建模型后调用）。 */
  refreshEntities: () => Promise<void>;
}

const EntityScopeContext = createContext<EntityScopeValue | null>(null);

export const EntityScopeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const message = useMessage();
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [entityId, setEntityIdState] = useState<number | undefined>();

  const load = useCallback(async () => {
    setEntitiesLoading(true);
    try {
      const res = await masterDataEntityApi.page({ page: 1, size: 100 });
      if (res.code !== 200) {
        message.error(res.message || '获取主数据模型列表失败');
        return;
      }
      const list = res.data?.records ?? [];
      setEntities(list);
      const saved = Number(localStorage.getItem(STORAGE_KEY));
      // 已选模型若仍在列表中则保持，避免使用中被刷新覆盖；否则回落到记忆值或首个模型
      setEntityIdState((prev) => {
        if (prev !== undefined && list.some((e) => e.id === prev)) return prev;
        if (saved && list.some((e) => e.id === saved)) return saved;
        return list[0]?.id;
      });
    } catch {
      message.error('获取主数据模型列表失败');
    } finally {
      setEntitiesLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const setEntityId = useCallback((id?: string) => {
    setEntityIdState(id);
    if (id === undefined) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, String(id));
    }
  }, []);

  const currentEntity = useMemo(
    () => entities.find((e) => e.id === entityId),
    [entities, entityId]
  );

  const value = useMemo<EntityScopeValue>(
    () => ({
      entities,
      entitiesLoading,
      entityId,
      currentEntity,
      setEntityId,
      refreshEntities: load
    }),
    [entities, entitiesLoading, entityId, currentEntity, setEntityId, load]
  );

  return <EntityScopeContext.Provider value={value}>{children}</EntityScopeContext.Provider>;
};

/** 取全局模型作用域；必须在 EntityScopeProvider 内调用。 */
export function useEntityScope(): EntityScopeValue {
  const ctx = useContext(EntityScopeContext);
  if (!ctx) {
    throw new Error('useEntityScope 必须在 EntityScopeProvider 内使用');
  }
  return ctx;
}

export default EntityScopeContext;
