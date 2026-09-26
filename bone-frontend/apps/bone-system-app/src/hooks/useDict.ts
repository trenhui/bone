/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { useCallback, useEffect, useState } from 'react';
import { dictApi } from '@/services/api';
import type { DictItem, DictOption } from '@/types';

/** 进程内缓存：字典几乎不变，但每个表单打开都会读一次——不缓存就把往返浪费在重复读上。 */
const optionCache = new Map<string, DictOption[]>();
const treeCache = new Map<string, DictItem[]>();

/**
 * 读取字典下拉数据（平台 + 租户覆盖合并，只含启用且生效中的项）。
 *
 * @param typeCode 值域编码；为空时不发请求（避免条件渲染场景下的无意义请求）
 * @param parentCode 取某父节点的直接子节点，顶层不传
 * @param lang 语言标签（如 zh-CN）；传入时由服务端下发本地化标签
 */
export function useDict(typeCode?: string, parentCode?: string, lang?: string) {
  const [options, setOptions] = useState<DictOption[]>([]);
  const [loading, setLoading] = useState(false);
  const cacheKey = typeCode ? `${typeCode}::${parentCode ?? ''}::${lang ?? ''}` : '';

  const load = useCallback(
    async (force = false) => {
      if (!typeCode) {
        setOptions([]);
        return;
      }
      if (!force && optionCache.has(cacheKey)) {
        setOptions(optionCache.get(cacheKey) as DictOption[]);
        return;
      }
      setLoading(true);
      try {
        const res = await dictApi.getOptions(typeCode, parentCode, lang);
        const list = res.data ?? [];
        optionCache.set(cacheKey, list);
        setOptions(list);
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    },
    [typeCode, parentCode, lang, cacheKey],
  );

  useEffect(() => {
    load();
  }, [load]);

  const labelOf = useCallback(
    (code?: string | null) =>
      code == null || code === '' ? '' : options.find((o) => o.code === code)?.label ?? code,
    [options],
  );

  return { options, loading, refresh: () => load(true), labelOf };
}

/** 读指定层级视图的树（CASCADE 值域用）。 */
export function useDictTree(typeCode?: string, hierarchyCode?: string) {
  const [tree, setTree] = useState<DictItem[]>([]);
  const [loading, setLoading] = useState(false);
  const cacheKey = typeCode ? `${typeCode}::${hierarchyCode ?? 'DEFAULT'}` : '';

  const load = useCallback(
    async (force = false) => {
      if (!typeCode) {
        setTree([]);
        return;
      }
      if (!force && treeCache.has(cacheKey)) {
        setTree(treeCache.get(cacheKey) as DictItem[]);
        return;
      }
      setLoading(true);
      try {
        const res = await dictApi.getItemTree(typeCode, hierarchyCode);
        const list = res.data ?? [];
        treeCache.set(cacheKey, list);
        setTree(list);
      } catch {
        setTree([]);
      } finally {
        setLoading(false);
      }
    },
    [typeCode, hierarchyCode, cacheKey],
  );

  useEffect(() => {
    load();
  }, [load]);

  return { tree, loading, refresh: () => load(true) };
}

/** 字典写入后调用：清掉进程内缓存，避免读到改前的值。 */
export function invalidateDictCache(typeCode?: string) {
  if (!typeCode) {
    optionCache.clear();
    treeCache.clear();
    return;
  }
  for (const key of [...optionCache.keys()]) {
    if (key.startsWith(`${typeCode}::`)) optionCache.delete(key);
  }
  for (const key of [...treeCache.keys()]) {
    if (key.startsWith(`${typeCode}::`)) treeCache.delete(key);
  }
}
