/**
 * P1-5 大 schema 性能基线（纯函数侧）。
 *
 * 为什么不直接在浏览器里掐表：浏览器噪声大、不可复现。这里对设计器每次
 * 编辑必然同步执行的三条**同步热路径**做隔离基准，得到可复现的 ms 数字：
 *   1. lintSchema        —— UiSchemaDesigner 的 issues useMemo，deps=[schema] ⇒ 每次 commit 全量重跑
 *   2. firstDiffPath     —— commit() 内必调，撤销合并的 coalesce 键
 *   3. JSON 深序列化     —— doSnapshot / 首次 baseline 走这条路
 *
 * 输出规模曲线（10/50/100/200/400 控件）⇒ 若呈超线性增长即为算法问题，
 * 若近似线性则瓶颈在渲染层（另由活体探针量）。
 */
import { describe, it } from 'vitest';
import type { MetaField } from '@bone/shared-types';
import { lintSchema } from '../apps/bone-metadata-app/src/components/uiSchema/lint';
import type { BoneUiSchema } from '../apps/bone-metadata-app/src/utils/uiSchemaEngine';

/** 复刻 UiSchemaDesigner 的 firstDiffPath（同一实现，避免度量到不同代码） */
function firstDiffPath(a: unknown, b: unknown, path = '', depth = 0): string | null {
  if (a === b) return null;
  if (depth > 8 || a == null || b == null || typeof a !== 'object' || typeof b !== 'object') return path || '(root)';
  const aArr = Array.isArray(a);
  if (aArr !== Array.isArray(b)) return path || '(root)';
  const keys = new Set([...Object.keys(a as object), ...Object.keys(b as object)]);
  for (const k of keys) {
    const p = firstDiffPath((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k], path ? `${path}.${k}` : k, depth + 1);
    if (p) return p;
  }
  return null;
}

const KINDS = ['INPUT', 'TEXTAREA', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'ENUM', 'SWITCH', 'SLIDER', 'REFERENCE'] as const;

/** 造一个 n 控件的表单 schema（形态贴近真实：多分区 + 每控件带 props） */
function bigSchema(n: number): BoneUiSchema {
  const perSection = 10;
  const sections = Array.from({ length: Math.ceil(n / perSection) }, (_, s) => ({
    id: `sec_${s + 1}`,
    title: `分区 ${s + 1}`,
    controls: Array.from({ length: Math.min(perSection, n - s * perSection) }, (_, j) => {
      const i = s * perSection + j;
      const kind = KINDS[i % KINDS.length];
      return {
        fieldCode: `f_${i}`,
        controlKind: kind,
        labelOverride: `字段 ${i}`,
        props: kind === 'INPUT' || kind === 'TEXTAREA'
          ? { maxLength: 50, pattern: '^[A-Z0-9]+$' }
          : kind === 'NUMBER' || kind === 'MONEY' || kind === 'SLIDER'
            ? { min: 0, max: 1000 }
            : {},
      };
    }),
  }));
  return {
    version: '1.2',
    entityCode: 'PERF_BENCH',
    views: { form: { sections }, detail: { sections: sections.slice(0, 2) } },
  } as unknown as BoneUiSchema;
}

/** 造配套的 MetaField 列表（lint 的 known/enum 源） */
function bigFields(n: number): MetaField[] {
  return Array.from({ length: n }, (_, i) => ({
    code: `f_${i}`,
    name: `字段${i}`,
    label: `字段 ${i}`,
    type: i % 3 === 0 ? 'STRING' : i % 3 === 1 ? 'DECIMAL' : 'DATE',
  })) as unknown as MetaField[];
}

/** 取中位数（避开 GC 抖动与首轮 JIT 预热污染） */
function bench(label: string, fn: () => void, rounds = 15): number {
  for (let i = 0; i < 3; i++) fn(); // 预热
  const xs: number[] = [];
  for (let i = 0; i < rounds; i++) {
    const t0 = performance.now();
    fn();
    xs.push(performance.now() - t0);
  }
  xs.sort((a, b) => a - b);
  return xs[Math.floor(rounds / 2)];
}

describe('P1-5 大 schema 性能基线', () => {
  it('规模曲线', () => {
    const sizes = [10, 50, 100, 200, 400];
    const rows: string[] = [];
    rows.push('控件数 | lintSchema(ms) | firstDiffPath(ms) | JSON序列化(ms) | 控件数×单控件字节');
    for (const n of sizes) {
      const s = bigSchema(n);
      const s2 = { ...s, entityCode: 'PERF_BENCH2' } as BoneUiSchema;
      const known = new Set(bigFields(n).map((f) => f.code));
      const enums = new Map<string, string[]>();

      const tLint = bench('lint', () => { lintSchema(s, known, enums); });
      const tDiff = bench('diff', () => { firstDiffPath(s, s2); });
      const tJson = bench('json', () => { JSON.parse(JSON.stringify(s)); });
      const bytes = JSON.stringify(s).length;
      rows.push(
        `${String(n).padEnd(6)} | ${tLint.toFixed(2).padStart(13)} | ${tDiff.toFixed(2).padStart(16)} | ${tJson.toFixed(2).padStart(14)} | ${String(bytes).padStart(20)}`,
      );
    }
    // eslint-disable-next-line no-console
    console.log(`\n===== P1-5 纯函数性能基线（${new Date().toISOString()}）=====\n${rows.join('\n')}\n`);
  });
});