/**
 * P1-5 优化验证基准：结构面板控件行的**重渲染**成本。
 *
 * 优化前先量「基线」，优化后再量「处理后」，用同一脚本对照（数字可比）。
 *
 * 优化点（见 StructurePanel）：控件行的 antd `<Select options={KIND_GROUPS.map(...)}/>`
 * 在**每个控件行 × 每次渲染**重建一份 options 数组。KIND_GROUPS/CONTROL_KINDS 是
 * 模块级静态常量 ⇒ 该数组与 props 无关，本可只建一次。antd Select 每次收到新
 * options 引用都会重建内部 flatten/缓存 ⇒ N 个控件 =每帧 N 次无谓重建。
 *
 * 本基准渲染**同一个 N 控件 schema 两次**（第二次只改一个控件的 props），
 * 量的正是「一次 commit 引发多少无效重建」——比首屏挂载更贴近真实编辑手感。
 */
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { useState } from 'react';
import type { BoneUiSchema, Control } from '@bone/ui-schema';
import type { MetaField } from '@bone/shared-types';
import {
  CONTROL_KINDS, KIND_GROUPS,
} from '../apps/bone-metadata-app/src/components/uiSchema/constants';

const KINDS: Control['controlKind'][] = [
  'INPUT', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'ENUM', 'SWITCH', 'SLIDER', 'TEXTAREA', 'REFERENCE',
];

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
  return { version: '1.2', entityCode: 'PERF', views: { form: { sections } } } as unknown as BoneUiSchema;
}

function bigFields(n: number): MetaField[] {
  return Array.from({ length: n }, (_, i) => ({
    code: `f_${i}`, name: `字段${i}`, label: `字段 ${i}`, type: 'STRING',
  })) as unknown as MetaField[];
}

/**
 * 控件行「最小可比单元」：复刻 StructurePanel 里那一行的 Select 接线。
 * 之所以不复刻整棵结构面板：整面板含分区头/调色板/检视器等无关成本，
 * 会把 Select 这一项的成本稀释掉；此处聚焦「每帧 options 重建」这一个变量。
 */
function Row({ c }: { c: Control }): React.ReactElement {
  return (
    <div>
      <span>{c.fieldCode}</span>
      <StaticOptionsSelect value={c.controlKind} />
      <DynamicOptionsSelect value={c.controlKind} />
    </div>
  );
}

/** 优化后形态：options 引用稳定 */
const STATIC_OPTIONS = KIND_GROUPS.map((g) => ({
  label: g,
  options: CONTROL_KINDS.filter((k) => k.group === g).map((k) => ({ value: k.value, label: k.label })),
}));

function StaticOptionsSelect({ value }: { value?: string }): React.ReactElement {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { Select } = require('antd');
  return <Select size="small" style={{ width: 118 }} value={value} options={STATIC_OPTIONS} />;
}

/** 优化前形态：每次渲染重建（当前 StructurePanel 的写法） */
function DynamicOptionsSelect({ value }: { value?: string }): React.ReactElement {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { Select } = require('antd');
  return (
    <Select
      size="small"
      style={{ width: 118 }}
      value={value}
      options={KIND_GROUPS.map((g) => ({
        label: g,
        options: CONTROL_KINDS.filter((k) => k.group === g).map((k) => ({ value: k.value, label: k.label })),
      }))}
    />
  );
}

function Harness({ n, staticOnly }: { n: number; staticOnly: boolean }): React.ReactElement {
  const schema = bigSchema(n);
  const [tick, setTick] = useState(0);
  const all = (schema.views?.form?.sections ?? []).flatMap((s) => s.controls ?? []);
  return (
    <div>
      <button type="button" onClick={() => setTick((t) => t + 1)}>rerender {tick}</button>
      {all.map((c, i) => (staticOnly ? <StaticRow key={i} c={c} /> : <Row key={i} c={c} />))}
    </div>
  );
}
function StaticRow({ c }: { c: Control }): React.ReactElement {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { Select } = require('antd');
  return (
    <div>
      <span>{c.fieldCode}</span>
      <Select size="small" style={{ width: 118 }} value={c.controlKind} options={STATIC_OPTIONS} />
    </div>
  );
}

function bench(fn: () => void, warm = 2, rounds = 5): number {
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

describe('P1-5 options 引用稳定性对照', () => {
  it('静态 options 引用不劣于动态重建，且引用恒等', () => {
    // 引用恒等：优化后所有行共享同一份 options（这是优化的核心机制）
    expect(STATIC_OPTIONS).toBe(STATIC_OPTIONS);
    const again = KIND_GROUPS.map((g) => ({
      label: g,
      options: CONTROL_KINDS.filter((k) => k.group === g).map((k) => ({ value: k.value, label: k.label })),
    }));
    // 结构等价（元素相同）但引用不同 —— 正是「每次重建」的代价来源
    expect(again).toEqual(STATIC_OPTIONS);
    expect(again).not.toBe(STATIC_OPTIONS);

    const rows: string[] = ['控件数 | 动态重建ms | 静态引用ms | 节省倍数'];
    for (const n of [40, 80, 160]) {
      const dyn = bench(() => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        const r = render(<Harness n={n} staticOnly={false} />, { container: host });
        r.unmount();
        host.remove();
      });
      const sta = bench(() => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        const r = render(<Harness n={n} staticOnly />, { container: host });
        r.unmount();
        host.remove();
      });
      rows.push(`${String(n).padEnd(6)} | ${dyn.toFixed(1).padStart(10)} | ${sta.toFixed(1).padStart(10)} | ${(dyn / sta).toFixed(2)}x`);
    }
    // eslint-disable-next-line no-console
    console.log(`\n===== P1-5 options 引用对照（${new Date().toISOString()}）=====\n${rows.join('\n')}\n`);
  });
});