// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * a11y 可访问名称接线回归钉子。
 *
 * <p>真实浏览器审计发现 5 个主列表的 `<table>` 全部没有 accessible name，
 * 屏幕阅读器只能读到「表格」而无法知道是哪一类数据；另有两个检索框只有 placeholder
 * （WCAG 不把 placeholder 当作可访问名称）。这类缺口 TypeScript 检查不出来，
 * 也不影响视觉与接口联调，只能在 DOM 审计里暴露，故用源码钉子固化为约束。
 *
 * <p>Modal 焦点陷阱不在此处断言：antd rc-dialog 默认 focusLock=true，已由真实浏览器
 * 键盘 POC 验证（Tab 12 次未逃逸、Escape 后焦点回到触发按钮），不靠源码字符串判断。
 */
const readPage = (name: string): string =>
  readFileSync(fileURLToPath(new URL(`./${name}`, import.meta.url)), 'utf-8');

describe('a11y · 表格 accessible name', () => {
  it('五个主列表表格均有显式可访问名称', () => {
    const cases: [string, string][] = [
      ['ReviewWorkbenchPage.tsx', '核赔待办列表'],
      ['PolicyManagementPage.tsx', '保单列表'],
      ['QuotaConfigPage.tsx', '额度账户列表'],
      ['MappingPage.tsx', '码值映射列表'],
      ['LibraryPage.tsx', '基础目录列表'],
    ];
    for (const [file, label] of cases) {
      expect(readPage(file), `${file} 表格缺少 aria-label="${label}"`).toContain(
        `aria-label="${label}"`,
      );
    }
  });

  it('保单详情弹窗内的四个子表格也都有名称', () => {
    const src = readPage('PolicyManagementPage.tsx');
    for (const label of [
      '承保主体映射健康度',
      '计划责任列表',
      '被保人名单',
      '额度账户明细',
    ]) {
      expect(src, `保单详情子表格缺少 ${label}`).toContain(`aria-label="${label}"`);
    }
  });

  it('额度公账汇总与映射页其余 Tab 表格不遗漏', () => {
    expect(readPage('QuotaConfigPage.tsx')).toContain('aria-label="公账责任额度池汇总"');
    const mapping = readPage('MappingPage.tsx');
    for (const label of ['未匹配码值列表', '别名列表', '拆分规则列表', '字段绑定列表']) {
      expect(mapping).toContain(`aria-label="${label}"`);
    }
  });
});

describe('a11y · 表单控件可访问名称', () => {
  it('统一查询条用 placeholder 作为显式 aria-label（一处覆盖所有接入页）', () => {
    const bar = readFileSync(
      fileURLToPath(new URL('../components/ClaimQueryBar.tsx', import.meta.url)),
      'utf-8',
    );
    expect(bar).toContain('aria-label={placeholder}');
  });

  it('映射页独立检索框与「含历史行」开关均有名称', () => {
    const src = readPage('MappingPage.tsx');
    expect(src).toContain('aria-label="保司码值 / 标准码"');
    expect(src).toContain('aria-label="是否包含历史码值"');
  });
});
