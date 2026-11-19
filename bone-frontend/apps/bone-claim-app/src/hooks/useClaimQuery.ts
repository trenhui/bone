import { useCallback, useMemo } from 'react';
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

export function useClaimQuery(): ClaimQueryController {
  const [searchParams, setSearchParams] = useSearchParams();

  const query = useMemo<ClaimQueryState>(() => {
    const filters: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (key.startsWith(FILTER_PREFIX)) filters[key.slice(FILTER_PREFIX.length)] = value;
    });
    return {
      keyword: searchParams.get(KEYWORD) ?? '',
      page: positiveInt(searchParams.get(PAGE), 1),
      size: positiveInt(searchParams.get(SIZE), DEFAULT_SIZE),
      filters,
    };
  }, [searchParams]);

  /**
   * 基于「当前」URL 增量改写，而非写死对象——否则会覆盖 claimId / tab 等外部参数。
   * 与 `useClaimIdParam` 的 `setSearchParams({ claimId: id })` 不同：那个 hook 只管一个键，
   * 本 hook 与其它 hook 共存于同一 URL，必须 merge 而不是 replace。
   */
  const patch = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        mutate(next);
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
        next.set(SIZE, String(size > 0 ? Math.trunc(size) : DEFAULT_SIZE));
        next.delete(PAGE);
      });
    },
    [patch],
  );

  const reset = useCallback(() => {
    patch((next) => {
      for (const key of [...next.keys()]) if (isManagedKey(key)) next.delete(key);
    });
  }, [patch]);

  return { query, setKeyword, setFilter, setPage, setSize, reset };
}
