// @vitest-environment node
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * App 路由注册契约：静态锁定三条新路由已注册且指向正确页面，且既有路由保留。
 * 用源码断言而非挂载整个 App，避免 HashRouter 与测试 MemoryRouter 嵌套冲突。
 */
const appSource = readFileSync(resolve(__dirname, '../App.tsx'), 'utf-8');

describe('App 路由注册', () => {
  it.each([
    ['/sourcing', 'SourcingConsole'],
    ['/supplier-onboarding', 'SupplierOnboarding'],
    ['/supplier-risk', 'SupplierRisk'],
  ])('路由 %s 指向 %s', (path, component) => {
    const routeRe = new RegExp(
      `<Route\\s+path="${path}"\\s+element=\\{<${component}\\s*/>`,
    );
    expect(appSource).toMatch(routeRe);
  });

  it('导入三个新页面组件', () => {
    expect(appSource).toMatch(/import SourcingConsole from '\.\/pages\/SourcingConsole'/);
    expect(appSource).toMatch(/import SupplierOnboarding from '\.\/pages\/SupplierOnboarding'/);
    expect(appSource).toMatch(/import SupplierRisk from '\.\/pages\/SupplierRisk'/);
  });

  it('既有路由保留（交易/供应商/履约/合同/HITL/就绪度/治理）', () => {
    for (const path of [
      '/transactions',
      '/suppliers',
      '/fulfillment',
      '/contract-hitl',
      '/ai-readiness',
      '/governance',
    ]) {
      expect(appSource).toContain(`path="${path}"`);
    }
  });
});
