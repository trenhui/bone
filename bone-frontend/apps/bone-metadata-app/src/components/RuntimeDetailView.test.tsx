import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { MetaField } from '../types';
import type { FormView } from '../utils/uiSchemaEngine';
import { RuntimeDetailView, detailValueOf } from './RuntimeDetailView';

const field = (code: string, displayName?: string, type = 'STRING', enumValues?: string): MetaField =>
  ({
    id: code,
    code,
    name: displayName ?? code,
    displayName: displayName ?? code,
    type,
    ...(enumValues ? { enumValues } : {}),
  }) as unknown as MetaField;

const detail: FormView = {
  pageHeader: ['policy_no', 'status'],
  sections: [
    {
      id: 'd_basic',
      title: '基本信息',
      controls: [
        { fieldCode: 'policy_no' },
        { fieldCode: 'claim_type' },
        { fieldCode: 'claim_amount' },
      ],
    },
    { id: 'd_empty', title: '空分区', controls: [] },
    {
      id: 'd_calc',
      title: '理算结果',
      kind: 'form',
      controls: [{ fieldCode: 'payable_amount' }, { fieldCode: 'status' }],
    },
  ],
};

const fields = [
  field('policy_no', '保单号'),
  field('claim_type', '理赔类型', 'ENUM', '[{"code":"medical","label":"医疗"},{"code":"accident","label":"意外"}]'),
  field('claim_amount', '申报金额', 'DECIMAL'),
  field('payable_amount', '应赔金额', 'DECIMAL'),
  field('status', '状态', 'ENUM', '[{"code":"DRAFT","label":"草稿"},{"code":"APPROVED","label":"已通过"}]'),
  field('invoice_no', '发票号'),
];

const record = {
  policy_no: 'P-2026-0003',
  claim_type: 'medical',
  claim_amount: 128.5,
  payable_amount: null,
  status: 'DRAFT',
  invoice_no: 'INV-1', // 不在 detail 配置里 → 不应渲染
};

describe('detailValueOf（详情值回显）', () => {
  it('ENUM → label（与列表/表单同口径）', () => {
    expect(detailValueOf(field('status', '状态', 'ENUM', '[{"code":"DRAFT","label":"草稿"}]'), 'DRAFT')).toBe('草稿');
  });
  it('ENUM 未命中码值 → 回落裸值', () => {
    expect(detailValueOf(field('status', '状态', 'ENUM', '[{"code":"DRAFT","label":"草稿"}]'), 'WAT')).toBe('WAT');
  });
  it('空值 → —', () => {
    expect(detailValueOf(field('x'), null)).toBe('—');
    expect(detailValueOf(field('x'), '')).toBe('—');
  });
  it('对象 → JSON（不显示 [object Object]）', () => {
    expect(detailValueOf(field('x'), { a: 1 })).toBe('{"a":1}');
  });
});

describe('RuntimeDetailView（协议 views.detail 的运行时投影）', () => {
  it('页头条：label: value 横排渲染', () => {
    render(<RuntimeDetailView detail={detail} fields={fields} record={record} />);
    expect(screen.getByText('保单号：')).toBeTruthy();
    // 协议 §13.2：页头字段必须同时存在于分区控件（否则 lint 悬空 ERROR）⇒ 两处出现属预期
    expect(screen.getAllByText('P-2026-0003').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('草稿').length).toBeGreaterThanOrEqual(1); // status ENUM → label（页头条+分区两处）
  });

  it('分区投影：标题 + 控件逐项渲染，空分区跳过', () => {
    render(<RuntimeDetailView detail={detail} fields={fields} record={record} />);
    expect(screen.getByText('基本信息')).toBeTruthy();
    expect(screen.getByText('理算结果')).toBeTruthy();
    expect(screen.queryByText('空分区')).toBeNull();
    expect(screen.getByText('理赔类型')).toBeTruthy();
    expect(screen.getByText('医疗')).toBeTruthy(); // claim_type ENUM → label
  });

  it('只投影配置控件：未配置字段不出现（区别于全字段平铺）', () => {
    render(<RuntimeDetailView detail={detail} fields={fields} record={record} />);
    expect(screen.queryByText('发票号')).toBeNull();
  });

  it('空配置 → Empty 回落提示（调用方据此回落平铺）', () => {
    render(<RuntimeDetailView detail={{ sections: [] }} fields={fields} record={record} />);
    expect(screen.getByText('该实体配置了详情视图但为空')).toBeTruthy();
  });
});
