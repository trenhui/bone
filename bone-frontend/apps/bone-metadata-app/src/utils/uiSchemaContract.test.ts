import { describe, it, expect } from 'vitest';
import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { validateUiSchema, normalizeUiSchema, type BoneUiSchema } from '@bone/ui-schema';

/**
 * 契约机检（consumer-driven contract test）：扫描 apps/ 下所有 `*.ui-schema.json`，
 * 用共享引擎的 `validateUiSchema` + `normalizeUiSchema` 校验 —— schema as code 的 CI 门禁等价物。
 * 任何违反协议的 schema（非法分区引用 / 控件缺标识 / 动作码重复 / 缺失必备字段）在此 fail-fast，
 * 而非等到运行时渲染成空白页（我们曾因此类缺陷渲染空壳且无报错）。
 */
function findSchemaFiles(dir: string, out: string[] = []): string[] {
  let entries: string[];
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const name of entries) {
    if (name === 'node_modules' || name === '.git') continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) findSchemaFiles(full, out);
    else if (name.endsWith('.ui-schema.json')) out.push(full);
  }
  return out;
}

const root = process.cwd(); // bone-frontend
const files = findSchemaFiles(join(root, 'apps'));

describe('UI Schema 契约机检（ingest 边界）', () => {
  it('至少发现一个 schema 文件', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  for (const file of files) {
    it(`${file} 通过契约校验且归一化无非法引用`, () => {
      const schema = JSON.parse(readFileSync(file, 'utf-8')) as BoneUiSchema;
      const v = validateUiSchema(schema);
      expect(v.valid, `校验错误: ${v.errors.join('; ')}`).toBe(true);

      const n = normalizeUiSchema(schema);
      expect(n.errors, `归一化错误: ${n.errors.join('; ')}`).toHaveLength(0);

      // 归一化后分区数不丢失（code 引用已解析为内联对象，或内联对象原样保留）
      const rawCount =
        (schema.views.form?.sections?.length ?? 0) + (schema.views.detail?.sections?.length ?? 0);
      const normCount =
        (n.schema.views.form?.sections?.length ?? 0) + (n.schema.views.detail?.sections?.length ?? 0);
      expect(normCount).toBe(rawCount);
    });
  }
});
