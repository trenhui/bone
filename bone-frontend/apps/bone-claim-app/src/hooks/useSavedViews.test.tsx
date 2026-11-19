import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useSavedViews } from './useSavedViews';

describe('useSavedViews · 保存视图本地持久化', () => {
  beforeEach(() => {
    localStorage.removeItem('bone-claim-saved-views-v1');
  });

  it('save 写入视图并从 localStorage 读回，同名同条件不重复', () => {
    const { result } = renderHook(() => useSavedViews());
    act(() => {
      result.current.save('我的待办', 'f_status=43');
      result.current.save('我的待办', 'f_status=43');
      result.current.save('高风险', 'f_risk=HIGH&f_status=43');
    });
    expect(result.current.views).toHaveLength(2);
    expect(result.current.views.map((v) => v.name)).toEqual(['我的待办', '高风险']);
    expect(result.current.views[0].queryString).toBe('f_status=43');
    expect(result.current.views[0].pinned).toBe(false);
  });

  it('同一名字不同条件视为两个视图（名字不是唯一键，名字+条件才是）', () => {
    const { result } = renderHook(() => useSavedViews());
    act(() => {
      result.current.save('待办', 'f_status=43');
      result.current.save('待办', 'f_status=46');
    });
    expect(result.current.views).toHaveLength(2);
  });

  it('togglePin 只置顶目标视图，remove 精确删除', () => {
    const { result } = renderHook(() => useSavedViews());
    act(() => {
      result.current.save('A', 'q=a');
      result.current.save('B', 'q=b');
    });
    const idA = result.current.views.find((v) => v.name === 'A')!.id;
    act(() => result.current.togglePin(idA));
    expect(result.current.views.find((v) => v.name === 'A')!.pinned).toBe(true);
    expect(result.current.views.find((v) => v.name === 'B')!.pinned).toBe(false);
    act(() => result.current.remove(idA));
    expect(result.current.views.map((v) => v.name)).toEqual(['B']);
  });

  it('坏数据整条丢弃，不让畸形对象流进 UI', () => {
    localStorage.setItem(
      'bone-claim-saved-views-v1',
      JSON.stringify([{ id: 'x', name: '坏数据' }, { id: 'ok', name: '好数据', queryString: 'q=1', pinned: false, createdAt: 1 }]),
    );
    const { result } = renderHook(() => useSavedViews());
    expect(result.current.views).toHaveLength(1);
    expect(result.current.views[0].name).toBe('好数据');
  });

  it('存储写入抛异常时降级为内存态，不崩溃', () => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = () => {
      throw new Error('quota exceeded');
    };
    try {
      const { result } = renderHook(() => useSavedViews());
      expect(() => act(() => result.current.save('A', 'q=1'))).not.toThrow();
      expect(result.current.views).toHaveLength(1);
    } finally {
      Storage.prototype.setItem = original;
    }
  });
});
