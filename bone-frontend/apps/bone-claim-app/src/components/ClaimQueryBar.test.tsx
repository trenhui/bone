// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, fireEvent } from '@testing-library/react';
import { ClaimQueryBar } from './ClaimQueryBar';
import type { SavedView } from '../hooks/useSavedViews';

const pinned: SavedView = {
  id: 'v1', name: '我的待办', queryString: 'f_status=43', pinned: true, createdAt: 1,
};
const normal: SavedView = {
  id: 'v2', name: '高风险', queryString: 'f_risk=HIGH', pinned: false, createdAt: 2,
};

afterEach(() => cleanup());

describe('ClaimQueryBar · 统一查询条语义', () => {
  it('回车与点搜索都提交 trim 后的关键字', () => {
    const onKeyword = vi.fn();
    render(<ClaimQueryBar keyword="" onKeyword={onKeyword} placeholder="编码/名称" />);
    const input = screen.getByPlaceholderText('编码/名称');
    fireEvent.change(input, { target: { value: '  ABC  ' } });
    fireEvent.click(screen.getByRole('button', { name: /search/i }));
    expect(onKeyword).toHaveBeenCalledWith('ABC');
  });

  it('置顶视图渲染为可直接点开的按钮，点击应用视图', () => {
    const onApplyView = vi.fn();
    render(
      <ClaimQueryBar keyword="" onKeyword={vi.fn()} views={[pinned, normal]}
        onApplyView={onApplyView} />,
    );
    fireEvent.click(screen.getByRole('button', { name: /我的待办/ }));
    expect(onApplyView).toHaveBeenCalledWith(pinned);
    expect(screen.queryByRole('button', { name: /高风险/ })).toBeNull();
  });

  it('无视图时不渲染管理入口，有视图时渲染；页面自定义 actions 原样呈现', () => {
    const { rerender } = render(
      <ClaimQueryBar keyword="" onKeyword={vi.fn()} onRemoveView={vi.fn()} actions={<span>ACTION</span>} />,
    );
    expect(screen.queryByText('管理视图')).toBeNull();
    expect(screen.getByText('ACTION')).toBeTruthy();
    rerender(
      <ClaimQueryBar keyword="" onKeyword={vi.fn()} views={[normal]}
        onRemoveView={vi.fn()} />,
    );
    expect(screen.getByText('管理视图')).toBeTruthy();
  });

  it('searchable=false：不渲染检索框，仍保留保存视图与页面自定义动作', () => {
    render(
      <ClaimQueryBar keyword="" onKeyword={vi.fn()} searchable={false}
        placeholder="不该出现的搜索框" views={[pinned]} onRemoveView={vi.fn()}
        actions={<span>ACTION</span>} />,
    );
    expect(screen.queryByPlaceholderText('不该出现的搜索框')).toBeNull();
    expect(screen.getByRole('button', { name: /我的待办/ })).toBeTruthy();
    expect(screen.getByText('ACTION')).toBeTruthy();
  });

  it('外部清空 keyword 后输入框同步清空（换保单/应用视图不残留旧文本）', () => {
    const { rerender } = render(
      <ClaimQueryBar keyword="OLD" onKeyword={vi.fn()} placeholder="搜索" />,
    );
    expect((screen.getByPlaceholderText('搜索') as HTMLInputElement).value).toBe('OLD');
    rerender(<ClaimQueryBar keyword="" onKeyword={vi.fn()} placeholder="搜索" />);
    expect((screen.getByPlaceholderText('搜索') as HTMLInputElement).value).toBe('');
  });
});
