// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * 编辑保单「承保主体」写回的回归钉子。
 *
 * <p>两个表单实例（新建 form / 编辑 editForm）都是合法的 FormInstance，TypeScript 无法发现
 * 「用错实例」——运行时表现为选了承保主体但隐藏的 insurerName 静默不更新，保存后主体名丢失。
 * 这里直接读源码钉住：编辑弹窗的 onChange 必须绑定 editForm，新建弹窗必须绑定 form，
 * 且两处不得退化成同一个实例。任何一处被改回 form.setFieldsValue 都会判红。
 */
const source = readFileSync(fileURLToPath(new URL('./PolicyManagementPage.tsx', import.meta.url)), 'utf-8');

describe('PolicyManagementPage 承保主体写回实例', () => {
  it('新建弹窗绑定 form，编辑弹窗绑定 editForm（禁止共用实例）', () => {
    const handlers = [...source.matchAll(/onChange=\{\(_code, name\) => (\w+)\.setFieldsValue\(\{ insurerName: name \}\)\}/g)]
      .map((m) => m[1]);
    expect(handlers).toHaveLength(2);
    expect(handlers).toContain('form');
    expect(handlers).toContain('editForm');
    expect(new Set(handlers).size).toBe(2);
  });

  it('编辑弹窗的 Form 绑定 editForm（实例来源未被改写）', () => {
    expect(source).toMatch(/<Form\s+key=\{editTarget\?\.policyNo[^}]*\}\s+form=\{editForm\}/);
  });
});
