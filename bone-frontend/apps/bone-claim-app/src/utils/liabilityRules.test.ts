// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  toNum,
  codesText,
  toCodeList,
  dailyPairProblem,
  limitModePairProblem,
  catalogPairProblem,
  pairValidator,
  type FieldGetter,
} from './liabilityRules';

/** 构造一个最小 FieldGetter，模拟 antd Form 的 getFieldValue。 */
const form = (values: Record<string, unknown>): FieldGetter => (name: string) => values[name];

describe('toNum', () => {
  it('数字字符串转数字', () => expect(toNum('12.5')).toBe(12.5));
  it('undefined/空串归零', () => {
    expect(toNum(undefined)).toBe(0);
    expect(toNum('')).toBe(0);
  });
  it('非有限值归零', () => expect(toNum('abc')).toBe(0));
  it('负数保留', () => expect(toNum('-3')).toBe(-3));
});

describe('codesText', () => {
  it('数组去空白并逗号串', () => expect(codesText(['a', ' b ', '', 'c'])).toBe('a,b,c'));
  it('逗号串仅做整体 trim（内部空格保留，由 toCodeList 再拆）', () =>
    expect(codesText(' a , b ')).toBe('a , b'));
  it('undefined 给空串', () => expect(codesText(undefined)).toBe(''));
});

describe('toCodeList', () => {
  it('逗号串拆数组并去空白', () => expect(toCodeList('a, b ,, c')).toEqual(['a', 'b', 'c']));
  it('空串/undefined 给空数组', () => {
    expect(toCodeList('')).toEqual([]);
    expect(toCodeList(undefined)).toEqual([]);
  });
});

describe('dailyPairProblem', () => {
  it('有额度无口径 → 报错', () =>
    expect(dailyPairProblem(form({ dailyLimit: '100', dailyLimitBasis: 'NONE' }))).toMatch(/必须选/));
  it('无额度有口径 → 报错', () =>
    expect(dailyPairProblem(form({ dailyLimit: '0', dailyLimitBasis: 'CALENDAR' }))).toMatch(
      /永远不会生效/,
    ));
  it('同配或同不配 → 通过', () => {
    expect(dailyPairProblem(form({ dailyLimit: '100', dailyLimitBasis: 'CALENDAR' }))).toBeNull();
    expect(dailyPairProblem(form({ dailyLimit: '0', dailyLimitBasis: 'NONE' }))).toBeNull();
  });
});

describe('limitModePairProblem', () => {
  it('NONE 模式无需名单', () =>
    expect(limitModePairProblem(form({ limitMode: 'NONE' }))).toBeNull());
  it('开了模式但无名单 → 报错', () =>
    expect(
      limitModePairProblem(
        form({ limitMode: 'WHITELIST', limitHospitalCodes: [], limitDiagnosisCodes: [] }),
      ),
    ).toMatch(/必须选/));
  it('给了医院名单 → 通过', () =>
    expect(
      limitModePairProblem(
        form({ limitMode: 'WHITELIST', limitHospitalCodes: ['h1'], limitDiagnosisCodes: [] }),
      ),
    ).toBeNull());
});

describe('catalogPairProblem', () => {
  it('不查目录却配了编码 → 报错', () =>
    expect(catalogPairProblem(form({ catalogPolicy: 'NONE', catalogCode: 'd1' }))).toMatch(/不该选/));
  it('必须查却没编码 → 报错', () =>
    expect(catalogPairProblem(form({ catalogPolicy: 'MANDATORY', catalogCode: '' }))).toMatch(
      /必须选/,
    ));
  it('策略与编码同配 → 通过', () => {
    expect(catalogPairProblem(form({ catalogPolicy: 'NONE', catalogCode: '' }))).toBeNull();
    expect(catalogPairProblem(form({ catalogPolicy: 'MANDATORY', catalogCode: 'd1' }))).toBeNull();
  });
});

describe('pairValidator', () => {
  it('无矛盾 → resolve', async () => {
    const rule = pairValidator(() => null);
    await expect(rule({ getFieldValue: form({}) }).validator()).resolves.toBeUndefined();
  });
  it('有矛盾 → reject 带信息', async () => {
    const rule = pairValidator(() => '冲突');
    await expect(rule({ getFieldValue: form({}) }).validator()).rejects.toThrow('冲突');
  });
});
