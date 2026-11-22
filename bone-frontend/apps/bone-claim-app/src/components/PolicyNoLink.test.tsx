import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { PolicyNoLink } from './PolicyNoLink';

/**
 * 保单号 → 保单详情（新标签页）回归。
 *
 * 钉住四件事：
 * ① 合法保单号渲染真实 `<a>`、target=_blank、rel 含 noopener，href 指向 `/policy?policyNo=`；
 * ② 传 text 时只改显示、不改下钻用的保单号；
 * ③ 点击只阻止行选中冒泡、不 preventDefault（否则浏览器不开新标签页）；
 * ④ 非法/空值 fail-closed —— 纯文本、无 href。
 */
describe('PolicyNoLink', () => {
  it('合法保单号渲染新标签页链接，href 指向保单详情深链', () => {
    render(<PolicyNoLink policyNo="POL-2026-0001" />);
    const link = screen.getByTestId('policy-no-link-POL-2026-0001');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toContain('/standalone#/policy?policyNo=POL-2026-0001');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('显示文本与下钻保单号解耦', () => {
    render(<PolicyNoLink policyNo="POL-2026-0001" text="保司给的别名" testId="cell-policy" />);
    const link = screen.getByTestId('cell-policy');
    expect(link.textContent).toContain('保司给的别名');
    expect(link.getAttribute('href')).toContain('policyNo=POL-2026-0001');
    expect(link.getAttribute('href')).not.toContain('保司给的别名');
  });

  it('点击只阻止行选中冒泡，不阻止浏览器打开新标签页', () => {
    const onRowClick = vi.fn();
    render(
      <div onClick={onRowClick}>
        <PolicyNoLink policyNo="POL-2026-0001" />
      </div>,
    );
    const link = screen.getByTestId('policy-no-link-POL-2026-0001');
    const notPrevented = fireEvent.click(link, { button: 0 });
    expect(onRowClick).not.toHaveBeenCalled();
    expect(notPrevented).toBe(true);
  });

  it('非法保单号（含注入字符）fail-closed：纯文本、无 href', () => {
    render(<PolicyNoLink policyNo="POL/../evil?x=1" />);
    const el = screen.getByTestId('policy-no-link-invalid');
    expect(el.tagName).toBe('SPAN');
    expect(el.textContent).toContain('POL/../evil?x=1');
    expect(el.getAttribute('href')).toBeNull();
  });

  it('空保单号渲染占位符且不生成链接', () => {
    render(<PolicyNoLink policyNo={null} />);
    const el = screen.getByTestId('policy-no-link-invalid');
    expect(el.textContent).toContain('—');
    expect(el.getAttribute('href')).toBeNull();
  });
});
