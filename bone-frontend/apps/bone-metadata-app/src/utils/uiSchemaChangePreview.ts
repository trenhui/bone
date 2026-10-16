/**
 * 发布变更预告：已存草稿 vs 线上发布快照 → 人话变更清单。
 *
 * 为什么存在（对标 Figma 版本对比 / GitHub PR summary / 飞书草稿已启用）：
 * 发布是「对终端生效」的闸门，建模者需要在上线前用**业务语言**看清
 * 「这次发布会改变什么」——「会多出审批人，仅>5000 必填」，而不是
 * 字段/DDL 结构视角（那是实体结构发布面板的职责，见 schemaDiff.ts）。
 *
 * 设计纪律：
 * - 纯函数：入参两份已 parse 的 schema + 字段表，输出结构化变更项；
 *   句子由结构化项 + describe.tsx（规则类）在 UI 层组装，无双份文案真源。
 * - 真源走后端 published 快照（UiSchemaResp.publishedSchemaJson），
 *   不用前端时间戳近似（publishState.ts 的 MVP 近似仅用于详情页脏态条）。
 * - never-published（published=null）时全部按新增预告（「首发内容」）。
 * - 分区级新建/移除**折叠**其字段项（一条「新增分区（N 个字段）」而非 N 条字段），
 *   避免预告清单被字段淹没；跨分区移动识别为 modify 而非 删+增。
 */
import type { MetaField } from '../types';
import type {
  ActionBarItem, BoneUiSchema, LinkageRule, Section, SubmitRule, Variant, VariantMatch,
} from './uiSchemaEngine';
import { normJson } from './normJson';

export type ChangeGroupKey = 'form' | 'detail' | 'list' | 'linkage' | 'submit' | 'action' | 'variant';
export type ChangeKind = 'add' | 'remove' | 'modify';

export interface ChangeItem {
  group: ChangeGroupKey;
  kind: ChangeKind;
  /** 稳定键：字段 code / 规则 code·name / 动作码 / 变体 key / 分区 id */
  key: string;
  /** 人话文本（字段/列/分区/变体类；规则类留空，由 UI 用 describe.tsx 组句） */
  text?: string;
  /** 规则类载荷（联动/提交校验/动作条），供 UI 渲染自然语言句子 */
  rule?: LinkageRule | SubmitRule | ActionBarItem;
}

export interface ChangeGroup {
  key: ChangeGroupKey;
  label: string;
  items: ChangeItem[];
}

export interface ChangePreview {
  groups: ChangeGroup[];
  total: number;
  /** 一行摘要（tooltip 用），如「表单 +1 ~1、联动规则 +1」；无变更为空串 */
  summary: string;
}

export const GROUP_LABEL: Record<ChangeGroupKey, string> = {
  form: '录入表单',
  detail: '详情视图',
  list: '列表',
  linkage: '联动规则',
  submit: '提交校验',
  action: '动作条',
  variant: '角色变体',
};

const EMPTY: ChangePreview = { groups: [], total: 0, summary: '' };

/* ============================== 视图（表单/详情）diff ============================== */

interface FlatControl {
  sectionId: string;
  control: Section['controls'][number];
}

function secTitle(s: Section | undefined): string {
  return s?.title || s?.id || '（未命名分区）';
}

function flattenControls(secs: Section[]): FlatControl[] {
  const out: FlatControl[] = [];
  for (const s of secs) for (const c of s.controls ?? []) out.push({ sectionId: s.id, control: c });
  return out;
}

function ctrlLabel(code: string, fields: MetaField[]): string {
  const f = fields.find((x) => x.code === code);
  return f?.displayName || code;
}

function diffView(
  viewKey: 'form' | 'detail',
  draft: BoneUiSchema,
  published: BoneUiSchema | null,
  fields: MetaField[],
  items: ChangeItem[],
): void {
  const dSecs = draft.views?.[viewKey]?.sections ?? [];
  const pSecs = published?.views?.[viewKey]?.sections ?? [];
  const dById = new Map(dSecs.map((s) => [s.id, s]));
  const pById = new Map(pSecs.map((s) => [s.id, s]));

  /** 被分区级条目折叠的字段 code（新建/移除分区内的字段不再单列） */
  const folded = new Set<string>();

  // 分区新建 / 移除（按 id 匹配；id 是结构稳定键）
  for (const s of dSecs) {
    if (pById.has(s.id)) continue;
    const n = (s.controls ?? []).length;
    const names = (s.controls ?? []).map((c) => ctrlLabel(c.fieldCode, fields)).slice(0, 4).join('、');
    items.push({
      group: viewKey, kind: 'add', key: `section:${s.id}`,
      text: `新增分区「${secTitle(s)}」（${n} 个字段${names ? `：${names}${n > 4 ? ' 等' : ''}` : ''}）`,
    });
    for (const c of s.controls ?? []) folded.add(c.fieldCode);
  }
  for (const s of pSecs) {
    if (dById.has(s.id)) continue;
    const n = (s.controls ?? []).length;
    items.push({
      group: viewKey, kind: 'remove', key: `section:${s.id}`,
      text: `移除分区「${secTitle(s)}」（${n} 个字段）`,
    });
    for (const c of s.controls ?? []) folded.add(c.fieldCode);
  }
  // 分区标题修改
  for (const s of dSecs) {
    const ps = pById.get(s.id);
    if (ps && secTitle(s) !== secTitle(ps)) {
      items.push({
        group: viewKey, kind: 'modify', key: `section:${s.id}`,
        text: `分区「${secTitle(ps)}」标题改为「${secTitle(s)}」`,
      });
    }
  }

  // 控件全局 diff（按 fieldCode 匹配；跨分区移动 = modify，不是 删+增）
  const dCtrls = flattenControls(dSecs);
  const pCtrls = flattenControls(pSecs);
  const pByCode = new Map(pCtrls.map((x) => [x.control.fieldCode, x]));
  const dByCode = new Map(dCtrls.map((x) => [x.control.fieldCode, x]));

  for (const x of dCtrls) {
    const code = x.control.fieldCode;
    if (folded.has(code)) continue;
    const nm = `「${ctrlLabel(code, fields)}」`;
    const px = pByCode.get(code);
    if (!px) {
      const where = `到分区「${secTitle(dById.get(x.sectionId))}」`;
      items.push({ group: viewKey, kind: 'add', key: code, text: `新增字段${nm}${where}` });
      continue;
    }
    if (px.sectionId !== x.sectionId) {
      items.push({
        group: viewKey, kind: 'modify', key: code,
        text: `${nm}从分区「${secTitle(pById.get(px.sectionId))}」移到「${secTitle(dById.get(x.sectionId))}」`,
      });
      continue;
    }
    const diffs: string[] = [];
    if ((x.control.labelOverride ?? '') !== (px.control.labelOverride ?? '')) {
      diffs.push(`显示名改为「${x.control.labelOverride || ctrlLabel(code, fields)}」`);
    }
    if ((x.control.visible === false) !== (px.control.visible === false)) {
      diffs.push(x.control.visible === false ? '设为默认隐藏' : '取消默认隐藏');
    }
    if (!!x.control.readonly !== !!px.control.readonly) {
      diffs.push(x.control.readonly ? '改为只读' : '取消只读');
    }
    const dKind = x.control.controlKind ?? 'INPUT';
    const pKind = px.control.controlKind ?? 'INPUT';
    if (dKind !== pKind) diffs.push(`控件类型 ${pKind}→${dKind}`);
    if (diffs.length) {
      items.push({ group: viewKey, kind: 'modify', key: code, text: `${nm}${diffs.join('、')}` });
    }
  }
  for (const px of pCtrls) {
    const code = px.control.fieldCode;
    if (folded.has(code) || dByCode.has(code)) continue;
    items.push({ group: viewKey, kind: 'remove', key: code, text: `移除字段「${ctrlLabel(code, fields)}」` });
  }

  // 同分区内列序调整（集合相同、顺序不同 → 一条 modify，不拆成逐字段移动）
  for (const s of dSecs) {
    const ps = pById.get(s.id);
    if (!ps) continue;
    const dc = (s.controls ?? []).map((c) => c.fieldCode);
    const pc = (ps.controls ?? []).map((c) => c.fieldCode);
    const sameSet = dc.length === pc.length && [...dc].sort().join('|') === [...pc].sort().join('|');
    if (sameSet && dc.join('|') !== pc.join('|')) {
      items.push({
        group: viewKey, kind: 'modify', key: `order:${s.id}`,
        text: `分区「${secTitle(s)}」内字段顺序调整`,
      });
    }
  }
}

/* ============================== 列表 diff ============================== */

function diffList(
  draft: BoneUiSchema,
  published: BoneUiSchema | null,
  fields: MetaField[],
  items: ChangeItem[],
): void {
  const dc = draft.views?.list?.columns ?? [];
  const pc = published?.views?.list?.columns ?? [];
  const pByCode = new Map(pc.map((c) => [c.fieldCode, c]));
  const dByCode = new Map(dc.map((c) => [c.fieldCode, c]));

  for (const c of dc) {
    const nm = `「${ctrlLabel(c.fieldCode, fields)}」`;
    const px = pByCode.get(c.fieldCode);
    if (!px) {
      const attrs = [c.width ? `宽 ${c.width}px` : '', c.pinned ? '固定左侧' : ''].filter(Boolean).join('，');
      items.push({
        group: 'list', kind: 'add', key: c.fieldCode,
        text: `新增列${nm}${attrs ? `（${attrs}）` : ''}`,
      });
      continue;
    }
    const diffs: string[] = [];
    if ((c.width ?? 0) !== (px.width ?? 0)) {
      diffs.push(`列宽 ${px.width ?? '默认'}→${c.width ?? '默认'}`);
    }
    if (!!c.pinned !== !!px.pinned) diffs.push(c.pinned ? '固定左侧' : '取消固定');
    if ((c.visible !== false) !== (px.visible !== false)) {
      diffs.push(c.visible === false ? '隐藏' : '取消隐藏');
    }
    if (diffs.length) {
      items.push({ group: 'list', kind: 'modify', key: c.fieldCode, text: `列${nm}${diffs.join('、')}` });
    }
  }
  for (const c of pc) {
    if (dByCode.has(c.fieldCode)) continue;
    items.push({ group: 'list', kind: 'remove', key: c.fieldCode, text: `移除列「${ctrlLabel(c.fieldCode, fields)}」` });
  }

  // 列序调整（集合相同、顺序不同）
  const dk = dc.map((c) => c.fieldCode);
  const pk = pc.map((c) => c.fieldCode);
  if (dk.length && dk.length === pk.length && [...dk].sort().join('|') === [...pk].sort().join('|') && dk.join('|') !== pk.join('|')) {
    items.push({ group: 'list', kind: 'modify', key: 'order', text: '列表列序调整' });
  }

  // 数据范围策略
  const ds = draft.views?.list?.dataScope?.policyCode;
  const ps = published?.views?.list?.dataScope?.policyCode;
  if (ds !== ps) {
    items.push({
      group: 'list', kind: 'modify', key: 'dataScope',
      text: ds ? `数据范围策略改为「${ds}」` : '移除数据范围策略',
    });
  }

  // 搜索字段
  const dsf = draft.views?.list?.searchFields ?? [];
  const psf = published?.views?.list?.searchFields ?? [];
  for (const f of dsf) {
    if (psf.includes(f)) continue;
    items.push({ group: 'list', kind: 'add', key: `search:${f}`, text: `搜索字段新增「${ctrlLabel(f, fields)}」` });
  }
  for (const f of psf) {
    if (dsf.includes(f)) continue;
    items.push({ group: 'list', kind: 'remove', key: `search:${f}`, text: `搜索字段移除「${ctrlLabel(f, fields)}」` });
  }
}

/* ============================== 规则 / 动作 / 变体 diff ============================== */

type RuleLike = LinkageRule | SubmitRule | ActionBarItem;

/** 规则稳定键：动作条按 actionCode、联动/校验按 code → name → 位置（协议建议规则带 code） */
function ruleKey(r: RuleLike, i: number): string {
  const anyR = r as { actionCode?: string; code?: string; name?: string };
  return anyR.actionCode || anyR.code || anyR.name || `#${i}`;
}

function ruleLabel(r: RuleLike): string {
  const anyR = r as { actionCode?: string; code?: string; name?: string };
  return anyR.name || anyR.code || anyR.actionCode || '（未命名规则）';
}

/**
 * 规则数组 diff（联动/提交校验/动作条共用）。
 * 键 = code → name → 位置。无 code/name 的规则按位置匹配：位置之前有增删会错位，
 * MVP 容忍（协议建议规则带 code；UI 层对 modify 只提示「已修改」不虚构细节）。
 */
function diffRules(
  group: ChangeGroupKey,
  dArr: RuleLike[] | undefined,
  pArr: RuleLike[] | undefined,
  items: ChangeItem[],
): void {
  const d = dArr ?? [];
  const p = pArr ?? [];
  const pByKey = new Map(p.map((x, i) => [ruleKey(x, i), x]));
  const dByKey = new Map(d.map((x, i) => [ruleKey(x, i), x]));
  d.forEach((x, i) => {
    const k = ruleKey(x, i);
    const px = pByKey.get(k);
    if (!px) {
      items.push({ group, kind: 'add', key: k, rule: x });
    } else if (normJson(x) !== normJson(px)) {
      items.push({ group, kind: 'modify', key: k, text: `规则「${ruleLabel(x)}」已修改` });
    }
  });
  p.forEach((x, i) => {
    const k = ruleKey(x, i);
    if (dByKey.has(k)) return;
    items.push({ group, kind: 'remove', key: k, rule: x });
  });
}

/** 变体命中条件 → 人类可读（单一真源：describe.tsx 的 describeVariant 与 UI 摘要都走这里） */
export function matchText(m?: VariantMatch): string {
  if (!m) return '全部身份（兜底）';
  const parts: string[] = [];
  if (m.roles?.length) parts.push(`角色∈{${m.roles.join('、')}}`);
  if (m.tiers?.length) parts.push(`档位∈{${m.tiers.join('、')}}`);
  if (m.channels?.length) parts.push(`渠道∈{${m.channels.join('、')}}`);
  if (m.devices?.length) parts.push(`设备∈{${m.devices.join('、')}}`);
  return parts.length ? parts.join(' 且 ') : '全部身份（兜底）';
}

/** 变体覆盖块统计（供人话摘要：「覆盖结构/规则/动作条/列表」） */
export function variantOverrideText(v: Variant): string {
  const blocks: string[] = [];
  const secN = (v.views?.form?.sections?.length ?? 0) + (v.views?.detail?.sections?.length ?? 0);
  if (secN) blocks.push(`结构 ${secN} 分区`);
  if (v.views?.list) blocks.push('列表');
  const ruleN = (v.linkageRules?.length ?? 0) + (v.submitRules?.length ?? 0);
  if (ruleN) blocks.push(`规则 ${ruleN} 条`);
  if (v.actionBar?.length) blocks.push(`动作条 ${v.actionBar.length} 项`);
  return blocks.length ? blocks.join('、') : '未覆盖任何块（完全沿用基础模板）';
}

function diffVariants(
  draft: BoneUiSchema,
  published: BoneUiSchema | null,
  items: ChangeItem[],
): void {
  const dv = draft.variants ?? [];
  const pv = published?.variants ?? [];
  const pByKey = new Map(pv.map((v) => [v.key, v]));
  const dByKey = new Map(dv.map((v) => [v.key, v]));
  for (const v of dv) {
    const label = v.label ?? v.key;
    const px = pByKey.get(v.key);
    if (!px) {
      items.push({
        group: 'variant', kind: 'add', key: v.key,
        text: `新增变体「${label}」（${matchText(v.match)}；${variantOverrideText(v)}）`,
      });
    } else if (normJson(v) !== normJson(px)) {
      items.push({ group: 'variant', kind: 'modify', key: v.key, text: `变体「${label}」的覆盖内容已修改` });
    }
  }
  for (const v of pv) {
    if (dByKey.has(v.key)) continue;
    items.push({ group: 'variant', kind: 'remove', key: v.key, text: `移除变体「${v.label ?? v.key}」` });
  }
}

/* ============================== 汇总 ============================== */

function summarize(groups: ChangeGroup[]): string {
  return groups
    .map((g) => {
      const a = g.items.filter((i) => i.kind === 'add').length;
      const r = g.items.filter((i) => i.kind === 'remove').length;
      const m = g.items.filter((i) => i.kind === 'modify').length;
      const seg = [a ? `+${a}` : '', r ? `-${r}` : '', m ? `~${m}` : ''].filter(Boolean).join(' ');
      return `${g.label} ${seg}`;
    })
    .join('、');
}

/**
 * 计算「发布将改变什么」。
 *
 * @param draft     已存草稿（baseline；设计器保存后的 schema）
 * @param published 线上发布快照（null=从未发布 → 全部按新增预告）
 * @param fields    实体字段表（字段 code → 显示名；无匹配回落 code）
 */
export function previewChanges(
  draft: BoneUiSchema | null,
  published: BoneUiSchema | null,
  fields: MetaField[],
): ChangePreview {
  if (!draft) return EMPTY;

  const items: ChangeItem[] = [];
  diffView('form', draft, published, fields, items);
  diffView('detail', draft, published, fields, items);
  diffList(draft, published, fields, items);
  diffRules('linkage', draft.linkageRules, published?.linkageRules, items);
  diffRules('submit', draft.submitRules, published?.submitRules, items);
  diffRules('action', draft.actionBar, published?.actionBar, items);
  diffVariants(draft, published, items);

  if (items.length === 0) return EMPTY;

  const order: ChangeGroupKey[] = ['form', 'detail', 'list', 'linkage', 'submit', 'action', 'variant'];
  const groups: ChangeGroup[] = order
    .map((key) => ({ key, label: GROUP_LABEL[key], items: items.filter((i) => i.group === key) }))
    .filter((g) => g.items.length > 0);

  return { groups, total: items.length, summary: summarize(groups) };
}
