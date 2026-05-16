import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const apiSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), 'api.ts'),
  'utf8',
);

/** 校验 IAM 前端 API 路径与设计文档 §5.2（/api/iam/users）对齐 */
describe('iam api user paths', () => {
  it('uses /users instead of legacy /accounts paths', () => {
    expect(apiSource).toContain("'/users'");
    expect(apiSource).not.toMatch(/['"]\/accounts['"]/);
  });

  it('declares audit log endpoints under /audit', () => {
    expect(apiSource).toContain("'/audit/logs'");
    expect(apiSource).toContain("'/audit/settings'");
  });
});
