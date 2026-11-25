// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, useSearchParams } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useClaimQuery } from './useClaimQuery';
import { useClaimIdParam } from './useClaimIdParam';

function wrapper(initial: string) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initial]}>{children}</MemoryRouter>
  );
  Wrapper.displayName = 'ClaimQueryTestWrapper';
  return Wrapper;
}

/** 同树内读取当前 URL，断言状态确实写进了地址栏（而不是只留在内存）。 */
function useCurrentSearch() {
  const [params] = useSearchParams();
  return params.toString();
}

describe('useClaimQuery · 查询状态与 URL 双向同步', () => {
  it('从 URL 读取 keyword/page/size 与结构化筛选', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?q=POLICY-1&page=3&size=50&f_status=43&f_risk=HIGH') },
    );
    expect(result.current.q.query).toEqual({
      keyword: 'POLICY-1',
      page: 3,
      size: 50,
      filters: { status: '43', risk: 'HIGH' },
    });
  });

  it('非法 page/size 归一为默认值，不产生 NaN 请求参数', () => {
    const { result } = renderHook(() => useClaimQuery(), {
      wrapper: wrapper('/quota?page=abc&size=-5'),
    });
    expect(result.current.query.page).toBe(1);
    expect(result.current.query.size).toBe(20);
  });

  it('setKeyword 写入 URL 并回到第 1 页', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?q=OLD&page=5') },
    );
    act(() => result.current.q.setKeyword('NEW'));
    expect(result.current.q.query.keyword).toBe('NEW');
    expect(result.current.q.query.page).toBe(1);
    expect(result.current.search).toContain('q=NEW');
    expect(result.current.search).not.toContain('page=');
  });

  it('setFilter 写筛选、空值删筛选，均不误删 claimId/tab 深链参数', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?claimId=123&tab=fund&page=2') },
    );
    act(() => result.current.q.setFilter('status', '43'));
    expect(result.current.search).toContain('f_status=43');
    expect(result.current.search).toContain('claimId=123');
    expect(result.current.search).toContain('tab=fund');
    expect(result.current.search).not.toContain('page=');

    act(() => result.current.q.setFilter('status', ''));
    expect(result.current.search).not.toContain('f_status');
    expect(result.current.search).toContain('claimId=123');
  });

  it('reset 只清本 hook 管理的参数，保留外部参数', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?q=X&page=2&f_risk=HIGH&claimId=9&tab=trace') },
    );
    act(() => result.current.q.reset());
    expect(result.current.q.query).toEqual({ keyword: '', page: 1, size: 20, filters: {} });
    expect(result.current.search).toContain('claimId=9');
    expect(result.current.search).toContain('tab=trace');
    expect(result.current.search).not.toContain('f_risk');
    expect(result.current.search).not.toContain('q=');
  });

  it('setPage 页码 1 不写入 URL（避免 ?page=1 噪音）', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota') },
    );
    act(() => result.current.q.setPage(1));
    expect(result.current.search).not.toContain('page=');
    act(() => result.current.q.setPage(4));
    expect(result.current.search).toContain('page=4');
  });

  it('applyQueryString 整体回灌视图，清掉旧筛选且不叠加、保留外部参数', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?q=OLD&f_status=43&claimId=5') },
    );
    act(() => result.current.q.applyQueryString('?q=NEW&page=2&f_risk=HIGH'));
    expect(result.current.q.query).toEqual({
      keyword: 'NEW', page: 2, size: 20, filters: { risk: 'HIGH' },
    });
    expect(result.current.search).not.toContain('f_status');
    expect(result.current.search).toContain('claimId=5');
  });

  it('与 useClaimIdParam 共存：两个 hook 的 URL 参数互不清除', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), id: useClaimIdParam(), search: useCurrentSearch() }),
      { wrapper: wrapper('/case?claimId=123') },
    );
    act(() => result.current.q.setFilter('status', '43'));
    expect(result.current.search).toContain('claimId=123');
    expect(result.current.search).toContain('f_status=43');
    act(() => result.current.id.submit());
    expect(result.current.search).toContain('claimId=123');
  });
});

describe('useClaimQuery · 同一事件内多次更新的合并语义', () => {
  it('连续改两个查询参数，两次更新都应落到 URL（不被同一渲染快照覆盖）', () => {
    const { result } = renderHook(
      () => ({ q: useClaimQuery(), search: useCurrentSearch() }),
      { wrapper: wrapper('/quota?q=OLD&page=5&f_status=43') },
    );
    act(() => {
      result.current.q.setKeyword('NEW');
      result.current.q.setFilter('risk', 'HIGH');
    });
    expect(result.current.search).toContain('q=NEW');
    expect(result.current.search).toContain('f_risk=HIGH');
    expect(result.current.search).not.toContain('page=');
  });
});

describe('useClaimQuery · defaultSize 可覆盖（列表页既有分页口径）', () => {
  it('未传 size 参数时用调用方指定的 defaultSize，而非全局默认 20', () => {
    const { result } = renderHook(() => useClaimQuery(10), {
      wrapper: wrapper('/policy'),
    });
    expect(result.current.query.size).toBe(10);
  });

  it('URL 显式 size 优先于 defaultSize', () => {
    const { result } = renderHook(() => useClaimQuery(10), {
      wrapper: wrapper('/policy?size=50'),
    });
    expect(result.current.query.size).toBe(50);
  });
});
