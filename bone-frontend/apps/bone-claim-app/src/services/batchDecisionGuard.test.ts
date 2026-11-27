// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { isBatchDecisionAllowed, MAX_BATCH_DECISION } from './reviewTask';

/**
 * 批量拒赔禁止的回归钉子。
 *
 * <p>背景：后端 `BatchDecisionItem` 无 rejection/comment 字段，批量拒赔 100% 进 failed 列表；
 * 且一批案件不能共用一套五要素（合规）。故批量只允许 APPROVED，拒赔必须逐案。
 */
describe('isBatchDecisionAllowed（批量决策白名单）', () => {
  it('仅 APPROVED 可批量，REJECTED / REQUEST_MORE_EVIDENCE 均禁止', () => {
    expect(isBatchDecisionAllowed('APPROVED')).toBe(true);
    expect(isBatchDecisionAllowed('REJECTED')).toBe(false);
    expect(isBatchDecisionAllowed('REQUEST_MORE_EVIDENCE')).toBe(false);
  });

  it('批量上限仍为 20（与后端契约一致）', () => {
    expect(MAX_BATCH_DECISION).toBe(20);
  });

  it('核赔工作台的 runBatch 与批量按钮都不能再走 REJECTED 批量路径', () => {
    const source = readFileSync(
      fileURLToPath(new URL('../pages/ReviewWorkbenchPage.tsx', import.meta.url)),
      'utf-8',
    );
    // 纵深防御：runBatch 与 retryFailed 都调用白名单守卫
    const guardCalls = [...source.matchAll(/isBatchDecisionAllowed\(decision\)/g)];
    expect(guardCalls.length).toBeGreaterThanOrEqual(2);
    // 不得再有 onConfirm={() => void runBatch('REJECTED')} 的入口
    expect(source).not.toMatch(/onConfirm=\{\(\) => void runBatch\('REJECTED'\)\}/);
    // 批量拒赔按钮处于禁用态
    expect(source).toMatch(/<Button danger disabled>\s*批量拒赔（需逐案填写五要素）\s*<\/Button>/);
  });
});
