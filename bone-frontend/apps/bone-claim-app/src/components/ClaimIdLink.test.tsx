import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigate };
});

import { ClaimIdLink } from './ClaimIdLink';

/**
 * 列表案件 ID 下钻到案件 360 的回归。
 *
 * <p>钉住三件事：① 合法雪花号普通点击 → 应用内 navigate 到 /case?claimId=...&tab=overview；
 * ② 传 text（报案号）时显示报案号、但下钻仍用 claimId（不能拿显示文本当 ID）；
 * ③ 非法/空 ID fail-closed —— 只渲染纯文本，不生成可注入的 href、不触发导航。
 * 这三点正是「点案件 ID 打开详情」这条链路最容易漂移的地方。
 */
describe('ClaimIdLink', () => {
  beforeEach(() => navigate.mockReset());

  it('合法案件 ID 普通点击走应用内导航到案件 360 详情', () => {
    render(<ClaimIdLink claimId="776102367333449728" />);
    const link = screen.getByTestId('claim-id-link-776102367333449728');
    fireEvent.click(link, { button: 0 });
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/case?claimId=776102367333449728&tab=overview');
  });

  it('显示报案号但下钻仍用 claimId，且支持指定 tab', () => {
    render(<ClaimIdLink claimId="776102367333449728" text="BAOAN-2026-001" tab="material" testId="cell-case" />);
    const link = screen.getByTestId('cell-case');
    expect(link.textContent).toContain('BAOAN-2026-001');
    fireEvent.click(link, { button: 0 });
    expect(navigate).toHaveBeenCalledWith('/case?claimId=776102367333449728&tab=material');
  });

  it('Ctrl+点击交给浏览器新标签页，不做应用内导航', () => {
    render(<ClaimIdLink claimId="776102367333449728" />);
    fireEvent.click(screen.getByTestId('claim-id-link-776102367333449728'), { button: 0, ctrlKey: true });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('非法 ID（含注入字符）fail-closed：纯文本、不可点、无导航', () => {
    render(<ClaimIdLink claimId="abc/../evil?x=1" />);
    const el = screen.getByTestId('claim-id-link-invalid');
    expect(el.tagName).toBe('SPAN');
    expect(el.textContent).toContain('abc/../evil?x=1');
    expect(el.getAttribute('href')).toBeNull();
    fireEvent.click(el);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('空 ID 渲染占位符且不生成链接', () => {
    render(<ClaimIdLink claimId={null} />);
    const el = screen.getByTestId('claim-id-link-invalid');
    expect(el.textContent).toContain('—');
    expect(navigate).not.toHaveBeenCalled();
  });
});
