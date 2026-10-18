import { describe, expect, it } from 'vitest';
import { resolveVariant } from '../../utils/uiSchemaEngine';
import {
  addAction, addSection, addVariant, buildDefaultSchema, mergeVariantIntoBase, moveAction, moveColumn,
  removeSection, removeVariant, setColumnsVisible, setColumnsWidth, updateAction, updateVariant,
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

describe('分区 id 生成（v1.2 修复：length+1 会撞名）', () => {
  it('删中间分区后再添加：新 id 不与现存分区碰撞', () => {
    let s = addSection(addSection(base())); // [sec_main, sec_1, sec_2]
    s = removeSection(s, 'form', 1); // 删 sec_1 → [sec_main, sec_2]
    const before = (s.views.form?.sections ?? []).map((x) => x.id);
    expect(before).toEqual(['sec_main', 'sec_2']); // 防删错下标假绿
    s = addSection(s); // length+1 旧实现会再生成 sec_2 → 撞名
    const ids = (s.views.form?.sections ?? []).map((x) => x.id);
    expect(new Set(ids).size).toBe(ids.length); // 全局唯一
    expect(ids.slice(0, 2)).toEqual(before); // 原有分区不被顶改
  });

  it('反复增删不产生重复 id（压力 20 轮）', () => {
    let s = base();
    for (let i = 0; i < 20; i++) {
      s = addSection(s);
      if (i % 3 === 0 && (s.views.form?.sections?.length ?? 0) > 1) s = removeSection(s, 'form', 1);
    }
    const ids = (s.views.form?.sections ?? []).map((x) => x.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe('动作条排序单一真源（v1.2 修复：数组位移不同步 sortOrder = 运行期顺序还原）', () => {
  const withActions = () => {
    let s = base();
    for (let i = 0; i < 3; i++) s = addAction(s);
    return s;
  };

  it('addAction：sortOrder 按 1..N 递增（引擎 resolveVisibleActions 的排序真源）', () => {
    const s = withActions();
    expect(s.actionBar?.map((a) => a.sortOrder)).toEqual([1, 2, 3]);
  });

  it('moveAction 相邻位移：数组序重排为 sortOrder 1..N，面板顺序 = 引擎顺序', () => {
    const s = withActions();
    const moved = moveAction(s, 0, 1); // 第 1 项下移
    expect(moved.actionBar?.map((a) => a.sortOrder)).toEqual([1, 2, 3]);
    // 引擎按 sortOrder 稳定排序后的顺序必须与数组序一致（单一真源）
    const engineOrder = [...(moved.actionBar ?? [])]
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((a) => a.actionCode);
    expect(engineOrder).toEqual((moved.actionBar ?? []).map((a) => a.actionCode));
  });

  it('moveAction 跨位位移（0→2）：落到末尾，sortOrder 随位置整体重排', () => {
    const s = withActions();
    const moved = moveAction(s, 0, 2);
    // 原首项移到了数组末尾；sortOrder 按新位置 1..N（非相邻交换会错序，必须整体重排）
    expect(moved.actionBar?.[2]?.actionCode).toBe(s.actionBar?.[0]?.actionCode);
    expect(moved.actionBar?.map((a) => a.sortOrder)).toEqual([1, 2, 3]);
  });

  it('负向探针：moveAction 若只位移数组不重排 sortOrder，本测试必须变红', () => {
    const s = withActions();
    const moved = moveAction(s, 0, 2);
    // 旧实现：数组变了、sortOrder 没变 ⇒ 引擎排序把顺序还原（与面板展示不一致）
    const engineOrder = [...(moved.actionBar ?? [])]
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((a) => a.actionCode);
    expect(engineOrder).toEqual((moved.actionBar ?? []).map((a) => a.actionCode));
  });

  it('updateAction 可直接改 sortOrder（面板「排序」输入框即改真源）', () => {
    const s = updateAction(withActions(), 2, { sortOrder: 0 });
    expect(s.actionBar?.[2]?.sortOrder).toBe(0);
  });
});

describe('列表列批量操作（v1.2 新增：对标 Salesforce List View）', () => {
  it('setColumnsVisible(false)：全部列 visible=false（语义清除，不留空数组）', () => {
    const s = setColumnsVisible(base(), false);
    expect((s.views.list?.columns ?? []).every((c) => c.visible === false)).toBe(true);
  });

  it('setColumnsVisible(true)：清除 visible 键（回落协议缺省=可见）', () => {
    const s = setColumnsVisible(setColumnsVisible(base(), false), true);
    expect((s.views.list?.columns ?? []).every((c) => c.visible === undefined)).toBe(true);
  });

  it('setColumnsWidth：统一列宽且保留其余列属性', () => {
    const s = setColumnsWidth(base(), 160);
    expect((s.views.list?.columns ?? []).every((c) => c.width === 160)).toBe(true);
    expect(s.views.list?.columns?.[0]?.fieldCode).toBe('amount');
  });

  it('moveColumn：位移后原数组不被 mutate', () => {
    const s = base();
    const before = s.views.list?.columns?.map((c) => c.fieldCode);
    moveColumn(s, 0, 1);
    expect(s.views.list?.columns?.map((c) => c.fieldCode)).toEqual(before);
  });
});
