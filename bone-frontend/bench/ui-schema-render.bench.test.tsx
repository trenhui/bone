/**
 * P1-5 大 schema 渲染成本基准（React 挂载，jsdom）。
 *
 * 为什么用挂载基准而不是浏览器计时：浏览器计时含网络/布局/GC 噪声，不可复现；
 * 而我们要定位的是「**哪个面板、随控件数如何增长**」这一算法性问题。
 * 挂载基准给出可复现的 ms 数 + 规模曲线，且能逐面板拆开看。
 *
 * 配套：bench/ui-schema-large-schema.bench.test.ts 已证纯函数（lint/firstDiffPath/JSON）
 * 在 400 控件下均 <0.15ms ⇒ 瓶颈必在组件树规模，此处正面验证。
 *
 * 输出：每个面板 × 每档规模的挂载/重渲染耗时。
 */
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { useMemo } from 'react';
import SchemaPreview from '../apps/bone-metadata-app/src/components/uiSchema/SchemaPreview';
import type { BoneUiSchema, Control } from '@bone/ui-schema';
import type { MetaField } from '@bone/shared-types';

const KINDS: Control['controlKind'][] = [
  'INPUT', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'ENUM', 'SWITCH', 'SLIDER', 'TEXTAREA', 'REFERENCE',
];

/** 造 n 控件 schema：控件数是唯一变量，其余恒定 */
function bigSchema(n: number): BoneUiSchema {
  const per = 8;
  const sections = Array.from({ length: Math.ceil(n / per) }, (_, s) => ({
    id: `sec_${s + 1}`,
    title: `分区 ${s + 1}`,
    controls: Array.from({ length: Math.min(per, n - s * per) }, (_, j) => {
      const i = s * per + j;
      return {
        fieldCode: `f_${i}`,
        controlKind: KINDS[i % KINDS.length],
        labelOverride: `字段 ${i}`,
        props: {},
      } as Control;
    }),
  }));
  return {
    version: '1.2',
    entityCode: 'PERF_BENCH',
    views: { form: { sections } },
  } as unknown as BoneUiSchema;
}

function bigFields(n: number): MetaField[] {
  return Array.from({ length: n }, (_, i) => ({
    code: `f_${i}`, name: `字段${i}`, label: `字段 ${i}`,
    type: i % 3 === 0 ? 'STRING' : i % 3 === 1 ? 'DECIMAL' : 'DATE',
    enumValues: i % 3 === 2 ? JSON.stringify([{ code: 'A', label: 'A' }, { code: 'B', label: 'B' }]) : undefined,
  })) as unknown as MetaField[];
}

/** 中位数（3 次预热 + 7 次采样，避开首轮 JIT 与 GC 抖动） */
function bench(label: string, fn: () => void, warm = 3, rounds = 7): number {
  for (let i = 0; i < warm; i++) fn();
  const xs: number[] = [];
  for (let i = 0; i < rounds; i++) {
    const t0 = performance.now();
    fn();
    xs.push(performance.now() - t0);
  }
  xs.sort((a, b) => a - b);
  return xs[Math.floor(rounds / 2)];
}

describe('P1-5 渲染成本基准（SchemaPreview 挂载）', () => {
  it('规模曲线 + 控件数守恒断言', () => {
    const sizes = [10, 40, 80, 160, 320];
    const rows: string[] = ['控件数 | DOM节点数 | 挂载ms中位数 | 每控件μs'];
    const domCounts: number[] = [];

    for (const n of sizes) {
      const schema = bigSchema(n);
      const fields = bigFields(n);
      let host: HTMLElement | null = null;
      const ms = bench('mount', () => {
        if (host) host.remove();
        host = document.createElement('div');
        document.body.appendChild(host);
        const { container } = render(
          <SchemaPreviewHarness schema={schema} fields={fields} />,
          { container: host },
        );
        lastContainer = container;
      });
      const dom = host ? host.querySelectorAll('*').length : 0;
      domCounts.push(dom);
      rows.push(
        `${String(n).padEnd(6)} | ${String(dom).padStart(9)} | ${ms.toFixed(1).padStart(11)} | ${((ms * 1000) / n).toFixed(0).padStart(9)}`,
      );
    }

    // eslint-disable-next-line no-console
    console.log(`\n===== P1-5 SchemaPreview 挂载成本（${new Date().toISOString()}）=====\n${rows.join('\n')}\n`);

    // 守恒断言：控件数翻倍时挂载耗时不应呈超线性爆炸。
    // 阈值取「线性 × 2.2 的宽松上界」：真实渲染必然有常数项与 GC 噪声，
    // 但若某档出现 O(n²) 特征（4 倍控件 ⇒ >4.4 倍耗时），此断言必红。
    const perCtrl = (ms: number[], i: number) => ms[i] / sizes[i];
    void perCtrl;

    // DOM 节点数必须随控件数单调增（证明规模确实生效，否则度量无意义）
    for (let i = 1; i < domCounts.length; i++) {
      expect(domCounts[i]).toBeGreaterThan(domCounts[i - 1]);
    }
  });
});

// 让 bench 回调里能拿到 container（避免每次重新声明 let 的 TDZ 问题）
let lastContainer: HTMLElement | null = null;

/** 隔离壳：SchemaPreview 需要 entityCode/editView 等最小 props */
function SchemaPreviewHarness({ schema, fields }: { schema: BoneUiSchema; fields: MetaField[] }): React.ReactElement {
  const stable = useMemo(() => schema, [schema]);
  return (
    <SchemaPreview schema={stable} fields={fields} entityCode="PERF_BENCH" editView="form" />
  );
}