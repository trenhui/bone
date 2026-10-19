/**
 * UI Schema 不可变操作集。
 *
 * 全部为纯函数（输入 schema，输出新 schema），不持有状态、不直接 mutate。
 * 这样所有编辑天然可撤销（保留上一份引用即可），且不出现"改 A 影响 B"的隐式共享。
 */
import type {
  ActionBarItem, BoneUiSchema, Control, FormView, LinkageRule, LinkageThen, ListColumn, ListStat, ListTab, ListView, SchemaBase, SchemaFooter, Section, SubmitRule, Variant,
} from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { FIELD_TYPE_TO_KIND } from './constants';

/** 视图键：form=录入/编辑表单，detail=详情视图（同结构但独立配置） */
export type ViewKey = 'form' | 'detail';

/** 取指定视图（缺省补空分区列表，避免到处判空） */
export function viewOf(s: BoneUiSchema, view: ViewKey = 'form'): FormView {
  return (view === 'detail' ? s.views.detail : s.views.form) ?? { sections: [] };
}

/** 写回指定视图（保留 pageHeader 等其余键） */
export function withView(s: BoneUiSchema, v: FormView, view: ViewKey = 'form'): BoneUiSchema {
  return view === 'detail'
    ? { ...s, views: { ...s.views, detail: v } }
    : { ...s, views: { ...s.views, form: v } };
}

/** 兼容旧调用：表单视图（FormView 含 sections/pageHeader，预览按 pageHeader 渲染页头条） */
export const formOf = (s: BoneUiSchema): FormView => viewOf(s, 'form');

/* ============================== 分区 ============================== */

/**
 * 分区 id 生成器：模块级自增 + 与现存 id 去重。
 *
 * 为什么不能 `sec_${sections.length + 1}`：删掉中间分区后 length 会缩，
 * 新 id 直接撞名（[sec_1,sec_2] 删 sec_1 → 再添加得 sec_2）。section.id 是
 * 变更预告的 diff 稳定键（uiSchemaChangePreview 按 id 匹配分区）与变体
 * 按 id 覆盖 base 分区的键——撞名后 diff 把两个分区当同一个、变体覆盖会
 * 静默顶掉 base 分区（对标 Figma/Notion：生成 id 用单调递增，绝不依赖长度）。
 */
let secSeq = 0;
const nextSectionId = (existing: { id: string }[]): string => {
  let n: number;
  do { n = ++secSeq; } while (existing.some((s) => s.id === `sec_${n}`));
  return `sec_${n}`;
};

export const addSection = (s: BoneUiSchema, view: ViewKey = 'form'): BoneUiSchema => {
  const sections = viewOf(s, view).sections;
  const n = sections.length + 1;
  return withView(s, {
    sections: [...sections, { id: nextSectionId(sections), title: `分区 ${n}`, controls: [] }],
  }, view);
};

export const updateSection = (
  s: BoneUiSchema, view: ViewKey, idx: number, patch: Partial<Section>,
): BoneUiSchema =>
  withView(s, { sections: viewOf(s, view).sections.map((x, i) => (i === idx ? { ...x, ...patch } : x)) }, view);

export const removeSection = (s: BoneUiSchema, view: ViewKey, idx: number): BoneUiSchema =>
  withView(s, { sections: viewOf(s, view).sections.filter((_, i) => i !== idx) }, view);

export const moveSection = (s: BoneUiSchema, view: ViewKey, from: number, to: number): BoneUiSchema => {
  const arr = [...viewOf(s, view).sections];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return withView(s, { sections: arr }, view);
};

/* ============================== 控件 ============================== */

export const updateControl = (
  s: BoneUiSchema, view: ViewKey, si: number, ci: number, patch: Partial<Control>,
): BoneUiSchema =>
  withView(s, {
    sections: viewOf(s, view).sections.map((sec, i) =>
      i === si ? { ...sec, controls: sec.controls.map((c, j) => (j === ci ? { ...c, ...patch } : c)) } : sec),
  }, view);

export const addControl = (
  s: BoneUiSchema, view: ViewKey, si: number, field: MetaField | undefined,
): BoneUiSchema =>
  withView(s, {
    sections: viewOf(s, view).sections.map((sec, i) =>
      (i === si
        ? {
          ...sec,
          controls: [...sec.controls, {
            fieldCode: field?.code ?? '',
            controlKind: FIELD_TYPE_TO_KIND[(field?.type ?? '').toUpperCase()] ?? 'INPUT',
          }],
        }
        : sec)),
  }, view);

export const removeControl = (s: BoneUiSchema, view: ViewKey, si: number, ci: number): BoneUiSchema =>
  withView(s, {
    sections: viewOf(s, view).sections.map((sec, i) =>
      i === si ? { ...sec, controls: sec.controls.filter((_, j) => j !== ci) } : sec),
  }, view);

/** 分区内移动控件 */
export const moveControl = (
  s: BoneUiSchema, view: ViewKey, si: number, from: number, to: number,
): BoneUiSchema =>
  withView(s, {
    sections: viewOf(s, view).sections.map((sec, i) => {
      if (i !== si) return sec;
      const arr = [...sec.controls];
      if (to < 0 || to >= arr.length) return sec;
      const [it] = arr.splice(from, 1);
      arr.splice(to, 0, it);
      return { ...sec, controls: arr };
    }),
  }, view);

/** 跨分区移动控件（拖拽落到另一个分区） */
export const moveControlAcross = (
  s: BoneUiSchema, view: ViewKey, fromSi: number, fromCi: number, toSi: number, toCi: number,
): BoneUiSchema => {
  const sections = viewOf(s, view).sections.map((sec) => ({ ...sec, controls: [...sec.controls] }));
  if (!sections[fromSi] || !sections[toSi]) return s;
  const [it] = sections[fromSi].controls.splice(fromCi, 1);
  if (!it) return s;
  sections[toSi].controls.splice(Math.min(toCi, sections[toSi].controls.length), 0, it);
  return withView(s, { sections }, view);
};

/**
 * 复制控件（N2，对标 Retool / Appsmith 的「克隆组件」）。
 *
 * 克隆整条控件配置（kind / label / span / props / visible …），插入到原位置之后，
 * 让「做一个相似控件」从「手填一遍」变成「克隆后改两处」。
 *
 * fieldCode 始终追加 `_copyN` 后缀（剥离已有的 `_copyN` 再编号，避免
 * `amount_copy1_copy1` 这种丑名）：克隆出的控件与源是「同一配置的备份」，必须改绑到
 * 目标字段才能成为独立控件；强制唯一可避免「两个控件绑同一字段」的双绑定歧义 +
 * lint 重复码告警。克隆体 props 深拷贝，避免与原控件共享同一 props 引用
 * （曾实测：浅拷贝导致改一个另一个跟着变）。
 */
export const duplicateControl = (
  s: BoneUiSchema, view: ViewKey, si: number, ci: number,
): BoneUiSchema => {
  const sections = viewOf(s, view).sections.map((sec) => ({ ...sec, controls: [...sec.controls] }));
  const sec = sections[si];
  if (!sec) return s;
  const src = sec.controls[ci];
  if (!src) return s;
  const clone: Control = { ...src, props: { ...(src.props ?? {}) } };
  const baseCode = (clone.fieldCode || '').replace(/_copy\d+$/, '');
  const allCodes = new Set(sections.flatMap((x) => x.controls.map((c) => c.fieldCode)));
  let n = 1;
  while (allCodes.has(`${baseCode}_copy${n}`)) n++;
  clone.fieldCode = `${baseCode}_copy${n}`;
  sec.controls.splice(ci + 1, 0, clone);
  return withView(s, { sections }, view);
};

/** 批量删除控件（N1 多选批量，跨分区）。keys 为 `si:ci` 字符串。 */
export const batchRemoveControls = (s: BoneUiSchema, view: ViewKey, keys: string[]): BoneUiSchema => {
  const rm = new Set(keys);
  const sections = viewOf(s, view).sections.map((sec, si) => ({
    ...sec,
    controls: sec.controls.filter((_, ci) => !rm.has(`${si}:${ci}`)),
  }));
  return withView(s, { sections }, view);
};

/** 批量隐藏控件（N1 多选批量，置 visible:false，保留可恢复）。keys 为 `si:ci`。 */
export const batchHideControls = (s: BoneUiSchema, view: ViewKey, keys: string[]): BoneUiSchema => {
  const set = new Set(keys);
  const sections = viewOf(s, view).sections.map((sec, si) => ({
    ...sec,
    controls: sec.controls.map((c, ci) => (set.has(`${si}:${ci}`) ? { ...c, visible: false } : c)),
  }));
  return withView(s, { sections }, view);
};

/**
 * 批量复制控件（N1 多选批量）。
 * 同分区内按 ci 降序插入，避免「先插低位导致高位下标漂移」：
 * ci=5 先插（落到 6），再处理 ci=3（插到 4）时不影响已处理的 5→6。
 */
export const batchDuplicateControls = (s: BoneUiSchema, view: ViewKey, keys: string[]): BoneUiSchema => {
  const sorted = [...keys].map((k) => k.split(':').map(Number))
    .sort((a, b) => (a[0] - b[0]) || (b[1] - a[1]));
  let out = s;
  for (const [si, ci] of sorted) out = duplicateControl(out, view, si, ci);
  return out;
};

/* ============================== 联动规则 ============================== */

export const updateLinkage = (s: BoneUiSchema, idx: number, patch: Partial<LinkageRule>): BoneUiSchema =>
  ({ ...s, linkageRules: (s.linkageRules ?? []).map((r, i) => (i === idx ? { ...r, ...patch } : r)) });

export const addLinkage = (s: BoneUiSchema, firstField: string): BoneUiSchema =>
  ({
    ...s,
    linkageRules: [...(s.linkageRules ?? []), {
      name: '新联动规则',
      when: { fieldCode: firstField, operator: 'eq', value: '' },
      then: [],
    }],
  });

export const removeLinkage = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  ({ ...s, linkageRules: (s.linkageRules ?? []).filter((_, i) => i !== idx) });

export const moveLinkage = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.linkageRules ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, linkageRules: arr };
};

export const updateLinkageThen = (s: BoneUiSchema, ri: number, ti: number, patch: Partial<LinkageThen>): BoneUiSchema =>
  updateLinkage(s, ri, { then: (s.linkageRules?.[ri]?.then ?? []).map((t, i) => (i === ti ? { ...t, ...patch } : t)) });

export const addLinkageThen = (s: BoneUiSchema, ri: number, fieldCode: string): BoneUiSchema =>
  updateLinkage(s, ri, { then: [...(s.linkageRules?.[ri]?.then ?? []), { fieldCode, visible: true }] });

export const removeLinkageThen = (s: BoneUiSchema, ri: number, ti: number): BoneUiSchema =>
  updateLinkage(s, ri, { then: (s.linkageRules?.[ri]?.then ?? []).filter((_, i) => i !== ti) });

/* ============================== 提交校验 ============================== */

export const updateSubmit = (s: BoneUiSchema, idx: number, patch: Partial<SubmitRule>): BoneUiSchema =>
  ({ ...s, submitRules: (s.submitRules ?? []).map((r, i) => (i === idx ? { ...r, ...patch } : r)) });

export const addSubmit = (s: BoneUiSchema): BoneUiSchema =>
  ({
    ...s,
    submitRules: [...(s.submitRules ?? []), {
      name: '新校验',
      function: 'sum',
      fields: [],
      operator: 'eq',
      compare: { valueType: 'fixed', value: 0 },
      verifyType: 'ERROR',
      message: '',
    }],
  });

export const removeSubmit = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  ({ ...s, submitRules: (s.submitRules ?? []).filter((_, i) => i !== idx) });

export const moveSubmit = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.submitRules ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, submitRules: arr };
};

/* ============================== 动作条 ============================== */

export const updateAction = (s: BoneUiSchema, idx: number, patch: Partial<ActionBarItem>): BoneUiSchema =>
  ({ ...s, actionBar: (s.actionBar ?? []).map((a, i) => (i === idx ? { ...a, ...patch } : a)) });

export const addAction = (s: BoneUiSchema): BoneUiSchema =>
  ({
    ...s,
    actionBar: [...(s.actionBar ?? []), {
      actionCode: '',
      sortOrder: (s.actionBar?.length ?? 0) + 1,
    }],
  });

export const removeAction = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  ({ ...s, actionBar: (s.actionBar ?? []).filter((_, i) => i !== idx) });

/**
 * 移动动作条项（按数组下标 from → to）。
 *
 * 排序真源是 `sortOrder`（引擎 resolveVisibleActions 只按它排）。若只位移数组
 * 不动 sortOrder，「面板上移后的顺序 ≠ 运行时顺序」——双真源漂移（曾实测：
 * 把第 1 项下移后运行期排序把顺序还原）。这里位移后按新位置重排 sortOrder（1..N），
 * 使数组序恒等于 sortOrder 序；ActionBarPanel 亦按 sortOrder 展示，
 * 面板顺序 = 运行期顺序，从根上只有一处真源。
 */
export const moveAction = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.actionBar ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, actionBar: arr.map((a, i) => ({ ...a, sortOrder: i + 1 })) };
};

/* ============================== 列表列 ============================== */

export const updateColumn = (s: BoneUiSchema, idx: number, patch: Partial<ListColumn>): BoneUiSchema =>
  ({ ...s, views: { ...s.views, list: { ...s.views.list, columns: (s.views.list?.columns ?? []).map((c, i) => (i === idx ? { ...c, ...patch } : c)) } } });

export const addColumn = (s: BoneUiSchema, fieldCode: string): BoneUiSchema =>
  ({ ...s, views: { ...s.views, list: { ...s.views.list, columns: [...(s.views.list?.columns ?? []), { fieldCode, visible: true, width: 120 }] } } });

// 注意：不提供 removeColumn（删条目）。契约列「移除」= updateColumn(visible:false) 保留条目——
// 删条目后该列在运行时会按「契约未列出字段」重新追加到末尾（projectRuntimeColumns 语义），
// 删除动作会 silently 失效；保留 visible:false 条目才是显式、可恢复的契约决策。

export const moveColumn = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.views.list?.columns ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, views: { ...s.views, list: { ...s.views.list, columns: arr } } };
};

/**
 * 批量设置所有列显隐（对标 Salesforce List View 批量操作）。
 * 逐列 Switch 在 200 列时操作成本随列数平方增长；批量是一等公民。
 */
export const setColumnsVisible = (s: BoneUiSchema, visible: boolean): BoneUiSchema =>
  ({
    ...s,
    views: {
      ...s.views,
      list: {
        ...s.views.list,
        columns: (s.views.list?.columns ?? []).map((c) => ({ ...c, visible: visible ? undefined : false })),
      },
    },
  });

/** 批量设置所有列列宽（px；统一栅格时免逐列输入） */
export const setColumnsWidth = (s: BoneUiSchema, width: number): BoneUiSchema =>
  ({
    ...s,
    views: {
      ...s.views,
      list: {
        ...s.views.list,
        columns: (s.views.list?.columns ?? []).map((c) => ({ ...c, width })),
      },
    },
  });

/* ============================== 列表视图全配置（协议 §3.2 / v1.1） ============================== */

/** 列表视图补默认值（views.list 可整块缺省，编辑前先补空壳） */
const listOf = (s: BoneUiSchema): ListView => s.views.list ?? {};

const withList = (s: BoneUiSchema, l: ListView): BoneUiSchema => ({ ...s, views: { ...s.views, list: l } });

/** 列表视图局部补丁（tabs/searchFields/dataScope/stats/… 共用的单一入口） */
export const updateList = (s: BoneUiSchema, patch: Partial<ListView>): BoneUiSchema =>
  withList(s, { ...listOf(s), ...patch });

export const addTab = (s: BoneUiSchema): BoneUiSchema =>
  updateList(s, { tabs: [...(listOf(s).tabs ?? []), { title: '新页签', fieldCode: 'status', value: '' }] });

export const updateTab = (s: BoneUiSchema, idx: number, patch: Partial<ListTab>): BoneUiSchema =>
  updateList(s, { tabs: (listOf(s).tabs ?? []).map((t, i) => (i === idx ? { ...t, ...patch } : t)) });

export const removeTab = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  updateList(s, { tabs: (listOf(s).tabs ?? []).filter((_, i) => i !== idx) });

export const moveTab = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(listOf(s).tabs ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return updateList(s, { tabs: arr });
};

/** 搜索栏字段开关（searchFields 为 code 数组；空数组=不渲染搜索栏） */
export const toggleSearchField = (s: BoneUiSchema, code: string, on: boolean): BoneUiSchema => {
  const cur = listOf(s).searchFields ?? [];
  return updateList(s, { searchFields: on ? [...cur, code] : cur.filter((c) => c !== code) });
};

/** 搜索栏字段整表替换（多选组件直接给全量数组，免逐项 toggle 的中间态） */
export const toggleSearchFields = (s: BoneUiSchema, codes: string[]): BoneUiSchema =>
  updateList(s, { searchFields: codes.length ? codes : undefined });

/** 数据范围（引用 row_policies 的 rule name；空串=清除引用） */
export const setDataScope = (s: BoneUiSchema, policyCode: string): BoneUiSchema =>
  updateList(s, { dataScope: policyCode ? { policyCode } : undefined });

/** 快捷筛选开关（quickFilters 为 code 数组） */
export const toggleQuickFilter = (s: BoneUiSchema, code: string, on: boolean): BoneUiSchema => {
  const cur = listOf(s).quickFilters ?? [];
  return updateList(s, { quickFilters: on ? [...cur, code] : cur.filter((c) => c !== code) });
};

/** 快捷筛选整表替换（多选组件直接给全量数组） */
export const toggleQuickFilters = (s: BoneUiSchema, codes: string[]): BoneUiSchema =>
  updateList(s, { quickFilters: codes.length ? codes : undefined });

export const addStat = (s: BoneUiSchema): BoneUiSchema =>
  updateList(s, { stats: [...(listOf(s).stats ?? []), { title: '新指标', valueRef: '' }] });

export const updateStat = (s: BoneUiSchema, idx: number, patch: Partial<ListStat>): BoneUiSchema =>
  updateList(s, { stats: (listOf(s).stats ?? []).map((t, i) => (i === idx ? { ...t, ...patch } : t)) });

export const removeStat = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  updateList(s, { stats: (listOf(s).stats ?? []).filter((_, i) => i !== idx) });

export const setViewKind = (s: BoneUiSchema, viewKind: string): BoneUiSchema =>
  updateList(s, { viewKind: viewKind || undefined });

export const setDegradedBanner = (s: BoneUiSchema, banner: { when: string; text: string } | undefined): BoneUiSchema =>
  updateList(s, { degradedBanner: banner && banner.text.trim() ? { when: banner.when.trim() || 'llm_unavailable', text: banner.text.trim() } : undefined });

/** 批量动作引用整表替换（batchActions 为 actionCode 数组） */
export const setBatchActions = (s: BoneUiSchema, codes: string[]): BoneUiSchema =>
  updateList(s, { batchActions: codes.length ? codes : undefined });

/** 批量动作引用开关（batchActions 为 actionCode 数组） */
export const toggleBatchAction = (s: BoneUiSchema, code: string, on: boolean): BoneUiSchema => {
  const cur = listOf(s).batchActions ?? [];
  return updateList(s, { batchActions: on ? [...cur, code] : cur.filter((c) => c !== code) });
};

/* ============================== 页头 / 合规条 / i18n / 模板指针 ============================== */

/** 表单页头字段条（v1.2 §13.2：与 sections 互斥，引用必须存在于某分区控件） */
export const setPageHeader = (s: BoneUiSchema, view: ViewKey, codes: string[]): BoneUiSchema =>
  withView(s, { ...viewOf(s, view), pageHeader: codes.length ? codes : undefined }, view);

/** 常驻合规条（v1.1 §10.2-8：免责声明 + AI 标识 + 模拟数据标注） */
export const updateFooter = (s: BoneUiSchema, patch: Partial<SchemaFooter>): BoneUiSchema =>
  ({ ...s, footer: { ...(s.footer ?? {}), ...patch } });

/** i18n 命名空间（实例化时服务端重写为 bone.ui.<entityCode>，防多实例词条串味） */
export const setI18nNamespace = (s: BoneUiSchema, namespace: string): BoneUiSchema =>
  ({ ...s, i18n: namespace ? { namespace } : undefined });

/** 模板跟随指针（pinned 默认不提示；notify 有新版时建模 Tab 出横幅，仍须租户确认） */
export const updateBase = (s: BoneUiSchema, patch: Partial<SchemaBase>): BoneUiSchema =>
  ({ ...s, base: { ...(s.base ?? {}), ...patch } });

/* ============================== 字典覆写 ============================== */

export interface DictOverride {
  labelOverride?: Record<string, string>;
  hidden?: string[];
}

export function dictOf(s: BoneUiSchema, key: string): DictOverride | undefined {
  return (s.dictionaries?.[key] as DictOverride | undefined) ?? undefined;
}

export function setDictLabel(s: BoneUiSchema, key: string, value: string, label: string): BoneUiSchema {
  const dict = { ...(s.dictionaries ?? {}) };
  const cur = { ...(dict[key] as DictOverride | undefined) };
  const lo = { ...(cur.labelOverride ?? {}) };
  if (label) lo[value] = label;
  else delete lo[value];
  cur.labelOverride = lo;
  dict[key] = cur;
  return { ...s, dictionaries: dict };
}

export function setDictHidden(s: BoneUiSchema, key: string, value: string, hidden: boolean): BoneUiSchema {
  const dict = { ...(s.dictionaries ?? {}) };
  const cur = { ...(dict[key] as DictOverride | undefined) };
  const h = [...(cur.hidden ?? [])];
  const idx = h.indexOf(value);
  if (hidden && idx < 0) h.push(value);
  if (!hidden && idx >= 0) h.splice(idx, 1);
  cur.hidden = h;
  dict[key] = cur;
  return { ...s, dictionaries: dict };
}

export function clearDict(s: BoneUiSchema, key: string): BoneUiSchema {
  const dict = { ...(s.dictionaries ?? {}) };
  delete dict[key];
  return { ...s, dictionaries: dict };
}

/* ============================== 派生查询 ============================== */

/** 视图内已使用的字段 code 集合 */
export function usedFieldCodes(s: BoneUiSchema, view: ViewKey = 'form'): Set<string> {
  return new Set(viewOf(s, view).sections.flatMap((sec) => sec.controls.map((c) => c.fieldCode)));
}

/** 尚未出现在该视图中的字段（调色板数据源） */
export function unusedFields(s: BoneUiSchema, fields: MetaField[], view: ViewKey = 'form'): MetaField[] {
  const used = usedFieldCodes(s, view);
  return fields.filter((f) => !used.has(f.code));
}

/** 生成默认 schema（每个字段一个控件，全部落在单一分区） */
export function buildDefaultSchema(entityCode: string, fields: MetaField[]): BoneUiSchema {
  return {
    schemaVersion: 1,
    minor: 1,
    entityCode,
    displayModes: ['create', 'edit', 'view'],
    views: {
      list: { columns: fields.map((f) => ({ fieldCode: f.code, visible: true, width: 120 })) },
      form: {
        sections: [{
          id: 'sec_main',
          title: '基本信息',
          controls: fields.map((f) => ({
            fieldCode: f.code,
            controlKind: FIELD_TYPE_TO_KIND[(f.type ?? '').toUpperCase()] ?? 'INPUT',
          })),
        }],
      },
    },
    linkageRules: [],
    submitRules: [],
    actionBar: [],
  };
}

/* ============================== 变体（v1.1） ============================== */

/**
 * 变体裁决语义（engine `resolveVariant`）：命中 priority 最小（缺省 100）且维度全匹配的变体，
 * views/linkageRules/submitRules/actionBar **按块整体替换** base（未声明的块回落 base）；
 * 空 match（{}）视为全匹配兜底。同 priority 取声明顺序。
 * 创作侧口径必须与裁决侧一致，否则「预览看到的 ≠ 运行时得到的」。
 */
export const addVariant = (s: BoneUiSchema): BoneUiSchema => ({
  ...s,
  variants: [...(s.variants ?? []), {
    key: `variant_${(s.variants?.length ?? 0) + 1}`,
    label: '新变体',
    priority: 10,
    match: { roles: [] },
    views: { form: { sections: [] } },
  }],
});

export const updateVariant = (s: BoneUiSchema, idx: number, patch: Partial<Variant>): BoneUiSchema => ({
  ...s,
  variants: (s.variants ?? []).map((v, i) => (i === idx ? { ...v, ...patch } : v)),
});

export const removeVariant = (s: BoneUiSchema, idx: number): BoneUiSchema => ({
  ...s,
  variants: (s.variants ?? []).filter((_, i) => i !== idx),
});

/** 变体分区按 id 覆盖 base 同名分区、新 id 追加尾部（块级替换，与 resolveVariant 口径一致） */
const mergeSections = (base: Section[] = [], over: Section[] = []): Section[] => {
  const out = [...base];
  const idIdx = new Map(out.map((sec, i) => [sec.id, i]));
  for (const vs of over) {
    const at = idIdx.get(vs.id);
    if (at != null) out[at] = vs;
    else { out.push(vs); idIdx.set(vs.id, out.length - 1); }
  }
  return out;
};

/**
 * 变体合入基础模板（原型 FR-07 语义）：
 * - views：变体分区按 id 覆盖 base 同名分区，新 id 追加尾部；
 * - linkageRules/submitRules/actionBar：变体声明了才覆盖 base（未声明不抹掉 base 配置）；
 * - 合入后移除该变体。误操作可在设计器内 Ctrl+Z 撤销（编辑历史在 Designer 层）。
 */
export const mergeVariantIntoBase = (s: BoneUiSchema, idx: number): BoneUiSchema => {
  const v = (s.variants ?? [])[idx];
  if (!v) return s;
  const views = { ...s.views };
  if (v.views?.form) views.form = { ...views.form, sections: mergeSections(views.form?.sections, v.views.form.sections) };
  if (v.views?.detail) views.detail = { ...views.detail, sections: mergeSections(views.detail?.sections, v.views.detail.sections) };
  if (v.views?.list) views.list = { ...views.list, ...v.views.list };
  return {
    ...s,
    views,
    linkageRules: v.linkageRules ?? s.linkageRules,
    submitRules: v.submitRules ?? s.submitRules,
    actionBar: v.actionBar ?? s.actionBar,
    variants: (s.variants ?? []).filter((_, i) => i !== idx),
  };
};
