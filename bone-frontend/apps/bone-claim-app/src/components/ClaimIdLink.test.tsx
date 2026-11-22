import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ClaimIdLink } from './ClaimIdLink';

/**
 * 列表案件 ID → 案件 360 详情（新标签页）回归。
 *
 * 钉住四件事：
 * ① 合法 ID 渲染真实 `<a>`、target=_blank、rel 含 noopener，href 指向案件 360 深链；
 * ② 传 text（报案号）时显示报案号、但 href 仍用 claimId（不能拿显示文本当 ID）；
 * ③ 点击只阻止行选中冒泡，**不** preventDefault（否则浏览器不会开新标签页）；
 * ④ 非法/空 ID fail-closed —— 只渲染纯文本，不生成可注入的 href。
 */
describe('ClaimIdLink', () => {
  it('合法案件 ID 渲染新标签页链接，href 指向案件 360 详情', () => {
    render(<ClaimIdLink claimId="776102367333449728" />);
    const link = screen.getByTestId('claim-id-link-776102367333449728');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toContain('#/case?claimId=776102367333449728&tab=overview');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('显示报案号但下钻仍用 claimId，且支持指定 tab', () => {
    render(<ClaimIdLink claimId="776102367333449728" text="BAOAN-2026-001" tab="material" testId="cell-case" />);
    const link = screen.getByTestId('cell-case');
    expect(link.textContent).toContain('BAOAN-2026-001');
    expect(link.getAttribute('href')).toContain('#/case?claimId=776102367333449728&tab=material');
    expect(link.getAttribute('href')).not.toContain('BAOAN-2026-001');
    expect(link.getAttribute('target')).toBe('_blank');
  });

  it('点击只阻止行选中冒泡，不阻止浏览器打开新标签页', () => {
    const onRowClick = vi.fn();
    render(
      <div onClick={onRowClick}>
        <ClaimIdLink claimId="776102367333449728" />
      </div>,
    );
    const link = screen.getByTestId('claim-id-link-776102367333449728');
    // fireEvent 返回 dispatchEvent 的结果：false 表示有人调用了 preventDefault。
    const notPrevented = fireEvent.click(link, { button: 0 });
    expect(onRowClick).not.toHaveBeenCalled();
    expect(notPrevented).toBe(true);
  });

  it('非法 ID（含注入字符）fail-closed：纯文本、不可点、无 href', () => {
    render(<ClaimIdLink claimId="abc/../evil?x=1" />);
    const el = screen.getByTestId('claim-id-link-invalid');
    expect(el.tagName).toBe('SPAN');
    expect(el.textContent).toContain('abc/../evil?x=1');
    expect(el.getAttribute('href')).toBeNull();
    expect(el.getAttribute('target')).toBeNull();
  });

  it('空 ID 渲染占位符且不生成链接', () => {
    render(<ClaimIdLink claimId={null} />);
    const el = screen.getByTestId('claim-id-link-invalid');
    expect(el.textContent).toContain('—');
    expect(el.getAttribute('href')).toBeNull();
  });
});
