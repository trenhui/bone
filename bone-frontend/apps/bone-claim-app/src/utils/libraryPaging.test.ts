// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
import { LIBRARY_MAX_PAGES, LIBRARY_PAGE_SIZE, fetchAllPages } from './libraryPaging';

/** 造 n 条记录（id 从 from 起），只关心条数与顺序。 */
const make = (from: number, n: number) => Array.from({ length: n }, (_, i) => ({ id: from + i }));

describe('fetchAllPages', () => {
  it('单页未满即结束，不额外翻页', async () => {
    const fetchPage = vi.fn(async () => ({ data: { records: make(1, 3), total: 3 } }));
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(3);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('多页拼接到末页（回归：>500 条不再静默截断）', async () => {
    const fetchPage = vi.fn(async (page: number) =>
      page === 1
        ? { data: { records: make(1, LIBRARY_PAGE_SIZE), total: 205 } }
        : { data: { records: make(201, 5), total: 205 } },
    );
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(205);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('total 为字符串时也能正确终止', async () => {
    const fetchPage = vi.fn(async () => ({
      data: { records: make(1, LIBRARY_PAGE_SIZE), total: String(LIBRARY_PAGE_SIZE) },
    }));
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(LIBRARY_PAGE_SIZE);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('触达页数上限时显式标记 truncated（绝不静默返回部分数据）', async () => {
    const fetchPage = vi.fn(async () => ({
      data: { records: make(1, LIBRARY_PAGE_SIZE), total: 999_999 },
    }));
    const r = await fetchAllPages(fetchPage);
    expect(r.truncated).toBe(true);
    expect(r.rows).toHaveLength(LIBRARY_MAX_PAGES * LIBRARY_PAGE_SIZE);
    expect(fetchPage).toHaveBeenCalledTimes(LIBRARY_MAX_PAGES);
  });

  it('空页正常结束', async () => {
    const fetchPage = vi.fn(async () => ({ data: { records: [], total: 0 } }));
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toEqual([]);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it('响应缺 data 时按空页处理', async () => {
    const fetchPage = vi.fn(async () => ({}));
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toEqual([]);
    expect(r.truncated).toBe(false);
  });
});
