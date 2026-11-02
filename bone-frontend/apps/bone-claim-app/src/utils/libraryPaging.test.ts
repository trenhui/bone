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
    const total = LIBRARY_PAGE_SIZE * 2 + 5;
    const fetchPage = vi.fn(async (page: number) => {
      const start = (page - 1) * LIBRARY_PAGE_SIZE + 1;
      const n = Math.min(LIBRARY_PAGE_SIZE, total - start + 1);
      return { data: { records: make(start, Math.max(n, 0)), total } };
    });
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(total);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(3);
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

  it('服务端把每页收窄到更小值时仍取尽（回归：不能把「本页未满」当末页）', async () => {
    // 服务端单页上限 100，这里模拟 50——正是「请求 200 却被钳到 100」的真实形态
    const serverPageSize = 50;
    const total = 205;
    const fetchPage = vi.fn(async (page: number) => {
      const start = (page - 1) * serverPageSize + 1;
      const n = Math.min(serverPageSize, total - start + 1);
      return { data: { records: make(start, Math.max(n, 0)), total } };
    });
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(total);
    expect(r.rows[total - 1]).toEqual({ id: total });
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(Math.ceil(total / serverPageSize));
  });

  it('total 缺失（null）时不因 Number(null)=0 在首页误判取尽', async () => {
    const fetchPage = vi.fn(async (page: number) =>
      page === 1
        ? { data: { records: make(1, LIBRARY_PAGE_SIZE), total: null as unknown as number } }
        : { data: { records: make(101, 3), total: null as unknown as number } },
    );
    const r = await fetchAllPages(fetchPage);
    expect(r.rows).toHaveLength(LIBRARY_PAGE_SIZE + 3);
    expect(r.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });
});
