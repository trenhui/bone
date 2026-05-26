import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const apiSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), 'api.ts'),
  'utf8',
);

/** 校验 IAM 前端 API 与后端 AccountController（/api/v1/iam/accounts）对齐 */
describe('iam api account paths', () => {
  it('uses v1 base and /accounts paths', () => {
    expect(apiSource).toContain('\'/api/v1/iam\'');
    expect(apiSource).toContain('\'/accounts\'');
  });

  it('declares audit log endpoints under /audit', () => {
    expect(apiSource).toContain('\'/audit/logs\'');
    expect(apiSource).toContain('\'/audit/settings\'');
  });

  it('uses size query param for pagination', () => {
    expect(apiSource).toContain('size: pageSize');
  });
});
