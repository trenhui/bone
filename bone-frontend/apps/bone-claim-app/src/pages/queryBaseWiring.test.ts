// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * P0-3「打通查询基座」接线回归钉子。
 *
 * <p>查询条件若只存页面 useState，浏览器前进/后退丢失、分享深链不带条件、通知回到列表无法还原上下文。
 * 但接线正确与否 TypeScript 检查不出来——页面照样编译、照样能跑，只是刷新后筛选悄悄没了。
 * 这里直接读源码钉住：三个主列表必须走 useClaimQuery（URL 真源）+ useSavedViews（保存视图），
 * 且不得再保留同语义的本地 state（那会是第二个真源）。
 *
 * <p>同时钉住「服务端真分页」没被回退成前端切片：分页 onChange 必须把页码/条数写回 URL，
 * 且只发一次更新（连续两次 functional setSearchParams 会基于同一渲染快照，后一次覆盖前一次）。
 */
const read = (name: string): string =>
  readFileSync(fileURLToPath(new URL(`./${name}`, import.meta.url)), 'utf-8');

const review = read('ReviewWorkbenchPage.tsx');
const preExam = read('PreExamWorkbenchPage.tsx');
const quota = read('QuotaConfigPage.tsx');

describe('P0-3 查询基座接线（URL 真源）', () => {
  it('核赔 / 初审 / 额度三页都接入统一查询基座与保存视图', () => {
    for (const [name, src] of [
      ['核赔', review],
      ['初审', preExam],
      ['额度', quota],
    ] as const) {
      expect(src, `${name}页未接 useClaimQuery`).toMatch(/useClaimQuery\(/);
      expect(src, `${name}页未接 useSavedViews`).toMatch(/useSavedViews\(/);
      expect(src, `${name}页未渲染统一查询条`).toMatch(/<ClaimQueryBar/);
    }
  });

  it('核赔页：任务维筛选进 URL，后端无 keyword 故显式关闭搜索框', () => {
    expect(review).toMatch(/setFilter\('taskStatus'/);
    expect(review).toMatch(/searchable=\{false\}/);
    expect(review).not.toMatch(/setTaskFilter|setPageSize/);
    expect(review).not.toMatch(/<Input\.Search/);
  });

  it('初审页：状态筛选进 URL，后端无 keyword 故显式关闭搜索框', () => {
    expect(preExam).toMatch(/setFilter\('status'/);
    expect(preExam).toMatch(/searchable=\{false\}/);
    expect(preExam).not.toMatch(/setStatusFilter/);
    expect(preExam).not.toMatch(/<Input\.Search/);
  });

  it('额度页：真实关键字进 URL，减人状态用 f_removed 结构化筛选', () => {
    expect(quota).toMatch(/onKeyword=\{setKeyword\}/);
    expect(quota).toMatch(/setFilter\('removed'/);
    expect(quota).toMatch(/f_removed/);
    expect(quota).not.toMatch(/keywordDraft|setRemovedFilter|setPageSize/);
    // keyword 是后端真实支持的参数（listQuotaAccounts.keyword），不是摆设。
    expect(quota).toMatch(/keyword: keyword \|\| undefined/);
  });

  it('三页分页均写回 URL，且同一次 onChange 不连发两次参数更新', () => {
    for (const [name, src] of [
      ['核赔', review],
      ['初审', preExam],
      ['额度', quota],
    ] as const) {
      expect(src, `${name}页分页未用查询基座的 setPage`).toMatch(/setPage\(/);
      expect(src, `${name}页分页未用查询基座的 setSize`).toMatch(/setSize\(/);
      expect(src, `${name}页存在 setPage→setSize 连发`).not.toMatch(
        /setPage\([^;]*\);\s*setSize\(/,
      );
      expect(src, `${name}页存在 setSize→setPage 连发`).not.toMatch(
        /setSize\([^;]*\);\s*setPage\(/,
      );
    }
  });
});
