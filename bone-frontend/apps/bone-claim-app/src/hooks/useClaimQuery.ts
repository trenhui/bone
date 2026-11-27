import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * 统一查询状态：列表筛选的唯一真源是 URL。
 *
 * <p><b>为什么状态进 URL</b>：提案 §5.2 把「作业上下文可恢复」列为查询便利性的前提——筛选条件
 * 如果只存组件 useState，就不透分享、浏览器前进/后退丢失、通知深链无法带条件回到列表。
 * 仓库既有真源是 `useClaimIdParam.ts` 的 query 参数（6 个单案页全从 `?claimId=` 读 ID），
 * 本 hook 沿用同一渠道，不另立 path 参数，避免「同一语义两处配置源」。
 */
export interface ClaimQueryState {
  /** 关键字（案件号 / 保单号 / 姓名等，由各页决定语义）。 */
  keyword: string;
  /** 页码，从 1 起；URL 非法值归一为 1。 */
  page: number;
  /** 每页条数；URL 非法值归一为默认 20。 */
  size: number;
  /** 结构化筛选（状态 / 时间 / 金额区间 / 风险等级 / 作业组 / 保司 / 保单），键由调用方定义。 */
  filters: Record<string, string>;
}

export interface ClaimQueryController {
  query: ClaimQueryState;
  /** 更新关键字并回到第 1 页（换检索词后停在第 5 页会得到空结果）。 */
  setKeyword: (keyword: string) => void;
  /** 更新单个筛选；传空串删除该键，并回到第 1 页。 */
  setFilter: (key: string, value: string) => void;
  /** 翻页。页码 <1 归一为 1。 */
  setPage: (page: number) => void;
  /** 改每页条数并回到第 1 页。 */
  setSize: (size: number) => void;
  /** 清空本 hook 管理的全部查询参数，保留其它参数（claimId / tab 等）。 */
  reset: () => void;
  /** 整体应用一段查询串（保存视图回灌）；同样保留外部参数。 */
  applyQueryString: (queryString: string) => void;
  /** 当前托管查询参数串（保存视图用；不含 claimId / tab 等外部参数）。 */
  currentQueryString: () => string;
}

const KEYWORD = 'q';
const PAGE = 'page';
const SIZE = 'size';
const FILTER_PREFIX = 'f_';
const DEFAULT_SIZE = 20;

function positiveInt(raw: string | null, fallback: number): number {
  if (raw === null || raw === '') return fallback;
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : fallback;
}

/** 只保留本 hook 管理的键；其余 query 参数原样带回，防止清筛选时误删深链上下文。 */
function isManagedKey(key: string): boolean {
  return key === KEYWORD || key === PAGE || key === SIZE || key.startsWith(FILTER_PREFIX);
}

export function useClaimQuery(defaultSize: number = DEFAULT_SIZE): ClaimQueryController {
  const [searchParams, setSearchParams] = useSearchParams();

  const query = useMemo<ClaimQueryState>(() => {
    const filters: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (key.startsWith(FILTER_PREFIX)) filters[key.slice(FILTER_PREFIX.length)] = value;
    });
    return {
      keyword: searchParams.get(KEYWORD) ?? '',
      page: positiveInt(searchParams.get(PAGE), 1),
      size: positiveInt(searchParams.get(SIZE), defaultSize),
      filters,
    };
  }, [searchParams, defaultSize]);

  /**
   * 基于「当前」URL 增量改写，而非写死对象——否则会覆盖 claimId / tab 等外部参数。
   * 与 `useClaimIdParam` 的 `setSearchParams({ claimId: id })` 不同：那个 hook 只管一个键，
   * 本 hook 与其它 hook 共存于同一 URL，必须 merge 而不是 replace。
   */
  /**
   * 同一事件内连续 patch 的暂存。
   *
   * <p>React Router v6 的 `setSearchParams(fn)` 中 fn 收到的 `prev` 来自**渲染时**的 location
   * 快照，不是上一次调用的结果。同一事件里连着改两个参数（例如切 Tab 同时 setPage(1) +
   * setKeyword('')）时，第二次会基于旧快照、把第一次的改动整段覆盖——表现为「筛选清了但页码还在」
   * 或反过来，属「配了不生效」同族的静默故障。这里用 ref 把本次事件内的连续更新串起来。
   */
  const pendingRef = useRef<URLSearchParams | null>(null);

  // URL 真正落地后再清空暂存；下一事件从最新地址栏出发，不复用过期快照。
  useEffect(() => {
    pendingRef.current = null;
  }, [searchParams]);

  const patch = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(pendingRef.current ?? prev);
        mutate(next);
        pendingRef.current = next;
        return next;
      });
    },
    [setSearchParams],
  );

  const goFirstPage = useCallback(
    (next: URLSearchParams) => {
      next.delete(PAGE);
    },
    [],
  );

  const setKeyword = useCallback(
    (keyword: string) => {
      patch((next) => {
        if (keyword) next.set(KEYWORD, keyword);
        else next.delete(KEYWORD);
        goFirstPage(next);
      });
    },
    [patch, goFirstPage],
  );

  const setFilter = useCallback(
    (key: string, value: string) => {
      patch((next) => {
        const urlKey = `${FILTER_PREFIX}${key}`;
        if (value) next.set(urlKey, value);
        else next.delete(urlKey);
        goFirstPage(next);
      });
    },
    [patch, goFirstPage],
  );

  const setPage = useCallback(
    (page: number) => {
      patch((next) => {
        if (page > 1) next.set(PAGE, String(Math.trunc(page)));
        else next.delete(PAGE);
      });
    },
    [patch],
  );

  const setSize = useCallback(
    (size: number) => {
      patch((next) => {
        next.set(SIZE, String(size > 0 ? Math.trunc(size) : defaultSize));
        next.delete(PAGE);
      });
    },
    [patch, defaultSize],
  );

  const reset = useCallback(() => {
    patch((next) => {
      for (const key of [...next.keys()]) if (isManagedKey(key)) next.delete(key);
    });
  }, [patch]);

  const applyQueryString = useCallback(
    (queryString: string) => {
      patch((next) => {
        // 先清掉旧查询参数，再灌入视图的查询串，避免两个视图叠加出幽灵筛选。
        for (const key of [...next.keys()]) if (isManagedKey(key)) next.delete(key);
        const incoming = new URLSearchParams(queryString.replace(/^\?/, ''));
        incoming.forEach((value, key) => {
          if (isManagedKey(key) && value) next.set(key, value);
        });
      });
    },
    [patch],
  );

  const currentQueryString = useCallback(() => {
    const next = new URLSearchParams();
    searchParams.forEach((value, key) => {
      if (isManagedKey(key) && value) next.set(key, value);
    });
    return next.toString();
  }, [searchParams]);

  return {
    query,
    setKeyword,
    setFilter,
    setPage,
    setSize,
    reset,
    applyQueryString,
    currentQueryString,
  };
}
