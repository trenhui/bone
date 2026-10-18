import { describe, expect, it } from 'vitest';
import { enumFieldsOf, lintSchema, type LintIssue } from './lint';
import { buildDefaultSchema, setDegradedBanner, setPageHeader, addStat } from './schemaOps';
import type { MetaField } from '../../types';

const FIELDS = [
  { id: '1', code: 'amount', displayName: '金额', type: 'DECIMAL', required: true },
  { id: '2', code: 'status', displayName: '状态', type: 'STRING', required: false, enumValues: JSON.stringify([{ code: 'DRAFT', label: '草稿' }, { code: 'SUBMITTED', label: '已提交' }]) },
  { id: '3', code: 'approver_id', displayName: '审批人', type: 'STRING', required: false },
] as unknown as MetaField[];

const known = new Set(FIELDS.map((f) => f.code));

function base() {
  return buildDefaultSchema('expense', FIELDS);
}

/** 只取某级别 + 消息含子串的 issue，断言更聚焦 */
function at(issues: LintIssue[], level: LintIssue['level'], sub: string): LintIssue | undefined {
  return issues.find((i) => i.level === level && i.message.includes(sub));
}

describe('lintSchema：默认 schema 干净（零 error 零 warning）', () => {
  it('基线自检：buildDefaultSchema 不应触发任何 error/warning（否则编辑器一打开就红）', () => {
    const issues = lintSchema(base(), known, enumFieldsOf(FIELDS)).filter((i) => i.level !== 'info');
    expect(issues).toEqual([]);
  });
});

describe('lintSchema：pageHeader 悬空引用（协议 v1.2 §13.2）', () => {
  it('页头引用不在任何分区控件中的字段 ⇒ error 且定位到 structure/pi', () => {
    const s = setPageHeader(base(), 'form', ['ghost_field']);
    const issue = at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', '页头引用了不在任何分区控件中的字段 ghost_field');
    expect(issue).toBeDefined();
    expect(issue?.locate).toMatchObject({ tab: 'structure', pi: 0 });
  });

  it('页头引用真实存在于分区控件的字段 ⇒ 无 error', () => {
    const s = setPageHeader(base(), 'form', ['amount']);
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.level === 'error')).toEqual([]);
  });

  it('页头重复引用同一字段 ⇒ warning（不是 error，渲染层去重即可）', () => {
    const s = setPageHeader(base(), 'form', ['amount', 'amount']);
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '页头重复引用字段 amount')).toBeDefined();
  });

  it('页头空字段码 ⇒ error', () => {
    const s = setPageHeader(base(), 'form', ['']);
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', '页头第 1 项为空字段码')).toBeDefined();
  });

  it('detail 视图页头独立校验（form 干净但 detail 悬空 ⇒ 仍报 error）', () => {
    const s = setPageHeader(setPageHeader(base(), 'form', ['amount']), 'detail', ['ghost']);
    const issues = lintSchema(s, known, enumFieldsOf(FIELDS));
    expect(at(issues, 'error', '详情页头引用了不在任何分区控件中的字段 ghost')).toBeDefined();
  });
});

describe('lintSchema：list 引用面', () => {
  it('统计卡缺 valueRef ⇒ warning（UI 不自算，聚合口径必须显式引用）', () => {
    const s = addStat(base());
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '未引用聚合口径 valueRef')).toBeDefined();
  });

  it('dataScope 声明但 policyCode 为空 ⇒ error', () => {
    const s = base();
    s.views.list!.dataScope = { policyCode: '' };
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', 'policyCode 为空')).toBeDefined();
  });

  it('degradedBanner 空 text ⇒ warning（依赖降级时用户看不到提示）', () => {
    const s = base();
    s.views.list!.degradedBanner = { when: 'llm_unavailable', text: '  ' };
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '降级横幅 text 为空')).toBeDefined();
  });

  it('合法 degradedBanner 对象不触发 warning（防误报回归）', () => {
    const s = setDegradedBanner(base(), { when: 'llm_unavailable', text: 'AI 不可用，规则审查模式' });
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.level !== 'info')).toEqual([]);
  });

  it('viewKind 超出协议 enum ⇒ warning（前向兼容但提示）', () => {
    const s = base();
    s.views.list!.viewKind = 'gantt';
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '视图形态 gantt 不在协议 enum')).toBeDefined();
  });

  it('页签绑定不存在字段 ⇒ error（硬过滤恒空，非 warning）；空字段码 ⇒ error', () => {
    const s = base();
    s.views.list!.tabs = [
      { title: '我的', fieldCode: 'ghost', value: '$me' },
      { title: '空码', fieldCode: '', value: 'x' },
    ];
    const issues = lintSchema(s, known, enumFieldsOf(FIELDS));
    expect(at(issues, 'error', '绑定了实体中不存在的字段 ghost')).toBeDefined();
    expect(at(issues, 'error', '列表页签第 2 个未绑定字段')).toBeDefined();
  });

  it('合法页签（真实字段 + $me 上下文变量）零 error——防误报回归', () => {
    const s = base();
    s.views.list!.tabs = [
      { title: '待我审批', fieldCode: 'approver_id', value: '$me' },
      { title: '草稿', fieldCode: 'status', value: 'DRAFT' },
      { title: '风险', fieldCode: 'amount', value: 0 },
    ];
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.level === 'error')).toEqual([]);
  });

  it('变体列表页签绑不存在字段 ⇒ error 且定位带 vi（base 查变体也要查）', () => {
    const s = base();
    s.variants = [
      {
        key: 'v1',
        label: '核赔工作台',
        match: { roles: ['reviewer'] },
        views: { list: { tabs: [{ title: '我的', fieldCode: 'ghost', value: '$me' }] } },
      },
    ];
    const issue = at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', '绑定了实体中不存在的字段 ghost');
    expect(issue).toBeDefined();
    // 变体作用域（vi 有值）的问题统一落 variants 面板（lint.ts scopeTab 约定）
    expect(issue?.locate).toMatchObject({ tab: 'variants', vi: 0 });
  });
});

describe('lintSchema：HITL 动作配置完整性（v1.2 §13.3）', () => {
  it('decisionKind=hitl 但无 reasonTypes ⇒ warning', () => {
    const s = base();
    s.actionBar = [{ actionCode: 'reject', decisionKind: 'hitl' }];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '未配置 reasonTypes')).toBeDefined();
  });

  it('decisionKind=hitl 配了 reasonTypes ⇒ 不报', () => {
    const s = base();
    s.actionBar = [{ actionCode: 'reject', decisionKind: 'hitl', reasonTypes: ['材料不符', '金额存疑'] }];
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.level === 'warning' && i.message.includes('reasonTypes'))).toEqual([]);
  });

  it('maxRounds<1 ⇒ warning（人工协商轮次无意义）', () => {
    const s = base();
    s.actionBar = [{ actionCode: 'reject', decisionKind: 'hitl', reasonTypes: ['x'], maxRounds: 0 }];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', 'maxRounds 应 ≥ 1')).toBeDefined();
  });
});

describe('lintSchema：规则冲突 / 死代码检测（v1.2 新增）', () => {
  it('联动编码重复 ⇒ warning', () => {
    const s = base();
    s.linkageRules = [
      { code: 'tax_show', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, then: [{ fieldCode: 'amount', visible: true }] },
      { code: 'tax_show', when: { fieldCode: 'status', operator: 'eq', value: 'SUBMITTED' }, then: [{ fieldCode: 'amount', visible: false }] },
    ];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '编码与其他规则重复')).toBeDefined();
  });

  it('多规则写同一字段 ⇒ warning（静默互相覆盖是高发误配）', () => {
    const s = base();
    s.linkageRules = [
      { code: 'r1', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, then: [{ fieldCode: 'amount', readonly: true }] },
      { code: 'r2', when: { fieldCode: 'status', operator: 'eq', value: 'SUBMITTED' }, then: [{ fieldCode: 'amount', readonly: false }] },
    ];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '被 2 条联动规则同时写入')).toBeDefined();
  });

  it('赋值联动成环 ⇒ warning（引擎单轮收敛，环上赋值不生效）', () => {
    const s = base();
    s.linkageRules = [
      { code: 'a2b', when: { fieldCode: 'amount', operator: 'gt', value: '0' }, then: [{ fieldCode: 'approver_id', value: 'x' }] },
      { code: 'b2a', when: { fieldCode: 'approver_id', operator: 'isNotNull' }, then: [{ fieldCode: 'amount', value: '1' }] },
    ];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '赋值联动存在环')).toBeDefined();
  });

  it('单向赋值不成环 ⇒ 不报（防误报回归）', () => {
    const s = base();
    s.linkageRules = [
      { code: 'a2b', when: { fieldCode: 'amount', operator: 'gt', value: '0' }, then: [{ fieldCode: 'approver_id', value: 'x' }] },
      { code: 'c2d', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, then: [{ fieldCode: 'approver_id', value: 'y' }] },
    ];
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.message.includes('环'))).toEqual([]);
  });

  it('动作条重复 actionCode ⇒ warning', () => {
    const s = base();
    s.actionBar = [
      { actionCode: 'approve', sortOrder: 1 },
      { actionCode: 'approve', sortOrder: 2 },
    ];
    expect(at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'warning', '在动作条中重复出现')).toBeDefined();
  });
});

describe('lintSchema：变体覆盖（v1.2 补齐：此前只扫 base）', () => {
  it('变体控件引用不存在字段 ⇒ error 且 locate 带 vi', () => {
    const s = base();
    s.variants = [{
      key: 'reviewer', label: '核赔台', priority: 10, match: { roles: ['reviewer'] },
      views: { form: { sections: [{ id: 'sec_x', title: '台', controls: [{ fieldCode: 'ghost_field' }] }] } },
    }];
    const issue = at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', 'ghost_field');
    expect(issue).toBeDefined();
    // 变体作用域问题统一落 variants 面板（点击自检跳到变体卡片，vi 不丢）
    expect(issue?.locate).toMatchObject({ tab: 'variants', vi: 0 });
    expect(issue?.message).toContain('变体「核赔台」');
  });

  it('变体空 then ⇒ error（此前可存可发布）', () => {
    const s = base();
    s.variants = [{
      key: 'reviewer', priority: 10, match: { roles: ['reviewer'] },
      linkageRules: [{ code: 'v_rule', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, then: [] }],
    }];
    const issue = at(lintSchema(s, known, enumFieldsOf(FIELDS)), 'error', '没有 then 动作');
    expect(issue).toBeDefined();
    expect(issue?.locate).toMatchObject({ tab: 'variants', vi: 0 });
  });

  it('变体未覆盖任何块 ⇒ 不产生变体作用域 issue（干净变体不误报）', () => {
    const s = base();
    s.variants = [{ key: 'mobile', label: '移动端', priority: 20, match: { devices: ['mobile'] } }];
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.message.includes('变体'))).toEqual([]);
  });

  it('变体 then 指向自己视图内的字段 ⇒ 不报表单外字段', () => {
    const s = base();
    s.variants = [{
      key: 'reviewer', priority: 10, match: { roles: ['reviewer'] },
      views: { form: { sections: [{ id: 'sec_x', title: '台', controls: [{ fieldCode: 'amount' }, { fieldCode: 'approver_id' }] }] } },
      linkageRules: [{ code: 'v1', when: { fieldCode: 'status', operator: 'eq', value: 'DRAFT' }, then: [{ fieldCode: 'approver_id', readonly: true }] }],
    }];
    expect(lintSchema(s, known, enumFieldsOf(FIELDS)).filter((i) => i.level !== 'info' && i.message.includes('变体'))).toEqual([]);
  });
});
