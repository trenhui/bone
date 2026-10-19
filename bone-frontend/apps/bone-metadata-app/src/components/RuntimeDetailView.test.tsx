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

/**
 * N7字段级角色可见性 —— 本组件曾是**唯一绕过 fieldVisible 的渲染面**：
 * 表单按 props.roles 隐藏了字段，详情抽屉却把值原样显示给所有人（数据泄露）。
 *
 * 判据刻意用「授权/未授权双向对照」而非「渲染成功」：只断言授权角色看得到，
 * 删掉过滤逻辑后测试照样绿（无判别力）。必须同时断言**未授权角色看不到**。
 */
describe('RuntimeDetailView · N7 字段级角色可见性', () => {
  const salary = 'base_salary';
  const salaryFields = [
    field('policy_no', '保单号'),
    field(salary, '税前月薪', 'DECIMAL'),
  ];
  const salaryRecord = { policy_no: 'P-1', [salary]: 88888 };
  // payroll 分区只有受限字段：用于验证「授权角色看不到时，连分区标题都不出现」
  const payrollOnly: FormView = {
    pageHeader: [],
    sections: [
      { id: 's_basic', title: '基本信息', controls: [{ fieldCode: 'policy_no' }] },
      { id: 's_pay', title: '薪酬信息', controls: [{ fieldCode: salary, props: { roles: ['hr'] } }] },
    ],
  };

  it('未授权角色：受限字段的「标签 + 值」都不出现', () => {
    render(<RuntimeDetailView detail={payrollOnly} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['employee'] }} />);
    expect(screen.queryByText('税前月薪')).toBeNull();       // 标签不泄露
    expect(screen.queryByText('88888')).toBeNull();          // 值不泄露
    expect(screen.getByText('基本信息')).toBeTruthy();          // 非受限分区照常
  });

  it('授权角色：受限字段正常展示（证明上一条不是「整体不渲染」的假绿）', () => {
    render(<RuntimeDetailView detail={payrollOnly} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['hr'] }} />);
    expect(screen.getByText('税前月薪')).toBeTruthy();
    expect(screen.getByText('88888')).toBeTruthy();
  });

  it('ctx.roles 缺失（未登录/旧 Shell 未下发）→ fail-closed 隐藏，不得静默全量放开', () => {
    render(<RuntimeDetailView detail={payrollOnly} fields={salaryFields} record={salaryRecord} />);
    expect(screen.queryByText('88888')).toBeNull();
  });

  it('授权角色看不到该分区全部字段时，连分区标题一并隐藏（不暴露受限分区存在性）', () => {
    render(<RuntimeDetailView detail={payrollOnly} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['employee'] }} />);
    expect(screen.queryByText('薪酬信息')).toBeNull();
  });

  it('页头条同样受角色门禁：分区声明 roles 的字段被pageHeader 引用时也不泄露', () => {
    // pageHeader 与分区是两条渲染路径，最容易只改一处。此处 salary 在**分区**里声明了
    // roles:['hr']，同时被 pageHeader 引用——未授权角色的页头条也不得出现该字段。
    const withHeader: FormView = {
      pageHeader: [salary, 'policy_no'],
      sections: [{
        id: 's1',
        title: 'S',
        controls: [
          { fieldCode: 'policy_no' },
          { fieldCode: salary, props: { roles: ['hr'] } },
        ],
      }],
    };
    render(<RuntimeDetailView detail={withHeader} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['employee'] }} />);
    expect(screen.queryByText('税前月薪：')).toBeNull();
    expect(screen.queryByText('88888')).toBeNull();
    // 反向：授权角色的页头条照常渲染（证明上一条不是整体不渲染）
    render(<RuntimeDetailView detail={withHeader} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['hr'] }} />);
    expect(screen.getAllByText('税前月薪：').length).toBeGreaterThanOrEqual(1);
  });

  it('页头条同样受角色门禁：分区声明 roles 的字段被pageHeader 引用时也不泄露', () => {
    // pageHeader 与分区是两条渲染路径，最容易只改一处。门禁来源是**分区控件的声明**，
    // 页头条只引用字段码 ⇒ 复用同一份判定。未授权角色的页头条不得出现该字段。
    const withHeader: FormView = {
      pageHeader: [salary, 'policy_no'],
      sections: [{
        id: 's1',
        title: 'S',
        controls: [
          { fieldCode: 'policy_no' },
          { fieldCode: salary, props: { roles: ['hr'] } },
        ],
      }],
    };
    render(<RuntimeDetailView detail={withHeader} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['employee'] }} />);
    expect(screen.queryByText('税前月薪：')).toBeNull();
    expect(screen.queryByText('88888')).toBeNull();
    // 反向：授权角色的页头条照常渲染（证明上一条不是整体不渲染）
    render(<RuntimeDetailView detail={withHeader} fields={salaryFields} record={salaryRecord} ctx={{ roles: ['hr'] }} />);
    expect(screen.getAllByText('税前月薪：').length).toBeGreaterThanOrEqual(1);
  });

  it('props.tier（档位）门禁与 roles 同源生效', () => {
    const tierOnly: FormView = {
      sections: [{
        id: 's1',
        title: 'S',
        controls: [{ fieldCode: salary, props: { tier: ['pro'] } }],
      }],
    };
    render(<RuntimeDetailView detail={tierOnly} fields={salaryFields} record={salaryRecord} ctx={{ tier: 'free' }} />);
    expect(screen.queryByText('88888')).toBeNull();
  });
});
