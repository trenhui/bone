import { describe, expect, it } from 'vitest';
import { resolveVariant } from '../../utils/uiSchemaEngine';
import {
  addVariant, buildDefaultSchema, mergeVariantIntoBase, removeVariant, updateVariant,
  setDegradedBanner, setPageHeader, updateFooter, setI18nNamespace, updateBase,
  setBatchActions, toggleQuickFilters, addStat, updateStat, removeStat, setViewKind, setDataScope,
  toggleSearchFields,
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

describe('v1.1/v1.2 新能力 ops（页头 / 合规条 / i18n / 模板 / list 全量）', () => {
  it('setPageHeader：空数组清空（不留空数组脏键）；非空按序写入', () => {
    const s = base();
    const next = setPageHeader(s, 'form', ['amount', 'status']);
    expect(next.views.form?.pageHeader).toEqual(['amount', 'status']);
    expect(setPageHeader(next, 'form', []).views.form?.pageHeader).toBeUndefined();
    // detail 与 form 独立配置，互不影响
    const d = setPageHeader(next, 'detail', ['status']);
    expect(d.views.detail?.pageHeader).toEqual(['status']);
    expect(d.views.form?.pageHeader).toEqual(['amount', 'status']);
  });

  it('setDegradedBanner：协议对象 {when,text}；空 text 清除而非留空壳（曾踩坑：string 漂移）', () => {
    const s = base();
    const withBanner = setDegradedBanner(s, { when: 'llm_unavailable', text: 'AI 服务不可用，已切换规则审查模式' });
    expect(withBanner.views.list?.degradedBanner).toEqual({ when: 'llm_unavailable', text: 'AI 服务不可用，已切换规则审查模式' });
    // 空 text ⇒ 整体清除（不给协议留 {when, text:''} 的半吊子形态）
    expect(setDegradedBanner(withBanner, { when: 'llm_unavailable', text: '   ' }).views.list?.degradedBanner).toBeUndefined();
    // when 缺省补 llm_unavailable（协议命名条件），不允许空 when 落库
    expect(setDegradedBanner(s, { when: '', text: '降级提示' }).views.list?.degradedBanner).toEqual({ when: 'llm_unavailable', text: '降级提示' });
  });

  it('负向探针：degradedBanner 若退回 string 写入，本测试必须变红（防协议漂移回归）', () => {
    const s = setDegradedBanner(base(), { when: 'llm_unavailable', text: 'x' });
    expect(typeof s.views.list?.degradedBanner).toBe('object');
  });

  it('updateFooter：三键增量合并（disclaimer/aiBadge/mockDataMark 各自独立）', () => {
    let s = updateFooter(base(), { disclaimer: '本界面由 AI 辅助生成，仅供参考' });
    expect(s.footer).toMatchObject({ disclaimer: '本界面由 AI 辅助生成，仅供参考' });
    s = updateFooter(s, { aiBadge: true });
    expect(s.footer).toMatchObject({ disclaimer: '本界面由 AI 辅助生成，仅供参考', aiBadge: true });
    s = updateFooter(s, { mockDataMark: true });
    expect(s.footer).toMatchObject({ aiBadge: true, mockDataMark: true });
  });

  it('setI18nNamespace：空串清除（不留空命名空间）；updateBase 增量合并 followMode', () => {
    const s = setI18nNamespace(base(), 'bone.ui.expense');
    expect(s.i18n).toEqual({ namespace: 'bone.ui.expense' });
    expect(setI18nNamespace(s, '').i18n).toBeUndefined();
    const b = updateBase(s, { followMode: 'notify' });
    expect(b.base).toMatchObject({ followMode: 'notify' });
    expect(updateBase(b, { followMode: 'pinned' }).base?.followMode).toBe('pinned');
  });

  it('list ops：quickFilters/batchActions 整表替换、空数组清除；stats 增改删', () => {
    let s = toggleQuickFilters(base(), ['mine', 'high_amount']);
    expect(s.views.list?.quickFilters).toEqual(['mine', 'high_amount']);
    expect(toggleQuickFilters(s, []).views.list?.quickFilters).toBeUndefined();

    s = setBatchActions(s, ['approve', 'reject']);
    expect(s.views.list?.batchActions).toEqual(['approve', 'reject']);
    expect(setBatchActions(s, []).views.list?.batchActions).toBeUndefined();

    s = addStat(s);
    expect(s.views.list?.stats).toHaveLength(1);
    expect(s.views.list?.stats?.[0]).toMatchObject({ title: '新指标', valueRef: '' });
    s = updateStat(s, 0, { title: '待审量', valueRef: 'agg.pending' });
    expect(s.views.list?.stats?.[0]).toMatchObject({ title: '待审量', valueRef: 'agg.pending' });
    s = removeStat(s, 0);
    expect(s.views.list?.stats).toHaveLength(0);
  });

  it('list ops：setViewKind/setDataScope/toggleSearchFields 空值清除语义', () => {
    let s = setViewKind(base(), 'kanban');
    expect(s.views.list?.viewKind).toBe('kanban');
    expect(setViewKind(s, '').views.list?.viewKind).toBeUndefined();

    s = setDataScope(base(), 'dept_only');
    expect(s.views.list?.dataScope).toEqual({ policyCode: 'dept_only' });
    expect(setDataScope(s, '').views.list?.dataScope).toBeUndefined();

    s = toggleSearchFields(base(), ['amount', 'status']);
    expect(s.views.list?.searchFields).toEqual(['amount', 'status']);
    expect(toggleSearchFields(s, []).views.list?.searchFields).toBeUndefined();
  });

  it('负向探针：list 写操作不得 mutate 原 schema（不可变纪律）', () => {
    const s = base();
    const snap = JSON.stringify(s);
    setPageHeader(s, 'form', ['amount']);
    setDegradedBanner(s, { when: 'llm_unavailable', text: 'x' });
    addStat(s);
    toggleQuickFilters(s, ['mine']);
    expect(JSON.stringify(s)).toBe(snap);
  });
});
