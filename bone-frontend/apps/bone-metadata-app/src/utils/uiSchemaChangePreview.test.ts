import { describe, expect, it } from 'vitest';
import type { MetaField } from '../types';
import type { BoneUiSchema, LinkageRule } from './uiSchemaEngine';
import { matchText, previewChanges, variantOverrideText } from './uiSchemaChangePreview';
import { normJson } from './normJson';

/**
 * 发布变更预告单测（第五轮：上线前「这次发布会改变什么」的人话清单）。
 *
 * 负向探针纪律（防假绿）：
 * - 同键内容修改不得判成 删+增（否则预告清单虚增、误导）；
 * - 无差异必须零项（否则「待发布」徽标误报）；
 * - 分区内纯换序不得逐字段报移动；
 * - normJson 必须对键序不敏感、对值敏感。
 */

const fields: MetaField[] = [
  { code: 'claim_no', displayName: '报案号' },
  { code: 'amount', displayName: '申报金额' },
  { code: 'approver', displayName: '审批人' },
  { code: 'hospital', displayName: '就诊医院' },
  { code: 'remark', displayName: '备注' },
] as MetaField[];

function schema(patch: Partial<BoneUiSchema> = {}): BoneUiSchema {
  return {
    schemaVersion: 1,
    entityCode: 'claim',
    views: {
      form: { sections: [{ id: 'base', title: '基本信息', controls: [{ fieldCode: 'claim_no' }, { fieldCode: 'amount' }] }] },
      detail: { sections: [{ id: 'base', title: '基本信息', controls: [{ fieldCode: 'claim_no' }] }] },
      list: { columns: [{ fieldCode: 'claim_no', width: 150 }, { fieldCode: 'amount', width: 120 }] },
    },
    linkageRules: [],
    submitRules: [],
    actionBar: [],
    variants: [],
    ...patch,
  };
}

const find = (p: ReturnType<typeof previewChanges>, group: string, key: string) =>
  p.groups.find((g) => g.key === group)?.items.find((i) => i.key === key);

describe('previewChanges：locate 定位（v1.2：条目可点击跳到配置面板）', () => {
  it('新增分区 ⇒ locate 到 structure/si', () => {
    const draft = schema({
      views: {
        form: { sections: [
          { id: 'base', title: '基本信息', controls: [{ fieldCode: 'claim_no' }, { fieldCode: 'amount' }] },
          { id: 'extra', title: '复核', controls: [{ fieldCode: 'approver' }] },
        ] },
        detail: { sections: [{ id: 'base', title: '基本信息', controls: [{ fieldCode: 'claim_no' }] }] },
        list: { columns: [{ fieldCode: 'claim_no', width: 150 }, { fieldCode: 'amount', width: 120 }] },
      },
    });
    const item = find(previewChanges(draft, schema(), fields), 'form', 'section:extra');
    expect(item?.locate).toEqual({ tab: 'structure', si: 1 });
  });

  it('列宽修改 ⇒ locate 到 list/idx', () => {
    const draft = schema({ views: { ...schema().views, list: { columns: [{ fieldCode: 'claim_no', width: 200 }, { fieldCode: 'amount', width: 120 }] } } });
    const item = find(previewChanges(draft, schema(), fields), 'list', 'claim_no');
    expect(item?.locate).toEqual({ tab: 'list', idx: 0 });
  });

  it('联动规则新增/修改 ⇒ locate 到 rules/ri', () => {
    const draft = schema({ linkageRules: [{ code: 'r1', when: { fieldCode: 'amount', operator: 'gt', value: '0' }, then: [{ fieldCode: 'approver', required: true }] }] });
    const p1 = previewChanges(draft, schema(), fields);
    expect(find(p1, 'linkage', 'r1')?.locate).toEqual({ tab: 'rules', ri: 0 });
    const p2 = previewChanges(draft, { ...schema(), linkageRules: [{ code: 'r1', when: { fieldCode: 'amount', operator: 'gte', value: '0' }, then: [{ fieldCode: 'approver', required: true }] }] }, fields);
    expect(find(p2, 'linkage', 'r1')?.locate).toEqual({ tab: 'rules', ri: 0 });
  });

  it('动作条新增 ⇒ locate 到 actions/idx', () => {
    const draft = schema({ actionBar: [{ actionCode: 'approve', sortOrder: 1 }] });
    expect(find(previewChanges(draft, schema(), fields), 'action', 'approve')?.locate).toEqual({ tab: 'actions', idx: 0 });
  });

  describe('previewChanges：同 code 重复规则 diff 键稳健化（v1.2：不静默漏报）', () => {
    const rule = (code: string, extra: Partial<LinkageRule> = {}): LinkageRule =>
      ({ code, name: code, when: 'always', then: [], ...extra } as LinkageRule);

    it('两条同 code 规则在草稿新增 ⇒ 各自独立 add（不折叠成一条）', () => {
      const draft = schema({ linkageRules: [rule('R'), rule('R')] });
      const g = previewChanges(draft, schema(), fields).groups.find((x) => x.key === 'linkage')!;
      const adds = g.items.filter((i) => i.kind === 'add');
      expect(adds).toHaveLength(2);
      expect(adds.map((i) => i.key).sort()).toEqual(['R#0', 'R#1']);
    });

    it('两条同 code 但内容不同 ⇒ 各自独立 modify（不互相覆盖）', () => {
      const draft = schema({ linkageRules: [rule('R', { name: '甲' }), rule('R', { name: '乙' })] });
      const pub = schema({ linkageRules: [rule('R'), rule('R')] });
      const g = previewChanges(draft, pub, fields).groups.find((x) => x.key === 'linkage')!;
      expect(g.items.filter((i) => i.kind === 'modify')).toHaveLength(2);
    });

    it('负向探针：唯一 code 仍用 code 本身作键（定位语义不变）', () => {
      const item = find(previewChanges(schema({ linkageRules: [rule('R1')] }), schema(), fields), 'linkage', 'R1');
      expect(item?.kind).toBe('add');
    });

    it('同 code 重复 + 其中一条新增 ⇒ 命中那条判 add、其余判 modify（不漏报）', () => {
    // 发布态有 2 条 R；草稿在末尾新增第 3 条 R ⇒ 第 3 条 add，前两条按位置 modify
      const pub = schema({ linkageRules: [rule('R'), rule('R')] });
      const draft = schema({ linkageRules: [rule('R'), rule('R'), rule('R', { name: '丙' })] });
      const g = previewChanges(draft, pub, fields).groups.find((x) => x.key === 'linkage')!;
      expect(g.items.filter((i) => i.kind === 'add')).toHaveLength(1);
      expect(g.items.find((i) => i.kind === 'add')?.key).toBe('R#2');
    });
  });

  it('变体新增 ⇒ locate 到 variants/vi', () => {
    const draft = schema({ variants: [{ key: 'v1', label: '移动端', priority: 10, match: { devices: ['mobile'] } }] });
    expect(find(previewChanges(draft, schema(), fields), 'variant', 'v1')?.locate).toEqual({ tab: 'variants', vi: 0 });
  });
});

describe('previewChanges：规则 modify 字段级明细（v1.2：说清「改了哪」）', () => {
  const baseRule: LinkageRule = { code: 'r1', name: '大额提示', when: { fieldCode: 'amount', operator: 'gt', value: '1000' }, then: [{ fieldCode: 'approver', required: true }] };

  it('算子修改 ⇒ 明细含 when.operator', () => {
    const draft = schema({ linkageRules: [{ ...baseRule, when: { fieldCode: 'amount', operator: 'gte', value: '1000' } }] });
    const pub = schema({ linkageRules: [baseRule] });
    const item = find(previewChanges(draft, pub, fields), 'linkage', 'r1');
    expect(item?.kind).toBe('modify');
    expect(item?.text).toContain('when.operator');
    expect(item?.text).toContain('规则「大额提示」');
  });

  it('then 动作修改 ⇒ 明细含 then（数组整体算一处，不逐行刷屏）', () => {
    const draft = schema({ linkageRules: [{ ...baseRule, then: [{ fieldCode: 'approver', required: true, readonly: true }] }] });
    const pub = schema({ linkageRules: [baseRule] });
    const item = find(previewChanges(draft, pub, fields), 'linkage', 'r1');
    expect(item?.text).toContain('then');
  });

  it('停用/启用 ⇒ 明细含 enabled', () => {
    const draft = schema({ linkageRules: [{ ...baseRule, enabled: false }] });
    const pub = schema({ linkageRules: [baseRule] });
    expect(find(previewChanges(draft, pub, fields), 'linkage', 'r1')?.text).toContain('enabled');
  });

  it('多处变化 ⇒ 折叠为「等 N 处」且不超过 3 条路径', () => {
    const draft = schema({
      linkageRules: [{
        code: 'r1', name: '大额提示',
        when: { fieldCode: 'amount', operator: 'gte', value: '2000' },
        then: [{ fieldCode: 'approver', required: true, readonly: true, notice: 'x' }],
        enabled: false,
      }],
    });
    const pub = schema({ linkageRules: [baseRule] });
    const item = find(previewChanges(draft, pub, fields), 'linkage', 'r1');
    expect(item?.text).toContain('等');
    // 路径最多 3 个（when.operator/when.value/then/enabled 里取前 3）
    const paths = (item?.text ?? '').match(/when\.operator|when\.value|then|enabled/g) ?? [];
    expect(paths.length).toBeLessThanOrEqual(3);
  });

  it('负向探针：仅文案差异（name 相同、结构等价）不得判 modify（normJson 兜底）', () => {
    const draft = schema({ linkageRules: [baseRule] });
    const pub = schema({ linkageRules: [{ ...baseRule }] });
    expect(find(previewChanges(draft, pub, fields), 'linkage', 'r1')).toBeUndefined();
  });
});

describe('previewChanges', () => {
  it('草稿与发布快照一致 ⇒ 零项（待发布判定不得误报）', () => {
    const p = previewChanges(schema(), schema(), fields);
    expect(p.total).toBe(0);
    expect(p.summary).toBe('');
    expect(p.groups).toEqual([]);
  });

  it('键序不同的同一份 schema ⇒ 仍判一致（normJson 内容等价）', () => {
    const a = schema();
    const serialized = JSON.stringify(a).replace(
      '"schemaVersion":1,"entityCode":"claim"',
      '"entityCode":"claim","schemaVersion":1',
    );
    expect(serialized.startsWith('{"entityCode"')).toBe(true); // 防替换落空假绿
    const b = JSON.parse(serialized) as BoneUiSchema;
    expect(normJson(a)).toBe(normJson(b));
    expect(previewChanges(a, b, fields).total).toBe(0);
  });

  it('draft 为 null（加载失败）⇒ 空预告，不抛异常', () => {
    expect(previewChanges(null, schema(), fields).total).toBe(0);
  });

  it('从未发布（published=null）⇒ 表单/详情/列表全部按新增预告', () => {
    const p = previewChanges(schema(), null, fields);
    const keys = p.groups.map((g) => g.key);
    expect(keys).toEqual(['form', 'detail', 'list']);
    expect(p.total).toBeGreaterThan(0);
    // 分区整体折叠为一条，不逐字段铺开
    const secItems = p.groups.find((g) => g.key === 'form')!.items;
    expect(secItems).toHaveLength(1);
    expect(secItems[0].kind).toBe('add');
    expect(secItems[0].text).toContain('新增分区「基本信息」');
    expect(secItems[0].text).toContain('2 个字段');
  });

  it('表单新增字段（存活分区内）⇒ add 项带显示名与所在分区', () => {
    const draft = schema();
    draft.views.form!.sections[0].controls.push({ fieldCode: 'approver' });
    const item = find(previewChanges(draft, schema(), fields), 'form', 'approver');
    expect(item?.kind).toBe('add');
    expect(item?.text).toContain('审批人');
    expect(item?.text).toContain('基本信息');
  });

  it('新建分区折叠其字段（一条分区项，不逐字段罗列）', () => {
    const draft = schema();
    draft.views.form!.sections.push({ id: 'risk', title: '风控信息', controls: [{ fieldCode: 'approver' }, { fieldCode: 'hospital' }] });
    const p = previewChanges(draft, schema(), fields);
    const items = p.groups.find((g) => g.key === 'form')!.items;
    expect(items).toHaveLength(1);
    expect(items[0].key).toBe('section:risk');
    expect(items[0].text).toContain('风控信息');
    expect(items[0].text).toContain('2 个字段');
  });

  it('负向探针：同字段属性修改不得判成 删+增', () => {
    const draft = schema();
    draft.views.form!.sections[0].controls[1].labelOverride = '申报金额（元）';
    const p = previewChanges(draft, schema(), fields);
    const items = p.groups.find((g) => g.key === 'form')!.items;
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe('modify');
    expect(items[0].text).toContain('显示名改为');
  });

  it('负向探针：分区内纯换序 ⇒ 一条顺序调整，不得逐字段报移动', () => {
    const draft = schema();
    const ctrls = draft.views.form!.sections[0].controls;
    draft.views.form!.sections[0].controls = [ctrls[1], ctrls[0]];
    const p = previewChanges(draft, schema(), fields);
    const items = p.groups.find((g) => g.key === 'form')!.items;
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe('modify');
    expect(items[0].text).toContain('顺序调整');
  });

  it('跨分区移动识别为 modify（不是 删+增）', () => {
    const base = schema();
    base.views.form!.sections.push({ id: 'risk', title: '风控信息', controls: [] });
    const draft = JSON.parse(JSON.stringify(base)) as BoneUiSchema;
    // amount 从 base 移到 risk
    draft.views.form!.sections[0].controls = draft.views.form!.sections[0].controls.filter((c) => c.fieldCode !== 'amount');
    draft.views.form!.sections[1].controls.push({ fieldCode: 'amount' });
    const p = previewChanges(draft, base, fields);
    const items = p.groups.find((g) => g.key === 'form')!.items;
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe('modify');
    expect(items[0].text).toContain('移到');
  });

  it('移除字段 ⇒ remove 项', () => {
    const draft = schema();
    draft.views.form!.sections[0].controls = draft.views.form!.sections[0].controls.filter((c) => c.fieldCode !== 'amount');
    const item = find(previewChanges(draft, schema(), fields), 'form', 'amount');
    expect(item?.kind).toBe('remove');
    expect(item?.text).toContain('移除字段');
  });

  it('列表：新增列带属性、列宽修改、移除列、列序调整各归各类', () => {
    const draft = schema();
    draft.views.list!.columns = [
      { fieldCode: 'amount', width: 120 },
      { fieldCode: 'claim_no', width: 150 },
      { fieldCode: 'approver', width: 110, pinned: 'right' },
    ];
    const p = previewChanges(draft, schema(), fields);
    expect(find(p, 'list', 'approver')?.kind).toBe('add');
    expect(find(p, 'list', 'approver')?.text).toContain('固定右侧');
    // claim_no 150→150、amount 120→120 列宽未变 ⇒ 存量列无 modify 项
    const listItems = p.groups.find((g) => g.key === 'list')!.items;
    expect(listItems.some((i) => i.kind === 'modify')).toBe(false);

    const draft2 = schema();
    draft2.views.list!.columns = [{ fieldCode: 'claim_no', width: 180 }, { fieldCode: 'amount', width: 120 }];
    const p2 = previewChanges(draft2, schema(), fields);
    const w = find(p2, 'list', 'claim_no');
    expect(w?.kind).toBe('modify');
    expect(w?.text).toContain('列宽 150→180');

    // 同集合换序 ⇒ 一条列序调整
    const draft3 = schema();
    draft3.views.list!.columns = [
      { fieldCode: 'amount', width: 120 },
      { fieldCode: 'claim_no', width: 150 },
    ];
    const p3 = previewChanges(draft3, schema(), fields);
    const order = p3.groups.find((g) => g.key === 'list')!.items;
    expect(order).toHaveLength(1);
    expect(order[0].key).toBe('order');
    expect(order[0].text).toContain('列序调整');

    // 移除列 ⇒ remove
    const draft4 = schema();
    draft4.views.list!.columns = [{ fieldCode: 'claim_no', width: 150 }];
    expect(find(previewChanges(draft4, schema(), fields), 'list', 'amount')?.kind).toBe('remove');
  });

  it('规则：新增带载荷（供 describe 组句）、修改判 modify、删除带载荷', () => {
    const draft = schema({
      linkageRules: [
        { code: 'approver_req', name: '大额审批', when: { fieldCode: 'amount', operator: 'gt', value: 5000 }, then: [{ fieldCode: 'approver', required: true }] },
      ],
    });
    const p = previewChanges(draft, schema(), fields);
    const add = find(p, 'linkage', 'approver_req');
    expect(add?.kind).toBe('add');
    expect(add?.rule).toBeTruthy();
    expect(add?.text).toBeUndefined();

    const published = schema({
      linkageRules: [
        { code: 'approver_req', name: '大额审批', when: { fieldCode: 'amount', operator: 'gt', value: 5000 }, then: [{ fieldCode: 'approver', required: true }] },
      ],
    });
    const draft2 = JSON.parse(JSON.stringify(published)) as BoneUiSchema;
    draft2.linkageRules![0].when!.value = 8000;
    const p2 = previewChanges(draft2, published, fields);
    const mod = find(p2, 'linkage', 'approver_req');
    expect(mod?.kind).toBe('modify');
    expect(p2.groups.find((g) => g.key === 'linkage')!.items).toHaveLength(1); // 负向：不得同时出现删+增

    const p3 = previewChanges(schema(), published, fields);
    const rm = find(p3, 'linkage', 'approver_req');
    expect(rm?.kind).toBe('remove');
    expect(rm?.rule).toBeTruthy();
  });

  it('动作条/提交校验按各自分组归组', () => {
    const draft = schema({
      submitRules: [{ code: 'sum_chk', function: 'sum', fields: ['amount'], operator: 'gt', compare: { valueType: 'fixed', value: 0 }, verifyType: 'ERROR', message: '金额须大于0' }],
      actionBar: [{ actionCode: 'approve' }],
    });
    const p = previewChanges(draft, schema(), fields);
    expect(find(p, 'submit', 'sum_chk')?.kind).toBe('add');
    expect(find(p, 'action', 'approve')?.kind).toBe('add');
    expect(p.groups.map((g) => g.key)).toContain('submit');
    expect(p.groups.map((g) => g.key)).toContain('action');
  });

  it('变体：新增带命中条件与覆盖统计、移除、修改各归各类', () => {
    const draft = schema({
      variants: [{ key: 'reviewer_wb', label: '核赔工作台', match: { roles: ['reviewer'] }, views: { form: { sections: [{ id: 'wb', controls: [] }] } } }],
    });
    const p = previewChanges(draft, schema(), fields);
    const add = find(p, 'variant', 'reviewer_wb');
    expect(add?.kind).toBe('add');
    expect(add?.text).toContain('核赔工作台');
    expect(add?.text).toContain('角色');

    const published = schema({
      variants: [{ key: 'reviewer_wb', label: '核赔工作台', match: { roles: ['reviewer'] } }],
    });
    const p2 = previewChanges(draft, published, fields);
    expect(find(p2, 'variant', 'reviewer_wb')?.kind).toBe('modify');

    const p3 = previewChanges(schema(), published, fields);
    expect(find(p3, 'variant', 'reviewer_wb')?.kind).toBe('remove');
  });

  it('summary 一行摘要含分组标签与增减计数', () => {
    const draft = schema();
    draft.views.form!.sections[0].controls.push({ fieldCode: 'approver' });
    draft.linkageRules = [{ code: 'r1', when: { fieldCode: 'amount', operator: 'gt', value: 1 }, then: [] }];
    const p = previewChanges(draft, schema(), fields);
    expect(p.summary).toContain('录入表单 +1');
    expect(p.summary).toContain('联动规则 +1');
    expect(p.total).toBe(2);
  });

  it('负向探针：normJson 对值敏感（改值得不同串）', () => {
    expect(normJson({ a: 1, b: 2 })).not.toBe(normJson({ a: 1, b: 3 }));
  });
});

describe('matchText / variantOverrideText', () => {
  it('空 match ⇒ 全部身份兜底', () => {
    expect(matchText(undefined)).toBe('全部身份（兜底）');
    expect(matchText({})).toBe('全部身份（兜底）');
  });

  it('四维命中条件可读拼接', () => {
    expect(matchText({ roles: ['reviewer'], devices: ['mobile'] })).toBe('角色∈{reviewer} 且 设备∈{mobile}');
  });

  it('未覆盖任何块 ⇒ 明示沿用基础模板', () => {
    expect(variantOverrideText({ key: 'k' })).toContain('沿用基础模板');
  });

  it('覆盖块统计按块计数', () => {
    const v = {
      key: 'k',
      views: { form: { sections: [{ id: 'a', controls: [] }, { id: 'b', controls: [] }] } },
      linkageRules: [{ code: 'r', when: {}, then: [] }],
      actionBar: [{ actionCode: 'x' }],
    };
    const t = variantOverrideText(v);
    expect(t).toContain('结构 2 分区');
    expect(t).toContain('规则 1 条');
    expect(t).toContain('动作条 1 项');
  });
});
