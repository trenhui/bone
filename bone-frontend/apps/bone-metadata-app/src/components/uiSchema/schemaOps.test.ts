import { describe, expect, it } from 'vitest';
import { resolveVariant } from '../../utils/uiSchemaEngine';
import {
  addVariant, buildDefaultSchema, mergeVariantIntoBase, removeVariant, updateVariant,
} from './schemaOps';

const base = () => buildDefaultSchema('expense', [
  { id: '1', code: 'amount', displayName: '金额', type: 'DECIMAL', required: true },
  { id: '2', code: 'status', displayName: '状态', type: 'STRING', required: false },
] as never);

describe('variant ops（变体创作纯函数）', () => {
  it('addVariant：追加默认变体且不改原对象（不可变纪律）', () => {
    const s = base();
    const next = addVariant(s);
    expect(next.variants).toHaveLength(1);
    expect(next.variants?.[0]).toMatchObject({ key: 'variant_1', label: '新变体', priority: 10, match: { roles: [] } });
    expect(s.variants).toBeUndefined(); // 原 schema 未被 mutate
    expect(addVariant(next).variants?.[1]?.key).toBe('variant_2');
  });

  it('updateVariant：只改目标下标，其余原样', () => {
    const s = addVariant(addVariant(base()));
    const next = updateVariant(s, 1, { label: '核赔工作台', match: { roles: ['reviewer'] } });
    expect(next.variants?.[0]).toEqual(s.variants?.[0]);
    expect(next.variants?.[1]).toMatchObject({ label: '核赔工作台', match: { roles: ['reviewer'] } });
  });

  it('removeVariant：按下标删除', () => {
    const s = addVariant(addVariant(base()));
    const next = removeVariant(s, 0);
    expect(next.variants).toHaveLength(1);
    expect(next.variants?.[0]?.key).toBe('variant_2');
  });

  it('越界下标：全部操作原样返回（负向：不抛异常、不产生半吊子状态）', () => {
    const s = addVariant(base());
    expect(updateVariant(s, 5, { label: 'x' })).toEqual(s);
    expect(removeVariant(s, 5)).toEqual(s);
    expect(mergeVariantIntoBase(s, 5)).toEqual(s);
  });

  it('mergeVariantIntoBase：同 id 分区覆盖、新 id 追加；未声明的规则/动作条不抹 base', () => {
    let s = base();
    s = updateVariant(addVariant(s), 0, {
      views: {
        form: {
          sections: [
            { id: 'sec_main', title: '核赔信息', controls: [{ fieldCode: 'amount' }] },
            { id: 'sec_extra', title: '复核记录', controls: [] },
          ],
        },
      },
      actionBar: [{ actionCode: 'approve', sortOrder: 1 }],
    });
    const merged = mergeVariantIntoBase(s, 0);

    // 同名分区被覆盖（标题换成变体的），新分区追加尾部
    expect(merged.views.form?.sections).toHaveLength(2);
    expect(merged.views.form?.sections?.[0]).toMatchObject({ id: 'sec_main', title: '核赔信息' });
    expect(merged.views.form?.sections?.[1]).toMatchObject({ id: 'sec_extra', title: '复核记录' });
    // 变体声明了 actionBar → 覆盖 base；未声明 linkageRules/submitRules → 保留 base
    expect(merged.actionBar).toEqual([{ actionCode: 'approve', sortOrder: 1 }]);
    expect(merged.linkageRules).toEqual(s.linkageRules);
    // 合入后变体移除
    expect(merged.variants).toHaveLength(0);
  });

  it('merge 后 resolveVariant 回落 base 即得变体内容（创作口径与裁决口径一致）', () => {
    let s = base();
    s = updateVariant(addVariant(s), 0, {
      match: { roles: ['reviewer'] },
      views: { form: { sections: [{ id: 'sec_main', title: '核赔台', controls: [] }] } },
    });
    // 合入前：reviewer 命中变体看到「核赔台」，其他人看到 base「基本信息」
    expect(resolveVariant(s, { roles: ['reviewer'] }).views.form?.sections?.[0]?.title).toBe('核赔台');
    expect(resolveVariant(s, { roles: ['employee'] }).views.form?.sections?.[0]?.title).toBe('基本信息');

    const merged = mergeVariantIntoBase(s, 0);
    // 合入后：任何身份都看到合入结果（变体已不存在）
    expect(resolveVariant(merged, { roles: ['employee'] }).views.form?.sections?.[0]?.title).toBe('核赔台');
  });

  it('负向探针：删掉 mergeSections 的 id 覆盖分支，本测试必须变红', () => {
    let s = base();
    s = updateVariant(addVariant(s), 0, {
      views: { form: { sections: [{ id: 'sec_main', title: '覆盖标题', controls: [] }] } },
    });
    const merged = mergeVariantIntoBase(s, 0);
    // 若实现退化成「仅追加不覆盖」，sec_main 会出现两份且 base 原标题残留
    const mains = (merged.views.form?.sections ?? []).filter((x) => x.id === 'sec_main');
    expect(mains).toHaveLength(1);
    expect(mains[0]?.title).toBe('覆盖标题');
    expect((merged.views.form?.sections ?? []).some((x) => x.title === '基本信息')).toBe(false);
  });
});
