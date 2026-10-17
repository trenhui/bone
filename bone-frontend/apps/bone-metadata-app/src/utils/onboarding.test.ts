import { describe, expect, it } from 'vitest';
import { onboardingMilestones, TOUR_STEPS } from './onboarding';
import type { BoneUiSchema, ListColumn } from './uiSchemaEngine';

/** 默认态：全字段平铺进单一「基本信息」分区 + 列表全列 120px（buildDefaultSchema 的产物形态） */
function defaultSchema(codes: string[]): BoneUiSchema {
  return {
    schemaVersion: 1,
    minor: 1,
    entityCode: 'claim_case',
    displayModes: ['create', 'edit', 'view'],
    views: {
      list: { columns: codes.map((c) => ({ fieldCode: c, visible: true, width: 120 })) },
      form: {
        sections: [{
          id: 'sec_main',
          title: '基本信息',
          controls: codes.map((c) => ({ fieldCode: c, controlKind: 'INPUT' })),
        }],
      },
    },
    linkageRules: [],
    submitRules: [],
    actionBar: [],
  };
}

/** 在默认态基础上加一个「金额信息」分区（组织过表单的最小信号） */
function withSecondSection(codes: string[]): BoneUiSchema {
  const s = defaultSchema(codes);
  s.views.form!.sections.push({ id: 'sec_money', title: '金额信息', controls: [{ fieldCode: 'claim_amount', controlKind: 'INPUT' }] });
  return s;
}

/** 测试内取列表列数组（工厂保证存在；断言失败时给出明确错误而非静默 undefined） */
function cols(s: BoneUiSchema): ListColumn[] {
  const c = s.views.list?.columns;
  if (!c) throw new Error('defaultSchema 工厂必须定义 views.list.columns');
  return c;
}

const FIELDS = ['claim_no', 'policy_no', 'claim_amount', 'invoice_no', 'invoice_date', 'status'];

describe('onboardingMilestones · 0-1 五里程碑', () => {
  it('默认平铺 schema（新实体首开）必须 0/5——首开进度卡不虚亮', () => {
    const ms = onboardingMilestones(defaultSchema(FIELDS), 'never', 0, FIELDS.length);
    expect(ms.filter((m) => m.done).map((m) => m.key)).toEqual([]);
    // 显式盯防「假绿」：默认 schema 本身零协议错误，但无任何真实配置 ⇒ 自检项不得亮
    expect(ms.find((m) => m.key === 'lint')!.done).toBe(false);
  });

  it('全配完（2 分区 + 精选列 + 规则 + 零错误 + 已发布）必须 5/5', () => {
    const s = withSecondSection(FIELDS);
    s.views.list!.columns = cols(s).slice(0, 3); // 6 列精选为 3 列
    s.submitRules = [{
      code: 'amt_gt0', function: 'sum', fields: ['claim_amount'], operator: 'gt',
      compare: { valueType: 'fixed', value: 0 }, verifyType: 'ERROR', message: '金额须大于 0',
    }];
    const ms = onboardingMilestones(s, 'published', 0, FIELDS.length);
    expect(ms.filter((m) => m.done).map((m) => m.key)).toEqual(['form', 'list', 'rules', 'lint', 'publish']);
  });

  it('负向：pending 态（有已保存未发布改动）publish 里程碑不得为 true', () => {
    const s = withSecondSection(FIELDS);
    s.views.list!.columns = cols(s).slice(0, 3);
    s.actionBar = [{ actionCode: 'submit' }];
    const ms = onboardingMilestones(s, 'pending', 0, FIELDS.length);
    expect(ms.find((m) => m.key === 'publish')!.done).toBe(false);
    expect(ms.filter((m) => m.done).length).toBe(4);
  });

  it('负向：有协议错误时 lint 不得为 true（错误必须阻断「自检通过」语义）', () => {
    const s = withSecondSection(FIELDS);
    const ms = onboardingMilestones(s, 'never', 2, FIELDS.length);
    expect(ms.find((m) => m.key === 'lint')!.done).toBe(false);
  });

  it('列表精选的三种信号：删列 / 调宽 / 固定列，任一即算精选过', () => {
    // 删列：6 → 4
    const pruned = defaultSchema(FIELDS);
    pruned.views.list!.columns = cols(pruned).slice(0, 4);
    expect(onboardingMilestones(pruned, 'never', 0, FIELDS.length).find((m) => m.key === 'list')!.done).toBe(true);
    // 调宽：列数不变但宽度非默认 120
    const resized = defaultSchema(FIELDS);
    cols(resized)[1] = { ...cols(resized)[1], width: 200 };
    expect(onboardingMilestones(resized, 'never', 0, FIELDS.length).find((m) => m.key === 'list')!.done).toBe(true);
    // 固定列（协议 enum：left=钉主键侧）
    const pinned = defaultSchema(FIELDS);
    cols(pinned)[0] = { ...cols(pinned)[0], pinned: 'left' };
    expect(onboardingMilestones(pinned, 'never', 0, FIELDS.length).find((m) => m.key === 'list')!.done).toBe(true);
    // 负向：原样不动（含 width 恰好 120）不算精选
    expect(onboardingMilestones(defaultSchema(FIELDS), 'never', 0, FIELDS.length).find((m) => m.key === 'list')!.done).toBe(false);
  });

  it('规则计数含变体与动作条（不只是 linkage/submit）', () => {
    const withVariant = defaultSchema(FIELDS);
    withVariant.variants = [{ key: 'v1', label: '核赔视角', priority: 10, match: { roles: ['reviewer'] }, views: { form: { sections: [] } } }];
    expect(onboardingMilestones(withVariant, 'never', 0, FIELDS.length).find((m) => m.key === 'rules')!.done).toBe(true);
    const withAction = defaultSchema(FIELDS);
    withAction.actionBar = [{ actionCode: 'submit' }];
    expect(onboardingMilestones(withAction, 'never', 0, FIELDS.length).find((m) => m.key === 'rules')!.done).toBe(true);
  });

  it('边界：无字段实体（fieldCount=0）列表项不得虚亮；schema 未加载时全不亮', () => {
    const empty = onboardingMilestones(defaultSchema([]), 'never', 0, 0);
    expect(empty.find((m) => m.key === 'list')!.done).toBe(false);
    const noSchema = onboardingMilestones(null, 'never', 0, 5);
    expect(noSchema.filter((m) => m.done)).toEqual([]);
  });
});

describe('TOUR_STEPS · 引导步骤定义', () => {
  it('六步、key 唯一、action 步必带 doneText', () => {
    expect(TOUR_STEPS).toHaveLength(6);
    expect(new Set(TOUR_STEPS.map((s) => s.key)).size).toBe(6);
    for (const s of TOUR_STEPS) {
      if (s.action) expect(s.doneText, `action 步 ${s.key} 缺少完成提示`).toBeTruthy();
      expect(s.body.length).toBeGreaterThan(10);
    }
  });

  it('action 步覆盖三个真实操作点：字段库 / 列表 tab / 规则 tab', () => {
    expect(TOUR_STEPS.filter((s) => s.action).map((s) => s.anchor)).toEqual(['palette-fab', 'tab-list', 'tab-rules']);
  });
});
