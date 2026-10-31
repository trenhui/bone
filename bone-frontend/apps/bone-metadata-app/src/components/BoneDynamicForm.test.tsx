import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaField } from '../types';
import type { BoneUiSchema } from '@bone/ui-schema';
import { BoneDynamicForm } from './BoneDynamicForm';

/**
 * ENUM 控件「空码表」降级回归。
 *
 * 背景（2026-10-21 全模块联调实测）：理赔实体 t_claim_case 的 schema 里
 * claim_type / hospital_level 声明为 controlKind=ENUM，但字段没配 enumValues。
 * 旧实现一律渲染 antd Select，码表为空时下拉点开只有「暂无数据」，
 * 结果**必填的枚举字段永远填不上** → 整张单据建不出来，而用户完全无从下手
 * （既选不到、也输不进）。本测试锁死「空码表必须降级为可输入的文本框」这一行为。
 *
 * 判据不是「渲染了 Select」这种同义反复，而是两条性质：
 *   1. 空码表 → 渲染出可输入的文本框（placeholder 明示未配置码表）；
 *   2. 有码表 → 仍是下拉（不得误伤正常枚举）。
 */
const field = (code: string, displayName: string, type = 'STRING', enumValues?: string): MetaField =>
  ({
    id: code,
    code,
    name: displayName,
    displayName,
    type,
    ...(enumValues ? { enumValues } : {}),
  }) as unknown as MetaField;

const schema = (controlKind: 'ENUM'): BoneUiSchema =>
  ({
    schemaVersion: 1,
    entityCode: 't_claim_case',
    views: {
      form: {
        sections: [
          { id: 's1', title: '申请信息', controls: [{ fieldCode: 'claim_type', controlKind }] },
        ],
      },
    },
    linkageRules: [],
    submitRules: [],
  }) as unknown as BoneUiSchema;

describe('BoneDynamicForm · ENUM 空码表降级', () => {
  it('码表为空 → 降级为文本框（可输入，不渲染点不开的空 Select）', () => {
    render(
      <BoneDynamicForm
        schema={schema('ENUM')}
        fields={[field('claim_type', '理赔类型')]}
        entityCode="t_claim_case"
        displayMode="create"
      />,
    );
    // 输入元素必须存在且可输入：空 Select 的内部 <input> 是 role=combobox，此处必须不是
    const input = screen.getByPlaceholderText(/未配置码表/);
    expect(input).toBeTruthy();
    expect(input.getAttribute('role')).not.toBe('combobox');
    expect((input as HTMLInputElement).disabled).toBe(false);
  });

  it('码表非空 → 仍是下拉（正常枚举不被降级误伤）', () => {
    render(
      <BoneDynamicForm
        schema={schema('ENUM')}
        fields={[
          field('claim_type', '理赔类型', 'STRING', '[{"code":"OUTPATIENT","label":"门诊"}]'),
        ]}
        entityCode="t_claim_case"
        displayMode="create"
      />,
    );
    expect(screen.queryByPlaceholderText(/未配置码表/)).toBeNull();
    // antd Select 的搜索框是 role=combobox
    const combo = document.querySelector('input[role="combobox"]');
    expect(combo).toBeTruthy();
  });
});

/**
 * 提交被规则拦截时必须有**置顶**汇总。
 *
 * 背景（2026-10-31 采购订单实测）：拦截提示原先只渲染在长表单**底部**（提交按钮上方），
 * 采购订单有 15+ 字段，弹窗内容区高度超出视口 ⇒ 用户点完「提交」，提示出现在滚动区末端、
 * 连提交按钮都不在同一屏，屏幕上看起来"什么都没发生"。自动化探针也只读 toast，于是把它
 * 记成「静默失败」——真正的缺陷不在"没提示"，而在"提示用户看不到"。
 *
 * 判据（三条，缺一即红，且都不是同义反复）：
 *   1. 出现汇总标题且**带条数**（「共 N 项需修正」），不是只把错误原文再打一遍；
 *   2. 汇总在 DOM 上位于所有分区**之前**（置顶）——这是本修复的核心性质；
 *   3. 拦截成立 ⇒ 不得发起提交（onSubmit 未被调用）。
 */
describe('BoneDynamicForm · 提交拦截汇总置顶', () => {
  const blockingSchema = (): BoneUiSchema =>
    ({
      schemaVersion: 1,
      entityCode: 'purchase_order',
      views: {
        form: {
          sections: [
            { id: 's1', title: '采购信息', controls: [{ fieldCode: 'amount' }] },
            { id: 's2', title: '备注', controls: [{ fieldCode: 'remark' }] },
          ],
        },
      },
      linkageRules: [],
      submitRules: [
        {
          name: '行项目金额合计一致',
          function: 'sum',
          fields: ['amount'],
          operator: 'eq',
          compare: { valueType: 'value', value: 1000 },
          verifyType: 'ERROR',
          message: '采购行项目金额合计必须等于采购金额',
        },
      ],
    }) as unknown as BoneUiSchema;

  it('拦截时：顶部出现带条数的汇总，且不发起提交', async () => {
    const onSubmit = vi.fn();
    const { container } = render(
      <BoneDynamicForm
        schema={blockingSchema()}
        fields={[field('amount', '采购金额', 'DECIMAL'), field('remark', '备注')]}
        entityCode="purchase_order"
        displayMode="create"
        onSubmit={onSubmit}
      />,
    );

    const submitBtn = screen.getByRole('button', { name: /提\s*交/ });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/提交被拦截：共 1 项需修正/)).toBeTruthy();
    });
    // 置顶性质：汇总必须是容器第一个子元素（排在「采购信息」分区之前）
    expect(container.firstElementChild?.textContent ?? '').toMatch(/提交被拦截：共 1 项需修正/);
    // 拦截 ⇒ 不发起提交
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('规则通过时：不出现拦截汇总（不误伤正常提交）', async () => {
    const onSubmit = vi.fn();
    render(
      <BoneDynamicForm
        schema={blockingSchema()}
        fields={[field('amount', '采购金额', 'DECIMAL'), field('remark', '备注')]}
        entityCode="purchase_order"
        displayMode="create"
        onSubmit={onSubmit}
      />,
    );
    // 把金额填成规则要求的 1000 ⇒ sum eq 1000 通过
    const input = document.querySelector('input');
    expect(input).toBeTruthy();
    fireEvent.change(input as HTMLInputElement, { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: /提\s*交/ }));

    await waitFor(() => {
      expect(screen.queryByText(/提交被拦截/)).toBeNull();
    });
    expect(onSubmit).toHaveBeenCalled();
  });
});
