import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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
