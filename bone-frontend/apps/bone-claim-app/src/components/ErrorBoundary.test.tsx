// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { ErrorBoundary, type ErrorReport } from './ErrorBoundary';

/** 故意在渲染期抛错的组件，用于触发 ErrorBoundary。 */
function Boom(): never {
  throw new Error('测试中故意抛错');
}

afterEach(() => cleanup());

describe('ErrorBoundary 可注入错误上报', () => {
  it('捕获渲染异常后展示兜底 UI，并调用注入的 reporter', () => {
    const reports: ErrorReport[] = [];
    // React 会把 componentDidCatch 的 console.error 打出来，这里静音避免污染测试输出
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ErrorBoundary reporter={(r) => reports.push(r)}>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText('页面渲染出错')).toBeDefined();
    expect(reports).toHaveLength(1);
    expect(reports[0].message).toBe('测试中故意抛错');
    expect(reports[0].at).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    spy.mockRestore();
  });

  it('reporter 自身抛错时兜底 UI 仍展示（监控故障不引发二次白屏）', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ErrorBoundary reporter={() => { throw new Error('上报服务不可用'); }}>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText('页面渲染出错')).toBeDefined();
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it('未注入 reporter 时不报错（保持既有降级行为）', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(<ErrorBoundary><Boom /></ErrorBoundary>);
    expect(screen.getByText('页面渲染出错')).toBeDefined();
    spy.mockRestore();
  });
});
