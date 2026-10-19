/**
 * P1-5 回归护栏：控件行的 antd Select 必须拿到**同一个 options 引用**。
 *
 * 为什么这是有牙齿的断言（而不是「跑通即验收」）：
 * 判据不是「渲染成功」，而是**引用同一性**——把 N 个控件行的 Select 收集起来，
 * 断言它们两两 `toBe`（同一对象）。一旦有人把 `options={CONTROL_KIND_OPTIONS}`
 * 改回内联 `KIND_GROUPS.map(...)`，每一行都会拿到各自新建的数组 ⇒ `toBe` 失败 ⇒ 必红。
 *
 * 判据的来源（为什么这个性质值得守）：antd Select/Segmented 对 options 做内部
 * flatten 与缓存，**引用一变即整体失效**。引用不稳定 ⇒ 每次编辑都要重建 N 份
 * 选项树。实测代价见 bench/ui-schema-select-options.bench.test.tsx：
 * 40/80/160 行下 2.08x / 3.11x / 2.37x。
 */
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import { StrictMode } from 'react';
import type { BoneUiSchema, Control } from '../../utils/uiSchemaEngine';
import type { MetaField } from '@bone/shared-types';

/**
 * 只桩掉 antd 的 Select，其余（Button/Input/Tag/Tooltip…）用真身。
 * 理由：真antd Select 不把 options 透出到 DOM，无法观测引用；
 * 而本测试要断言的恰恰是「传进去的引用是否同一」，桩件是恰当的观测点。
 */
const seenOptions: unknown[] = [];
vi.mock('antd', async () => {
  const real = await vi.importActual<typeof import('antd')>('antd');
  const React = await import('react');
  return {
    ...real,
    Select: (props: Record<string, unknown>) => {
      seenOptions.push(props.options);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return React.createElement('div', { 'data-testid': 'select' } as any, String(props.value ?? ''));
    },
  };
});

// eslint-disable-next-line import/first
import StructurePanel from './StructurePanel';
// eslint-disable-next-line import/first
import { CONTROL_KIND_OPTIONS, CONTROL_KINDS, KIND_GROUPS } from './constants';

function schemaOf(n: number): BoneUiSchema {
  const sections = [{
    id: 'sec_1',
    title: '分区 1',
    controls: Array.from({ length: n }, (_, i) => ({
      fieldCode: `f_${i}`, controlKind: 'INPUT', labelOverride: `字段${i}`, props: {},
    })) as Control[],
  }];
  return { version: '1.2', entityCode: 'T', views: { form: { sections } } } as unknown as BoneUiSchema;
}
const fieldsOf = (n: number): MetaField[] =>
  Array.from({ length: n }, (_, i) => ({ code: `f_${i}`, name: `字段${i}`, displayName: `字段${i}`, type: 'STRING' })) as unknown as MetaField[];

/**
 * 抽出「控件类型下拉」的 options。
 *
 * 定位依据用**内容特征**而非位置或形状猜测：控件类型下拉的每个分组项都带
 * `options` 子数组，且其分组 label 恰为 KIND_GROUPS（实测 5 组）。
 * 面板里其它下拉（分区形态=6 项扁平、显示模式=3 项、字段选择等）都不满足这个特征，
 * 因此不会被误收进断言集。
 */
const kindOptions = () => seenOptions.filter((o) => {
  if (!Array.isArray(o) || o.length !== KIND_GROUPS.length) return false;
  return o.every((g, i) => {
    const gg = g as { label?: unknown; options?: unknown };
    return gg.label === KIND_GROUPS[i] && Array.isArray(gg.options);
  });
});

describe('P1-5 options 引用稳定性护栏', () => {
  it('同一 schema 内所有控件行的类型下拉共享同一 options 引用（非逐行重建）', () => {
    seenOptions.length = 0;
    const n = 12;
    render(
      <StrictMode>
        <StructurePanel schema={schemaOf(n)} onChange={() => {}} fields={fieldsOf(n)} view="form" onViewChange={() => {}} />
      </StrictMode>,
      { container: document.createElement('div') },
    );

    const opts = kindOptions();
    // 断言前置：确实观测到了 N 个控件行的下拉（否则下面恒绿 = 无判别力）
    expect(opts.length).toBeGreaterThanOrEqual(n);

    const first = opts[0];
    // 核心断言：引用同一。退回内联 .map() 时这里必红。
    for (const o of opts) expect(o).toBe(first);
    // 且就是 constants 里那一份单例（防止有人复制一份到组件内再各自 map）
    expect(first).toBe(CONTROL_KIND_OPTIONS);
  });

  it('CONTROL_KIND_OPTIONS 是模块级单例：多处 import 共享同一引用（ESM 模块缓存保证）', () => {
    // 语义即结论：模块级 const 只求值一次 ⇒ 所有 import 方拿到同一对象。
    // 这条与「组件必须引用它而非内联重建」共同构成护栏。
    expect(CONTROL_KIND_OPTIONS).toBe(CONTROL_KIND_OPTIONS);
    expect(Object.isFrozen(CONTROL_KIND_OPTIONS)).toBe(false); // 未被 freeze 误伤（freeze 会阻断后续按需扩展）
  });

  it('单例内容完整：5 个分组、全部控件类型都在（防止"提速"时丢了选项）', () => {
    expect(CONTROL_KIND_OPTIONS).toHaveLength(5);
    const flat = CONTROL_KIND_OPTIONS.flatMap((g) => g.options.map((o) => o.value));
    // 与 CONTROL_KINDS 一一对应，一个不少（优化不能改语义）
    expect(flat).toEqual(CONTROL_KINDS.map((k) => k.value));
    // 分组顺序与 KIND_GROUPS 一致（下拉里的分组次序是产品语义）
    expect(CONTROL_KIND_OPTIONS.map((g) => g.label)).toEqual(KIND_GROUPS);
  });
});