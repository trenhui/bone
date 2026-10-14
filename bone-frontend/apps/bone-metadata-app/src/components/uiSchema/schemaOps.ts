/**
 * UI Schema 不可变操作集。
 *
 * 全部为纯函数（输入 schema，输出新 schema），不持有状态、不直接 mutate。
 * 这样所有编辑天然可撤销（保留上一份引用即可），且不出现"改 A 影响 B"的隐式共享。
 */
import type {
  ActionBarItem, BoneUiSchema, Control, LinkageRule, LinkageThen, ListColumn, Section, SubmitRule,
} from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { FIELD_TYPE_TO_KIND } from './constants';

/** 视图键：form=录入/编辑表单，detail=详情视图（同结构但独立配置） */
export type ViewKey = 'form' | 'detail';

/** 取指定视图（缺省补空分区列表，避免到处判空） */
export function viewOf(s: BoneUiSchema, view: ViewKey = 'form'): { sections: Section[] } {
  return (view === 'detail' ? s.views.detail : s.views.form) ?? { sections: [] };
}

/** 写回指定视图 */
export function withView(s: BoneUiSchema, v: { sections: Section[] }, view: ViewKey = 'form'): BoneUiSchema {
  return view === 'detail'
    ? { ...s, views: { ...s.views, detail: v } }
    : { ...s, views: { ...s.views, form: v } };
}

/** 兼容旧调用：表单视图 */
export const formOf = (s: BoneUiSchema): { sections: Section[] } => viewOf(s, 'form');

/* ============================== 分区 ============================== */

export const addSection = (s: BoneUiSchema, view: ViewKey = 'form'): BoneUiSchema => {
  const n = viewOf(s, view).sections.length + 1;
  return withView(s, {
    sections: [...viewOf(s, view).sections, { id: `sec_${n}`, title: `分区 ${n}`, controls: [] }],
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

export const moveAction = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.actionBar ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, actionBar: arr };
};

/* ============================== 列表列 ============================== */

export const updateColumn = (s: BoneUiSchema, idx: number, patch: Partial<ListColumn>): BoneUiSchema =>
  ({ ...s, views: { ...s.views, list: { ...s.views.list, columns: (s.views.list?.columns ?? []).map((c, i) => (i === idx ? { ...c, ...patch } : c)) } } });

export const addColumn = (s: BoneUiSchema, fieldCode: string): BoneUiSchema =>
  ({ ...s, views: { ...s.views, list: { ...s.views.list, columns: [...(s.views.list?.columns ?? []), { fieldCode, visible: true, width: 120 }] } } });

export const removeColumn = (s: BoneUiSchema, idx: number): BoneUiSchema =>
  ({ ...s, views: { ...s.views, list: { ...s.views.list, columns: (s.views.list?.columns ?? []).filter((_, i) => i !== idx) } } });

export const moveColumn = (s: BoneUiSchema, from: number, to: number): BoneUiSchema => {
  const arr = [...(s.views.list?.columns ?? [])];
  if (to < 0 || to >= arr.length) return s;
  const [it] = arr.splice(from, 1);
  arr.splice(to, 0, it);
  return { ...s, views: { ...s.views, list: { ...s.views.list, columns: arr } } };
};

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
