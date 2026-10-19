/**
 * 前端协议一致性自检。
 *
 * 定位：保存前的"快速反馈"，不替代后端校验（后端 validate 才是发布闸门）。
 * 与后端 UI-REF-xx 码的关系：前端报的是**本地可判**的问题（引用悬空、配置缺失、props 越界），
 * 后端报的是**跨真源**的问题（字段是否真存在、动作码是否注册）。两者互补，不重叠。
 *
 * 每条带 `locate`，点击可跳到配置面板对应位置 —— 自检从"告诉你有问题"升级为"带你到问题那里"。
 *
 * 覆盖范围（v1.2 补齐）：基础模板 + **每个变体**（变体是"换身份才暴露"的高危区，
 * 曾只扫 base，变体里写错字段码/配空 then 都能存能发布——配置型产品的校验必须
 * 覆盖所有生效分支，对标 Salesforce Flow 冲突扫描 / Appsmith action 链环检测）。
 */
import { parseEnumValues } from '@bone/shared-types';
import type { BoneUiSchema, LinkageRule, Section, SubmitRule, ActionBarItem, UiCondition } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { FN_NEEDS_COMPARE, PROPS_PENDING_REVIEW, PROP_WHITELIST } from './constants';

export type LintLevel = 'error' | 'warning' | 'info';

export interface LintLocate {
  tab: 'structure' | 'rules' | 'actions' | 'list' | 'dict' | 'variants';
  /** 分区下标 / 控件下标 */
  si?: number;
  ci?: number;
  /** 规则下标 / then 下标 */
  ri?: number;
  ti?: number;
  /** 列下标 / 动作下标 */
  idx?: number;
  /** 页头条目下标（pageHeader） */
  pi?: number;
  /** 字典键 */
  key?: string;
  /** 变体内问题：变体下标（VariantsPanel 据此高亮对应卡片；base 模板不设） */
  vi?: number;
}

export interface LintIssue {
  level: LintLevel;
  message: string;
  locate?: LintLocate;
}

/**
 * 自检作用域：base 或某个变体。
 * label 进问题文案（「变体 X：…」），vi 进 locate（点击跳到该变体卡片）。
 */
interface Scope {
  label?: string;
  vi?: number;
}

const pre = (scope: Scope, msg: string): string => (scope.label ? `${scope.label}：${msg}` : msg);

/**
 * 定位面板口径：变体作用域（vi 有值）的问题统一落 `variants` 面板——
 * 否则点击自检跳到基础模板的 structure/rules 面板，vi 丢失、用户找不到变体在哪。
 */
const scopeTab = (scope: Scope, base: LintLocate['tab']): LintLocate['tab'] => (scope.vi != null ? 'variants' : base);

/** 递归收集条件里引用的字段 */
function condFields(c: UiCondition | undefined, out: string[] = []): string[] {
  if (!c) return out;
  if (c.all) c.all.forEach((x) => condFields(x, out));
  if (c.any) c.any.forEach((x) => condFields(x, out));
  if (c.not) condFields(c.not, out);
  if (c.fieldCode) out.push(c.fieldCode);
  if (c.valueType === 'field' && c.compareField) out.push(c.compareField);
  return out;
}

/* ============================== 分区结构 ============================== */

function lintSections(
  sections: Section[],
  knownFields: Set<string>,
  scope: Scope,
  issues: LintIssue[],
): Map<string, LintLocate> {
  const seen = new Map<string, LintLocate>();
  const tab = scopeTab(scope, 'structure');
  const withVi = (l: LintLocate): LintLocate => (scope.vi != null ? { ...l, vi: scope.vi } : l);
  sections.forEach((sec, si) => {
    if (!sec.controls?.length) {
      issues.push({ level: 'warning', message: pre(scope, `分区「${sec.title || sec.id}」没有任何控件`), locate: withVi({ tab, si }) });
    }
    (sec.controls ?? []).forEach((c, ci) => {
      const at: LintLocate = withVi({ tab, si, ci });

      if (!c.fieldCode) {
        issues.push({ level: 'error', message: pre(scope, `分区「${sec.title || sec.id}」第 ${ci + 1} 个控件未绑定字段`), locate: at });
      } else if (c.virtual) {
        // 协议 v1.2：STAT/CHART/TIMELINE/COMPUTED 虚拟控件不是 meta_field 引用（经 dataRef/lineage 解析），
        // 不做实体字段存在性校验；但 fieldCode 禁与真实字段码碰撞（否则联动/字典会误命中）。
        if (knownFields.has(c.fieldCode)) {
          issues.push({ level: 'error', message: pre(scope, `虚拟控件 ${c.fieldCode} 与实体真实字段码碰撞（协议禁止）`), locate: at });
        }
      } else {
        if (seen.has(c.fieldCode)) {
          issues.push({ level: 'error', message: pre(scope, `字段 ${c.fieldCode} 在表单中重复出现，联动将互相覆盖`), locate: at });
        } else {
          seen.set(c.fieldCode, at);
        }
        if (!knownFields.has(c.fieldCode)) {
          issues.push({ level: 'error', message: pre(scope, `控件引用了实体中不存在的字段 ${c.fieldCode}`), locate: at });
        }
      }

      Object.keys(c.props ?? {}).forEach((k) => {
        if (PROPS_PENDING_REVIEW.includes(k)) {
          issues.push({ level: 'warning', message: pre(scope, `控件 ${c.fieldCode} 用了 props.${k}（v1.2 待评审，尚未进协议白名单）`), locate: at });
        } else if (!(PROP_WHITELIST as readonly string[]).includes(k)) {
          issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.${k} 不在协议白名单（schema.json additionalProperties=false，越界属性运行时不做保证）`), locate: at });
        }
      });

      /* ---------- N4：控件级校验规则 props 格式（创作侧编辑器配套，写入前即时反馈） ---------- */
      const kindN = (c.controlKind ?? 'INPUT').toUpperCase();
      const isNumeric = kindN === 'NUMBER' || kindN === 'MONEY' || kindN === 'SLIDER';
      const isText = kindN === 'INPUT' || kindN === 'TEXTAREA';
      const cp = c.props ?? {};
      if (cp.maxLength !== undefined) {
        const ml = Number(cp.maxLength);
        if (!Number.isInteger(ml) || ml < 1) {
          issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.maxLength 必须为 ≥1 的整数`), locate: at });
        } else if (!isText) {
          issues.push({ level: 'warning', message: pre(scope, `控件 ${c.fieldCode} 是 ${c.controlKind ?? 'INPUT'}，maxLength 仅对文本型（INPUT/TEXTAREA）生效`), locate: at });
        }
      }
      if (cp.min !== undefined || cp.max !== undefined) {
        if (!isNumeric) {
          issues.push({ level: 'warning', message: pre(scope, `控件 ${c.fieldCode} 是 ${c.controlKind ?? 'INPUT'}，min/max 仅对数值型（NUMBER/MONEY/SLIDER）生效`), locate: at });
        } else {
          const mn = Number(cp.min);
          const mx = Number(cp.max);
          if (cp.min !== undefined && Number.isNaN(mn)) issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.min 必须为数字`), locate: at });
          if (cp.max !== undefined && Number.isNaN(mx)) issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.max 必须为数字`), locate: at });
          if (!Number.isNaN(mn) && !Number.isNaN(mx) && mn > mx) issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.min(${mn}) 不能大于 max(${mx})`), locate: at });
        }
      }
      if (typeof cp.pattern === 'string' && cp.pattern.trim()) {
        if (!isText) {
          issues.push({ level: 'warning', message: pre(scope, `控件 ${c.fieldCode} 是 ${c.controlKind ?? 'INPUT'}，pattern 仅对文本型（INPUT/TEXTAREA）生效`), locate: at });
        }
        try {
          // 仅做编译校验（不消费结果）；编译失败 = 建模者正则写错，报 error 而非静默放行
          new RegExp(cp.pattern);
        } catch {
          issues.push({ level: 'error', message: pre(scope, `控件 ${c.fieldCode} 的 props.pattern 不是合法正则：${cp.pattern}`), locate: at });
        }
      }
    });
  });
  return seen;
}

/* ============================== 页头关键信息条 ============================== */

function lintPageHeaderView(
  view: { sections?: { controls?: { fieldCode: string }[] }[]; pageHeader?: string[] } | undefined,
  viewName: string,
  scope: Scope,
  issues: LintIssue[],
): void {
  const inSections = new Set((view?.sections ?? []).flatMap((sec) => (sec.controls ?? []).map((c) => c.fieldCode)));
  const used = new Set<string>();
  const tab = scopeTab(scope, 'structure');
  const withVi = (l: LintLocate): LintLocate => (scope.vi != null ? { ...l, vi: scope.vi } : l);
  (view?.pageHeader ?? []).forEach((code, pi) => {
    const at: LintLocate = withVi({ tab, pi });
    if (!code) {
      issues.push({ level: 'error', message: pre(scope, `${viewName}页头第 ${pi + 1} 项为空字段码`), locate: at });
      return;
    }
    if (used.has(code)) {
      issues.push({ level: 'warning', message: pre(scope, `${viewName}页头重复引用字段 ${code}`), locate: at });
    }
    used.add(code);
    if (!inSections.has(code)) {
      issues.push({
        level: 'error',
        message: pre(scope, `${viewName}页头引用了不在任何分区控件中的字段 ${code}（悬空引用：协议 v1.2 §13.2 要求页头字段必须同时存在于 sections）`),
        locate: at,
      });
    }
  });
}

/* ============================== 列表视图 ============================== */

function lintListView(list: BoneUiSchema['views']['list'], knownFields: Set<string>, scope: Scope, issues: LintIssue[]): void {
  if (!list) return;
  const tab = scopeTab(scope, 'list');
  const at = (idx?: number): LintLocate => ({ tab, ...(idx != null ? { idx } : {}), ...(scope.vi != null ? { vi: scope.vi } : {}) });

  (list.columns ?? []).forEach((col, idx) => {
    if (!col.fieldCode) {
      issues.push({ level: 'error', message: pre(scope, `列表第 ${idx + 1} 列未绑定字段`), locate: at(idx) });
    } else if (!knownFields.has(col.fieldCode)) {
      issues.push({ level: 'info', message: pre(scope, `列表列 ${col.fieldCode} 不在实体字段中（聚合/关联列属正常）`), locate: at(idx) });
    }
  });
  (list.searchFields ?? []).forEach((fc, idx) => {
    if (!fc) {
      issues.push({ level: 'error', message: pre(scope, `搜索栏第 ${idx + 1} 项为空字段码`), locate: at(idx) });
    } else if (!knownFields.has(fc)) {
      issues.push({ level: 'info', message: pre(scope, `搜索字段 ${fc} 不在实体字段中（关联搜索属正常）`), locate: at(idx) });
    }
  });
  (list.tabs ?? []).forEach((t, idx) => {
    if (!t.fieldCode) {
      issues.push({ level: 'error', message: pre(scope, `列表页签第 ${idx + 1} 个未绑定字段`), locate: at(idx) });
    } else if (!knownFields.has(t.fieldCode)) {
      // 页签是运行时硬过滤（fieldCode=value ⇒ WHERE 条件），绑不存在的字段 ⇒ 该页签恒空、
      // 用户"点了没数据"——与表单控件悬空同属字段引用类硬错误；区别于列表列/搜索字段的悬空
      // （聚合列/关联搜索属正常，保持 info 级）。后端 checkListView 同口径拦截（UI-REF-01），
      // 对账矩阵见 UiSchemaRuleParityTest。
      issues.push({ level: 'error', message: pre(scope, `列表页签「${t.title ?? t.titleKey ?? idx + 1}」绑定了实体中不存在的字段 ${t.fieldCode}`), locate: at(idx) });
    }
    if (t.title == null && t.titleKey == null) {
      issues.push({ level: 'warning', message: pre(scope, `列表页签第 ${idx + 1} 个既无 title 也无 titleKey，运行时将显示空页签`), locate: at(idx) });
    }
  });
  (list.quickFilters ?? []).forEach((code, idx) => {
    if (!code) {
      issues.push({ level: 'error', message: pre(scope, `快捷筛选第 ${idx + 1} 项为空`), locate: at(idx) });
    }
  });
  (list.batchActions ?? []).forEach((code, idx) => {
    if (!code) {
      issues.push({ level: 'error', message: pre(scope, `批量动作第 ${idx + 1} 项为空 actionCode`), locate: at(idx) });
    }
  });
  (list.stats ?? []).forEach((st, idx) => {
    if (!st.valueRef) {
      issues.push({ level: 'warning', message: pre(scope, `统计卡「${st.title ?? st.titleKey ?? idx + 1}」未引用聚合口径 valueRef（UI 不自算，后端聚合端点提供）`), locate: at(idx) });
    }
    if (st.title == null && st.titleKey == null) {
      issues.push({ level: 'warning', message: pre(scope, `统计卡第 ${idx + 1} 个既无 title 也无 titleKey`), locate: at(idx) });
    }
  });
  if (list.dataScope && !list.dataScope.policyCode) {
    issues.push({ level: 'error', message: pre(scope, '数据范围声明了但 policyCode 为空（后端 WHERE 强制需要明确的 row_policies code）'), locate: at() });
  }
  if (list.degradedBanner && !list.degradedBanner.text.trim()) {
    issues.push({ level: 'warning', message: pre(scope, '降级横幅 text 为空，依赖不可用时用户看不到任何提示'), locate: at() });
  }
  if (list.viewKind && list.viewKind !== 'table' && list.viewKind !== 'kanban' && list.viewKind !== 'calendar') {
    issues.push({ level: 'warning', message: pre(scope, `视图形态 ${list.viewKind} 不在协议 enum（table/kanban/calendar），运行时前向忽略并按表格渲染`), locate: at() });
  }
}

/* ============================== 规则集（联动 / 校验 / 动作条） ============================== */

interface RuleSet {
  linkageRules?: LinkageRule[];
  submitRules?: SubmitRule[];
  actionBar?: ActionBarItem[];
}

/**
 * 赋值联动成环检测（引擎 linkageState 单轮收敛，环上的赋值不生效）：
 * 边 = 规则读取字段（when/compareField）→ 写入字段（then.value）。
 * DFS 找环，报出环路径（如 X→Y→X）。
 */
function detectAssignCycles(rules: LinkageRule[], scope: Scope, issues: LintIssue[]): void {
  const writers = new Map<string, { reads: string[]; tag: string }[]>();
  rules.forEach((r, ri) => {
    if (r.enabled === false) return;
    const reads = condFields(r.when);
    (r.then ?? []).forEach((t) => {
      if (t.value === undefined || t.value === null || !t.fieldCode) return;
      const list = writers.get(t.fieldCode) ?? [];
      list.push({ reads, tag: r.code || r.name || `规则${ri + 1}` });
      writers.set(t.fieldCode, list);
    });
  });
  if (!writers.size) return;

  const state = new Map<string, 'visiting' | 'done'>();
  const stack: string[] = [];
  const reported = new Set<string>();

  const dfs = (f: string): void => {
    const st = state.get(f);
    if (st === 'done') return;
    if (st === 'visiting') {
      const from = stack.indexOf(f);
      const cycle = [...stack.slice(from), f].join(' → ');
      if (!reported.has(cycle)) {
        reported.add(cycle);
        issues.push({
          level: 'warning',
          message: pre(scope, `赋值联动存在环 ${cycle}（引擎单轮收敛，环上的赋值不会按预期逐级生效，建议改为单向流水）`),
          locate: { tab: scopeTab(scope, 'rules'), ...(scope.vi != null ? { vi: scope.vi } : {}) },
        });
      }
      return;
    }
    state.set(f, 'visiting');
    stack.push(f);
    for (const w of writers.get(f) ?? []) for (const r of w.reads) dfs(r);
    stack.pop();
    state.set(f, 'done');
  };
  for (const f of writers.keys()) dfs(f);
}

function lintRuleSet(
  { linkageRules = [], submitRules = [], actionBar = [] }: RuleSet,
  formCodes: Set<string>,
  knownFields: Set<string>,
  scope: Scope,
  issues: LintIssue[],
): void {
  // 定位口径：联动/校验 → 规则面板第 ri 条；动作条 → 动作条面板第 idx 项（tab 必传）。
  // 变体作用域统一落 variants 面板（点击自检跳到变体卡片，vi 不丢）。
  const loc = (extra: LintLocate): LintLocate => ({
    ...extra,
    tab: scopeTab(scope, extra.tab),
    ...(scope.vi != null ? { vi: scope.vi } : {}),
  });

  /* ---------- 联动规则 ---------- */
  const codeCount = new Map<string, number>();
  linkageRules.forEach((r) => { const c = r.code?.trim(); if (c) codeCount.set(c, (codeCount.get(c) ?? 0) + 1); });
  // 多规则写同一字段（引擎按顺序后者覆盖前者）：静默互相覆盖是高发误配
  const writers = new Map<string, number>();
  linkageRules.forEach((r) => (r.then ?? []).forEach((t) => { if (t.fieldCode) writers.set(t.fieldCode, (writers.get(t.fieldCode) ?? 0) + 1); }));

  linkageRules.forEach((r, ri) => {
    const at = loc({ tab: 'rules', ri });
    const tag = r.code || r.name || `规则${ri + 1}`;

    if (!r.when || Object.keys(r.when).length === 0) {
      issues.push({ level: 'warning', message: pre(scope, `联动「${tag}」没有 when 条件，将始终命中`), locate: at });
    }
    if (!r.then?.length) {
      issues.push({ level: 'error', message: pre(scope, `联动「${tag}」没有 then 动作，配置后无任何效果`), locate: at });
    }
    if (r.code?.trim() && (codeCount.get(r.code.trim()) ?? 0) > 1) {
      issues.push({ level: 'warning', message: pre(scope, `联动「${tag}」编码与其他规则重复（前后端对账将无法区分，建议 code 全局唯一）`), locate: at });
    }

    condFields(r.when).forEach((fc) => {
      if (fc && !formCodes.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: pre(scope, `联动「${tag}」的 when 引用了不存在的字段 ${fc}`), locate: at });
      }
    });

    (r.then ?? []).forEach((t, ti) => {
      if (!t.fieldCode) {
        issues.push({ level: 'error', message: pre(scope, `联动「${tag}」第 ${ti + 1} 个动作未指定字段`), locate: loc({ tab: 'rules', ri, ti }) });
      } else {
        if (!formCodes.has(t.fieldCode) && !knownFields.has(t.fieldCode)) {
          issues.push({ level: 'warning', message: pre(scope, `联动「${tag}」作用于表单外的字段 ${t.fieldCode}，运行时看不到效果`), locate: loc({ tab: 'rules', ri, ti }) });
        }
        if ((writers.get(t.fieldCode) ?? 0) > 1) {
          issues.push({ level: 'warning', message: pre(scope, `字段 ${t.fieldCode} 被 ${writers.get(t.fieldCode)} 条联动规则同时写入（按顺序后者覆盖前者，确认是否有意分层）`), locate: loc({ tab: 'rules', ri, ti }) });
        }
      }
    });

    if (r.enabled === false) {
      issues.push({ level: 'info', message: pre(scope, `联动「${tag}」已停用（运行期不生效）`), locate: at });
    }
  });

  detectAssignCycles(linkageRules, scope, issues);

  /* ---------- 提交校验 ---------- */
  const submitCodeCount = new Map<string, number>();
  submitRules.forEach((r) => { const c = r.code?.trim(); if (c) submitCodeCount.set(c, (submitCodeCount.get(c) ?? 0) + 1); });

  submitRules.forEach((r, ri) => {
    const at = loc({ tab: 'rules', ri });
    const tag = r.code || r.name || `校验${ri + 1}`;

    if (!r.fields?.length) {
      issues.push({ level: 'error', message: pre(scope, `校验「${tag}」未指定 fields，引擎无从取数`), locate: at });
    }
    if (!r.message) {
      issues.push({ level: 'warning', message: pre(scope, `校验「${tag}」缺少 message，失败时用户看不到原因`), locate: at });
    }
    if (FN_NEEDS_COMPARE.includes(r.function) && !r.compare) {
      issues.push({ level: 'error', message: pre(scope, `校验「${tag}」的 ${r.function} 缺 compare 右值，引擎将按 fail-closed 阻断`), locate: at });
    }
    if (r.compare?.valueType === 'field' && !r.compare.fieldCode) {
      issues.push({ level: 'error', message: pre(scope, `校验「${tag}」的比较值选了「字段」但未填字段编码`), locate: at });
    }
    if (r.function === 'balance' && (r.fields?.length ?? 0) < 2) {
      issues.push({ level: 'error', message: pre(scope, `校验「${tag}」balance 需要两组字段（借/贷）`), locate: at });
    }
    if (r.code?.trim() && (submitCodeCount.get(r.code.trim()) ?? 0) > 1) {
      issues.push({ level: 'warning', message: pre(scope, `校验「${tag}」编码与其他规则重复（前后端对账将无法区分）`), locate: at });
    }

    (r.fields ?? []).forEach((fc) => {
      const root = String(fc).split('.')[0];
      if (root && !formCodes.has(root) && !knownFields.has(root)) {
        issues.push({ level: 'warning', message: pre(scope, `校验「${tag}」引用了不存在的字段 ${fc}`), locate: at });
      }
    });
    condFields(r.when).forEach((fc) => {
      if (fc && !formCodes.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: pre(scope, `校验「${tag}」的 when 引用了不存在的字段 ${fc}`), locate: at });
      }
    });

    if (r.enabled === false) {
      issues.push({ level: 'info', message: pre(scope, `校验「${tag}」已停用（运行期不生效）`), locate: at });
    }
  });

  /* ---------- 动作条 ---------- */
  const actionCodeCount = new Map<string, number>();
  actionBar.forEach((a) => { const c = a.actionCode?.trim(); if (c) actionCodeCount.set(c, (actionCodeCount.get(c) ?? 0) + 1); });

  actionBar.forEach((a, i) => {
    const at = loc({ tab: 'actions', idx: i });
    if (!a.actionCode) {
      issues.push({ level: 'error', message: pre(scope, `动作条第 ${i + 1} 项未填 actionCode`), locate: at });
    } else if ((actionCodeCount.get(a.actionCode.trim()) ?? 0) > 1) {
      issues.push({ level: 'warning', message: pre(scope, `动作 ${a.actionCode} 在动作条中重复出现`), locate: at });
    }
    condFields(a.visibleWhen).forEach((fc) => {
      if (fc && !formCodes.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: pre(scope, `动作 ${a.actionCode || i + 1} 的门控引用了不存在的字段 ${fc}`), locate: at });
      }
    });
    // HITL 人工决策配置完整性（v1.2 §13.3）：decisionKind=hitl 必须有理由类型，否则工作台无理由可填
    if (a.decisionKind === 'hitl') {
      if (!a.reasonTypes?.length) {
        issues.push({ level: 'warning', message: pre(scope, `HITL 动作 ${a.actionCode || i + 1} 未配置 reasonTypes，人工决策时无理由类型可选`), locate: at });
      }
      if (a.maxRounds !== undefined && a.maxRounds < 1) {
        issues.push({ level: 'warning', message: pre(scope, `HITL 动作 ${a.actionCode || i + 1} 的 maxRounds 应 ≥ 1（当前 ${a.maxRounds}）`), locate: at });
      }
    }
  });
}

/* ============================== 主入口 ============================== */

/**
 * 执行自检。
 * @param knownFields 实体真实字段 code 集合（用于判悬空引用）
 * @param enumFields  带枚举的字段 → 可选值 code 列表（用于判字典覆写越界）
 */
export function lintSchema(
  s: BoneUiSchema,
  knownFields: Set<string>,
  enumFields: Record<string, string[]>,
): LintIssue[] {
  const issues: LintIssue[] = [];

  /* ---------- 基础模板 ---------- */
  const form = s.views?.form ?? { sections: [] };
  if (!(form.sections ?? []).length) {
    issues.push({ level: 'warning', message: '表单没有任何分区，运行时将以默认布局渲染', locate: { tab: 'structure' } });
  }
  const seen = lintSections(form.sections ?? [], knownFields, {}, issues);
  if (s.views?.detail) {
    lintSections(s.views.detail.sections ?? [], knownFields, {}, issues);
    lintPageHeaderView(s.views.detail, '详情', {}, issues);
  }
  lintPageHeaderView(form, '表单', {}, issues);
  lintListView(s.views?.list, knownFields, {}, issues);

  lintRuleSet(
    { linkageRules: s.linkageRules, submitRules: s.submitRules, actionBar: s.actionBar },
    new Set(seen.keys()), knownFields, {}, issues,
  );

  /* ---------- 变体（v1.2 补齐：此前只扫 base，变体错误可存可发布） ---------- */
  (s.variants ?? []).forEach((v, vi) => {
    const scope: Scope = { label: `变体「${v.label ?? v.key}」`, vi };
    // 只扫变体**自己声明**的块（未声明的块回落 base，由 base 作用域负责，
    // 避免同一问题在 base 与变体下重复报两遍）
    if (v.views?.form) {
      lintSections(v.views.form.sections ?? [], knownFields, scope, issues);
      lintPageHeaderView(v.views.form, '表单', scope, issues);
    }
    if (v.views?.detail) {
      lintSections(v.views.detail.sections ?? [], knownFields, scope, issues);
      lintPageHeaderView(v.views.detail, '详情', scope, issues);
    }
    if (v.views?.list) lintListView(v.views.list, knownFields, scope, issues);

    // 规则目标的「生效表单字段」口径与 resolveVariant 一致：声明了用变体的，否则回落 base
    const effForm = v.views?.form ?? s.views?.form;
    const effDetail = v.views?.detail ?? s.views?.detail;
    const codes = new Set([
      ...(effForm?.sections ?? []).flatMap((sec) => (sec.controls ?? []).map((c) => c.fieldCode)),
      ...(effDetail?.sections ?? []).flatMap((sec) => (sec.controls ?? []).map((c) => c.fieldCode)),
    ]);
    lintRuleSet(
      { linkageRules: v.linkageRules, submitRules: v.submitRules, actionBar: v.actionBar },
      codes, knownFields, scope, issues,
    );
  });

  /* ---------- 字典覆写（协议 §3.1：只引用，不新造） ---------- */
  Object.keys(s.dictionaries ?? {}).forEach((key) => {
    const at: LintLocate = { tab: 'dict', key };
    const known = enumFields[key];
    if (!known) {
      issues.push({ level: 'error', message: `dictionaries 覆写了实体中不存在的码表 ${key}`, locate: at });
      return;
    }
    const ov = s.dictionaries?.[key] as { labelOverride?: Record<string, string>; hidden?: string[] } | undefined;
    (ov?.hidden ?? []).forEach((v) => {
      if (!known.includes(v)) {
        issues.push({ level: 'error', message: `码表 ${key} 隐藏了不存在的选项 ${v}（UI 层不得新造选项）`, locate: at });
      }
    });
    Object.keys(ov?.labelOverride ?? {}).forEach((v) => {
      if (!known.includes(v)) {
        issues.push({ level: 'error', message: `码表 ${key} 改写了不存在的选项 ${v}（UI 层不得新造选项）`, locate: at });
      }
    });
    // 全隐藏 = 运行时该字段无可选值
    if (known.length && (ov?.hidden ?? []).length >= known.length) {
      issues.push({ level: 'warning', message: `码表 ${key} 的所有选项都被隐藏，该字段运行时将无值可选`, locate: at });
    }
  });

  return issues;
}

/** 从实体字段推导「带枚举的字段 → 可选值 code 列表」 */
export function enumFieldsOf(fields: MetaField[]): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  fields.forEach((f) => {
    const opts = parseEnumValues(f.enumValues);
    if (opts.length) out[f.code] = opts.map((o) => o.code);
  });
  return out;
}

/** 统计各级别数量 */
export function countLevels(issues: LintIssue[]): Record<LintLevel, number> {
  return {
    error: issues.filter((i) => i.level === 'error').length,
    warning: issues.filter((i) => i.level === 'warning').length,
    info: issues.filter((i) => i.level === 'info').length,
  };
}
