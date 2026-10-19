/**
 * 协议驱动界面引擎（BoneUiSchema 渲染内核）。
 *
 * 移植自 `doc/design/lowcode/cases/ui-schema-samples/mock-expense-dynamic-v1.html` 的已跑测逻辑，
 * 去 DOM 依赖、补 TS 类型、补上下文变量（$me/$myteam/$all）与变体裁决。
 *
 * 设计纪律（与协议 §0/§11 一致）：
 * - 纯函数 + 无远程拉取、无缓存写；数据读写统一走 runtime 通道（渲染器负责）。
 * - linkage/submit 只在前端跑（体验拦截）；后端 `RuntimeWriteValidator` + Hook + 行策略是安全底线，不后移。
 * - 不 eval 任何字符串、不引入表达式语言；比较符/聚合函数全部白名单。
 *
 * 金额比较容差 0.005（DECIMAL(20,6) 精度口径，防 88.5+40≠128.5 浮点误伤）。
 */

/* ============================== 协议类型 ============================== */

export type Operator =
  | 'isNull' | 'isNotNull' | 'in' | 'notIn'
  | 'eq' | 'neq' | 'gt' | 'gte' | 'lt' | 'lte'
  | 'between' | 'earlierThan' | 'laterThan';

export type ControlKind =
  | 'INPUT' | 'TEXTAREA' | 'NUMBER' | 'SWITCH' | 'DATE' | 'DATETIME'
  | 'ENUM' | 'REFERENCE' | 'CHILD_TABLE' | 'ATTACHMENT' | 'MONEY'
  | 'STAT' | 'CHART' | 'TIMELINE' | 'COMPUTED' | 'QUOTE' | 'ALERT' | 'SLIDER';

export type ValueType = 'fixed' | 'field';

export interface UiCondition {
  fieldCode?: string;
  operator?: Operator;
  value?: unknown;
  valueType?: ValueType;
  compareField?: string;
  all?: UiCondition[];
  any?: UiCondition[];
  not?: UiCondition;
}

export interface Control {
  fieldCode: string;
  controlKind?: ControlKind;
  labelOverride?: string;
  labelKey?: string;
  placeholder?: string;
  /** 控件基线态（如专票的税号默认隐藏）；linkage 可覆盖 */
  visible?: boolean;
  readonly?: boolean;
  /**
   * 控件级必填覆盖（协议 v1.2 §4.3）：true 时即便元数据字段非必填，该控件也强制必填
   * （对标宜搭/PowerApps 字段必填）。与元数据必填、联动 then[].required 取并集；
   * 渲染器据此显示 * 并将空值判为错误（validateControlValues）。
   */
  required?: boolean;
  virtual?: boolean;
  props?: Record<string, unknown>;
}

export interface Section {
  id: string;
  title?: string;
  titleKey?: string;
  /**
   * 分区类型（协议 v1.1 §10.2）：form=常规字段分区（默认）；dashboard=统计卡区；
   * timeline=版本链/决策轨迹；compare=三一致对照/对账；report=数字注入报告；tasks=待办区。
   */
  kind?: 'form' | 'dashboard' | 'timeline' | 'compare' | 'report' | 'tasks';
  /**
   * 分区布局（协议 v1.1 §10.2）：single=单栏（默认）；split-left-40/split-left-60=
   * 左右分栏（采购审查「左 40% 风险面板 + 右 60% 原文」即 split-left-40）。
   */
  layout?: 'single' | 'split-left-40' | 'split-left-60';
  controls: Control[];
}

export interface LinkageThen {
  fieldCode: string;
  visible?: boolean;
  required?: boolean;
  readonly?: boolean;
  disabled?: boolean;
  notice?: string;
  /** 档位显隐（v1.1：与 schema 的 tier:string[] 同口径，渲染器按 ctx.tier 过滤控件） */
  tier?: string[];
  value?: unknown;
  /** notice 的 i18n key 形态（直通渲染器翻译，内联 notice 优先于 key 解析结果） */
  noticeKey?: string;
}

export interface LinkageRule {
  name?: string;
  code?: string;
  when: UiCondition;
  then: LinkageThen[];
  /**
   * 规则级启停（协议 v1.2 待评审）：`false` 时前端渲染与后端强制都整体跳过该条。
   * 关闭必须显式落 `false` —— `undefined` 等价启用（曾踩坑：`checked||undefined` 导致开关关不掉）。
   */
  enabled?: boolean;
}

export type SubmitFunction =
  | 'sum' | 'count' | 'max' | 'min' | 'compare'
  | 'balance' | 'unique' | 'consistency' | 'completeness';

export interface SubmitParams {
  /** 差额容差（balance/consistency；缺省 MONEY_TOLERANCE，claim 发票场景用 1） */
  tolerance?: number;
  /** 最少要素数（completeness；缺省 fields.length） */
  minReasons?: number;
  /** 查重范围（unique；前端仅做本单判重/提醒，跨单查重由后端承担，scope 透传日志） */
  scope?: string;
}

export interface SubmitRule {
  name?: string;
  code?: string;
  function: SubmitFunction;
  fields: string[];
  operator: Operator;
  /** 可选生效前置（如拒赔完整性仅 status=REJECTING 时跑；缺省全量跑。对标 Salesforce 验证规则的生效条件） */
  when?: UiCondition;
  /** sum/count/max/min/compare 必填；balance/consistency/completeness 样板均无 compare（右值来自 fields/params） */
  compare?: { valueType: ValueType; value?: unknown; fieldCode?: string };
  params?: SubmitParams;
  verifyType: 'ERROR' | 'WARNING';
  message: string;
  /** message 的 i18n key 形态（内联 message 优先于 key 解析结果） */
  messageKey?: string;
  /** 规则级启停（协议 v1.2 待评审）：`false` 时整条跳过，语义同 LinkageRule.enabled */
  enabled?: boolean;
  /** 停用原因（供审核追溯"为什么停用"，v1.2 待评审） */
  disabledReason?: string;
}

/** 动作二次确认（协议 confirm 四键白名单） */
export interface ActionConfirm {
  title?: string;
  content?: string;
  titleKey?: string;
  contentKey?: string;
}

export interface ActionBarItem {
  actionCode: string;
  visibleWhen?: UiCondition;
  sortOrder?: number;
  confirm?: ActionConfirm;
  decisionKind?: 'open' | 'hitl';
  reasonTypes?: string[];
  maxRounds?: number;
  requireDualApproveWhen?: UiCondition;
}

export interface ListColumn {
  fieldCode: string;
  visible?: boolean;
  width?: number;
  /** 固定列方向（协议 enum）：left=横向滚动钉左侧（如主键/案件号）；right=钉右侧（如操作列） */
  pinned?: 'left' | 'right';
  format?: Record<string, unknown>;
}

export interface ListTab {
  title?: string;
  titleKey?: string;
  fieldCode?: string;
  /** 协议允许 string|number|boolean（布尔 tab 如「风险标记」「已达封顶」） */
  value?: string | number | boolean;
}

export interface ListStat {
  title?: string;
  titleKey?: string;
  /** 引用聚合口径 code（由后端聚合端点提供，UI 不计算） */
  valueRef?: string;
  trend?: string;
}

export interface ListView {
  columns?: ListColumn[];
  searchFields?: string[];
  tabs?: ListTab[];
  quickFilters?: string[];
  stats?: ListStat[];
  dataScope?: { policyCode: string };
  batchActions?: string[];
  viewKind?: string;
  /**
   * 降级横幅（协议对象，非字符串）：when=命名条件（如 llm_unavailable），
   * text=横幅文案。LLM 不可用时提示规则审查模式（procuraAI 约束）。
   */
  degradedBanner?: { when: string; text: string };
}

export interface FormView {
  sections: Section[];
  /**
   * 表单页头关键信息条（协议 v1.2 §13.2）：独立于 sections 渲染在视图顶部
   * （如案件号/申请人/状态），与分区互斥。引用的字段必须同时存在于某个
   * section.controls，否则悬空引用（lint/后端 validate 均报 ERROR）。
   */
  pageHeader?: string[];
}

export interface VariantMatch {
  roles?: string[];
  tiers?: string[];
  channels?: string[];
  devices?: string[];
}

export interface Variant {
  key: string;
  label?: string;
  labelKey?: string;
  priority?: number;
  match?: VariantMatch;
  views?: { list?: ListView; form?: FormView; detail?: FormView };
  linkageRules?: LinkageRule[];
  submitRules?: SubmitRule[];
  actionBar?: ActionBarItem[];
}

/** 常驻合规条（免责声明 + AI 标识；协议 footer 四键白名单） */
export interface SchemaFooter {
  disclaimer?: string;
  disclaimerKey?: string;
  aiBadge?: boolean;
  mockDataMark?: boolean;
}

/** 模板跟随指针（实例侧只声明来源与版本，合入走租户确认式 pull，永不自动推送） */
export interface SchemaBase {
  templateCode?: string;
  baseVersion?: string;
  followMode?: 'pinned' | 'notify';
}

export interface BoneUiSchema {
  schemaVersion: number;
  /** 版本协商 minor（与 schema 的 `minor` 同名；缺省=1，前向忽略未知字段） */
  minor?: number;
  /** @deprecated 历史别名，`minor` 优先 */
  schemaMinor?: number;
  entityCode: string;
  displayModes?: string[];
  views: { list?: ListView; form?: FormView; detail?: FormView };
  linkageRules?: LinkageRule[];
  submitRules?: SubmitRule[];
  actionBar?: ActionBarItem[];
  /** 常驻合规条（footer） */
  footer?: SchemaFooter;
  dictionaries?: Record<string, unknown>;
  i18n?: { namespace?: string };
  /** 模板跟随指针（base） */
  base?: SchemaBase;
  variants?: Variant[];
}

/** 渲染上下文：解析 $me/$myteam/$all 与变体裁决 */
export interface UiContext {
  me?: string;
  myTeam?: string[];
  all?: string;
  roles?: string[];
  tier?: string;
  channel?: string;
  device?: string;
}

/** 单字段的 UI 动态态（linkage 输出） */
export interface FieldUiState {
  visible: boolean;
  required: boolean;
  readonly: boolean;
  disabled: boolean;
  notice: string;
  noticeKey?: string;
  tier?: string[];
}

export interface SubmitResult {
  errors: string[];
  warnings: string[];
}

const MONEY_TOLERANCE = 0.005;

/* ============================== 路径求值 ============================== */

/**
 * 穿透数组路径求值：items.amount → 各行 amount 组成的数组（聚合规则用）。
 * 末段多值返回数组由聚合函数消费；单值返回标量；无命中返回 undefined。
 */
export function pathVal(obj: unknown, path: string): unknown {
  const parts = path.split('.');
  let cur: unknown[] = [obj];
  for (const k of parts) {
    const next: unknown[] = [];
    for (const o of cur) {
      if (o == null) continue;
      if (Array.isArray(o)) {
        for (const it of o) {
          if (it != null && typeof it === 'object' && k in (it as Record<string, unknown>)) {
            next.push((it as Record<string, unknown>)[k]);
          }
        }
      } else if (k in (o as Record<string, unknown>)) {
        next.push((o as Record<string, unknown>)[k]);
      }
    }
    cur = next;
  }
  if (!cur.length) return undefined;
  return cur.length === 1 ? cur[0] : cur;
}

/* ============================== 比较 ============================== */

function ctxVal(v: unknown, ctx: UiContext): unknown {
  if (v === '$me') return ctx.me;
  if (v === '$myteam') return ctx.myTeam;
  if (v === '$all') return ctx.all ?? '$all';
  return v;
}

/** 双数值走数值比较，否则走字符串比较（ISO 日期 yyyy-mm-dd 可直接比） */
function cmpOrd(op: Operator, left: unknown, right: unknown): boolean {
  const ln = Number(left);
  const rn = Number(right);
  if (left !== '' && right !== '' && !Number.isNaN(ln) && !Number.isNaN(rn)) {
    left = ln;
    right = rn;
  } else {
    left = String(left ?? '');
    right = String(right ?? '');
  }
  switch (op) {
  case 'gt': return (left as number) > (right as number);
  case 'gte': return (left as number) >= (right as number);
  case 'lt': return (left as number) < (right as number);
  case 'lte': return (left as number) <= (right as number);
  default: return false;
  }
}

function cmp(op: Operator, left: unknown, right: unknown): boolean {
  switch (op) {
  case 'eq': return left === right;
  case 'neq': return left !== right;
  case 'gt':
  case 'gte':
  case 'lt':
  case 'lte': return cmpOrd(op, left, right);
  case 'isNull': return left == null || left === '' || (Array.isArray(left) && left.length === 0);
  case 'isNotNull': return !(left == null || left === '' || (Array.isArray(left) && left.length === 0));
  case 'in': return Array.isArray(right) && right.includes(left);
  case 'notIn': return Array.isArray(right) && !right.includes(left);
  case 'between': return Array.isArray(right) && Number(left) >= Number(right[0]) && Number(left) <= Number(right[1]);
  case 'earlierThan': return !!left && !!right && String(left) < String(right);
  case 'laterThan': return !!left && !!right && String(left) > String(right);
  default: return false;
  }
}

/* ============================== 条件求值 ============================== */

/** 条件嵌套深度上限（协议 UI-REF-05；超限 fail-closed 不命中，防恶意/误配深层嵌套栈溢出） */
export const MAX_COND_DEPTH = 3;

export function evalCond(c: UiCondition | undefined, data: Record<string, unknown>, ctx: UiContext = {}, depth = 0): boolean {
  if (!c) return true;
  if (depth > MAX_COND_DEPTH) {
    console.warn(`[uiSchema] condition depth > ${MAX_COND_DEPTH}, fail-closed to false`);
    return false;
  }
  if (c.all) return c.all.every((x) => evalCond(x, data, ctx, depth + 1));
  if (c.any) return c.any.some((x) => evalCond(x, data, ctx, depth + 1));
  if (c.not) return !evalCond(c.not, data, ctx, depth + 1);
  if (!c.fieldCode || !c.operator) return true;
  const left = pathVal(data, c.fieldCode);
  let right: unknown;
  if (c.valueType === 'field') {
    right = c.compareField != null ? pathVal(data, c.compareField) : undefined;
  } else {
    right = ctxVal(c.value, ctx);
  }
  if (right === '$all') return true;
  return cmp(c.operator, left, right);
}

/* ============================== linkage ============================== */

/**
 * 求全部字段的 UI 态。
 * - baseRequired：字段静态必填（来自 meta_field.required），与 linkage 的 required 取并集。
 * - baseStates：字段基线态（来自控件声明，如专票的税号默认隐藏），在规则之前落地，规则再覆盖。
 * - 默认所有字段 visible=true, required=baseRequired, readonly=false。
 * - linkage `then` 收敛到 visible/required/readonly/disabled/notice/tier/value 七动作（防范围膨胀）。
 * - `value` 赋值有意写回 `data`（赋值联动后继规则可见，对标 TPA excuteRule）；调用方如需不可变快照请先 `structuredClone`。
 * - 循环联动：本函数为单轮收敛（渲染器负责"次轮只读提示"与 warn 日志）。
 */
export function linkageState(
  data: Record<string, unknown>,
  rules: LinkageRule[] = [],
  ctx: UiContext = {},
  baseRequired: Record<string, boolean> = {},
  baseStates: Record<string, Partial<FieldUiState>> = {},
): Record<string, FieldUiState> {
  const st: Record<string, FieldUiState> = {};
  const ensure = (f: string): FieldUiState =>
    st[f] || (st[f] = { visible: true, required: !!baseRequired[f], readonly: false, disabled: false, notice: '', noticeKey: '' });

  // 字段基线态（如 tax_no 默认隐藏）：先于规则落地
  for (const [f, partial] of Object.entries(baseStates)) {
    Object.assign(ensure(f), partial);
  }

  for (const r of rules) {
    if (r.enabled === false) continue; // 规则级停用：整条不生效（协议 v1.2）
    if (!evalCond(r.when, data, ctx)) continue;
    for (const t of r.then) {
      const s = ensure(t.fieldCode);
      if (t.visible !== undefined) s.visible = t.visible;
      if (t.required !== undefined) s.required = t.required;
      if (t.readonly !== undefined) s.readonly = t.readonly;
      if (t.disabled !== undefined) s.disabled = t.disabled;
      if (t.notice !== undefined) s.notice = t.notice;
      if (t.noticeKey !== undefined) s.noticeKey = t.noticeKey;
      if (t.tier !== undefined) s.tier = t.tier;
      if (t.value !== undefined && t.value !== null) {
        data[t.fieldCode] = t.value;
      }
    }
  }
  return st;
}

/**
 * 字段可见性总判定（预览与运行时同源）：
 * - 控件/联动静态 visible=false ⇒ 隐藏；
 * - 联动 then.tier 声明后 ⇒ 仅 ctx.tier 命中才可见（不声明=不限制）。
 *
 * 曾踩坑：RulesPanel 早可配 tier 显隐，但两个渲染器都只读 visible ⇒ 「配了不生效」
 * 的假能力。所有渲染器判可见性必须走本函数，不得各写各的。
 */
export function fieldVisible(st: FieldUiState | undefined, ctx: UiContext = {}): boolean {
  if (!st) return true;
  if (st.visible === false) return false;
  if (st.tier && st.tier.length && (!ctx.tier || !st.tier.includes(ctx.tier))) return false;
  return true;
}

/* ============================== submit ============================== */

/** 数值聚合（sum/min/max 用）：只收数值，非数值忽略 */
function numVals(data: Record<string, unknown>, fields: string[]): number[] {
  const out: number[] = [];
  for (const f of fields) {
    const v = pathVal(data, f);
    if (Array.isArray(v)) out.push(...v.map(Number).filter((x) => !Number.isNaN(x)));
    else if (v != null && v !== '') {
      const n = Number(v);
      if (!Number.isNaN(n)) out.push(n);
    }
  }
  return out;
}

/** 非空计数（count/completeness 用）：数组计非空元素，标量非空计 1（count 不再漏数非数值行） */
function countVals(data: Record<string, unknown>, fields: string[]): number {
  let n = 0;
  for (const f of fields) {
    const v = pathVal(data, f);
    if (Array.isArray(v)) {
      for (const it of v) if (it != null && it !== '') n++;
    } else if (v != null && v !== '') n++;
  }
  return n;
}

/** 标量或求和（consistency 左值：发票金额标量 vs 清单列求和） */
function scalarOrSum(data: Record<string, unknown>, field: string): unknown {
  const v = pathVal(data, field);
  if (Array.isArray(v)) return v.map(Number).filter((x) => !Number.isNaN(x)).reduce((a, b) => a + b, 0);
  return v;
}

/**
 * 本单判重（unique 前端诚实口径：前端无跨单查库能力）。
 * - 数组列：按最长行逐行取组合键判重；
 * - 标量组合：全非空即提醒（WARNING 交人工确认，ERROR 则阻断），缺值不扰。
 */
function hasDupCombo(data: Record<string, unknown>, fields: string[]): boolean {
  const cols = fields.map((f) => pathVal(data, f));
  if (cols.some(Array.isArray)) {
    const len = Math.max(0, ...cols.map((c) => (Array.isArray(c) ? c.length : 0)));
    const seen = new Set<string>();
    for (let i = 0; i < len; i++) {
      const key = JSON.stringify(cols.map((c) => (Array.isArray(c) ? (c[i] ?? null) : c)));
      if (seen.has(key)) return true;
      seen.add(key);
    }
    return false;
  }
  return cols.every((v) => v != null && v !== '');
}

/** 容差比较：数值 eq/neq 走容差（含边界通过：差额==容差算过，与 erp"差额>0.01 不得保存"同口径），其余走 cmp */
function passWithTolerance(op: Operator, left: unknown, right: unknown, tolerance: number): boolean {
  if (typeof left === 'number' && typeof right === 'number' && (op === 'eq' || op === 'neq')) {
    // 浮点护栏：100.01-100=0.010000000000005116，边界比较需 epsilon（DECIMAL 精度口径的 IEEE754 落地）
    const ok = Math.abs(left - right) - tolerance <= 1e-9;
    return op === 'eq' ? ok : !ok;
  }
  return cmp(op, left, right);
}

/**
 * 提交期校验：聚合函数九白名单 + 必填短路。
 * - fail-closed：未知函数名 / sum系缺 compare 一律 ERROR 阻断（配置错误不静默放行）。
 * - balance/consistency/completeness 右值来自 fields/params（样板均无 compare）。
 * - ERROR 阻断提交，WARNING 弹确认后可提交。
 * 注意：前端先拦（体验），后端 RuntimeWriteValidator + Hook 照旧强制（安全底线不后移）。
 */
export function runSubmit(
  data: Record<string, unknown>,
  rules: SubmitRule[] = [],
  linkage: LinkageRule[] = [],
  ctx: UiContext = {},
  baseRequired: Record<string, boolean> = {},
): SubmitResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  for (const r of rules) {
    const tag = r.name ?? r.code ?? '校验';
    if (r.enabled === false) continue; // 规则级停用：整条不生效（协议 v1.2）
    // 生效前置：when 存在且不命中则整条跳过（如拒赔完整性只在 REJECTING 跑，不误伤正常提交）
    if (r.when && !evalCond(r.when, data, ctx)) continue;
    const push = (msg: string): void => {
      (r.verifyType === 'WARNING' ? warnings : errors).push(`【${tag}】${msg}`);
    };
    const tol = r.params?.tolerance ?? MONEY_TOLERANCE;

    // unique：命中即按 verifyType 出 message（operator/compare 不参与，scope 透传后端日志）
    if (r.function === 'unique') {
      if (hasDupCombo(data, r.fields)) push(r.message);
      continue;
    }

    let left: unknown;
    let right: unknown;
    switch (r.function) {
    case 'compare':
      left = pathVal(data, r.fields[0]);
      break;
    case 'sum':
      left = numVals(data, r.fields).reduce((a, b) => a + b, 0);
      break;
    case 'min': {
      const vs = numVals(data, r.fields);
      left = vs.length ? Math.min(...vs) : undefined;
      break;
    }
    case 'max': {
      const vs = numVals(data, r.fields);
      left = vs.length ? Math.max(...vs) : undefined;
      break;
    }
    case 'count':
      left = countVals(data, r.fields);
      break;
    case 'balance': {
      // erp 借贷平衡：fields[0] 借方和 vs fields[1] 贷方和
      if (r.fields.length < 2) {
        push(`配置错误：balance 需两组字段（借/贷），rule=${r.code ?? r.name}`);
        continue;
      }
      left = numVals(data, [r.fields[0]]).reduce((a, b) => a + b, 0);
      right = numVals(data, [r.fields[1]]).reduce((a, b) => a + b, 0);
      break;
    }
    case 'consistency': {
      // claim 发票一致：fields[0] 标量 vs 其余列聚合和
      left = scalarOrSum(data, r.fields[0]);
      right = numVals(data, r.fields.slice(1)).reduce((a, b) => a + b, 0);
      break;
    }
    case 'completeness': {
      // 要素完整性：非空要素数 vs minReasons（缺省 fields.length）
      left = countVals(data, r.fields);
      right = r.params?.minReasons ?? r.fields.length;
      break;
    }
    default:
      push(`配置错误：未知函数 ${String((r as SubmitRule).function)}（rule=${r.code ?? r.name})`);
      continue;
    }

    // sum/count/max/min/compare 的右值来自 compare；缺 compare = 配置错误
    if (right === undefined && (r.function === 'sum' || r.function === 'count' || r.function === 'max' || r.function === 'min' || r.function === 'compare')) {
      if (!r.compare) {
        push(`配置错误：${r.function} 缺 compare 右值（rule=${r.code ?? r.name})`);
        continue;
      }
      right = r.compare.valueType === 'field' ? pathVal(data, r.compare.fieldCode ?? '') : r.compare.value;
    }

    if (!passWithTolerance(r.operator, left, right, tol)) push(r.message);
  }

  // 必填短路（与后端必填同口径）
  const st = linkageState(data, linkage, ctx, baseRequired);
  for (const [f, s] of Object.entries(st)) {
    if (s.visible && s.required) {
      const v = data[f];
      if (v == null || v === '' || (Array.isArray(v) && v.length === 0)) {
        errors.push(`【必填】${f} 不能为空`);
      }
    }
  }

  return { errors, warnings };
}

/* ============================== 控件级校验（对标宜搭/PowerApps 字段级约束） ============================== */

/**
 * 控件级单字段校验（协议 v1.2 §4.3）：配置即控件 props，创作侧 ControlInspector 暴露 UI。
 * 与 runSubmit（跨字段提交规则 + 元数据必填）互补，互不重复报必填：
 * - 必填覆盖：仅当 `control.required===true` 且**元数据未要求**时才报（元数据必填由 runSubmit 负责，避免双报）；
 * - 数值型（NUMBER/MONEY/SLIDER）的 min/max 越界；
 * - 文本型（INPUT/TEXTAREA）的 maxLength 超长、pattern 正则不匹配；
 * - 非法 pattern（编译失败）视为配置错误 fail-closed，不静默放行（与 runSubmit 的 fail-closed 口径一致）。
 *
 * 注意：前端先拦（体验），后端 RuntimeWriteValidator 仍是安全底线，不后移。
 */
const NUMERIC_KINDS = new Set<ControlKind>(['NUMBER', 'MONEY', 'SLIDER']);
const TEXT_KINDS = new Set<ControlKind>(['INPUT', 'TEXTAREA']);

export function validateControlValues(
  data: Record<string, unknown>,
  controls: Control[] = [],
  baseRequired: Record<string, boolean> = {},
): { errors: string[]; warnings: string[] } {
  const errors: string[] = [];
  const warnings: string[] = [];

  for (const c of controls) {
    if (c.virtual || !c.fieldCode) continue;
    const code = c.fieldCode;
    const kind = c.controlKind ?? 'INPUT';
    const v = data[code];
    const empty = v == null || v === '' || (Array.isArray(v) && v.length === 0);

    // 必填覆盖：仅控件显式要求且元数据未要求时由本函数报（避免与 runSubmit 的元数据必填双报）
    if (c.required === true && !baseRequired[code]) {
      if (empty) { errors.push(`【必填】${code} 不能为空`); continue; }
    }
    if (empty) continue;

    const props = c.props ?? {};
    if (NUMERIC_KINDS.has(kind)) {
      const n = Number(v);
      if (!Number.isNaN(n)) {
        if (props.min !== undefined && n < Number(props.min)) errors.push(`【范围】${code} 不能小于 ${props.min}`);
        if (props.max !== undefined && n > Number(props.max)) errors.push(`【范围】${code} 不能大于 ${props.max}`);
      }
    } else if (TEXT_KINDS.has(kind)) {
      const s = String(v);
      if (props.maxLength !== undefined && s.length > Number(props.maxLength)) {
        errors.push(`【长度】${code} 长度不能超过 ${props.maxLength} 个字符`);
      }
      if (typeof props.pattern === 'string' && props.pattern.trim()) {
        try {
          if (!new RegExp(props.pattern).test(s)) errors.push(`【格式】${code} 不符合格式要求（${props.pattern}）`);
        } catch {
          // 非法正则：配置错误，fail-closed（不静默放行，提示建模者修正）
          errors.push(`【配置错误】${code} 的 pattern 不是合法正则：${props.pattern}`);
        }
      }
    }
  }

  return { errors, warnings };
}

/* ============================== actionBar ============================== */

/** 按状态门控过滤可见动作，并按 sortOrder 稳定排序 */
export function resolveVisibleActions(data: Record<string, unknown>, actions: ActionBarItem[] = [], ctx: UiContext = {}): ActionBarItem[] {
  return actions
    .filter((a) => (a.visibleWhen ? evalCond(a.visibleWhen, data, ctx) : true))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

/* ============================== 版本协商（前向兼容：RJSF 惯例） ============================== */

export const KNOWN_SCHEMA_MAJOR = 1;
export const KNOWN_SCHEMA_MINOR = 1;

export type SchemaVersionCheck = 'ok' | 'warn-unknown-minor' | 'error-bad-major';

/**
 * 版本协商：大版本≠1 直接 error（未知大版本显式拒绝，不猜渲染）；
 * minor 高于已知只 warn（黄条告警、未知字段忽略，渲染继续）；
 * minor 缺省按 1（v1.0 实例零修改仍合法）。
 */
export function checkSchemaVersion(schema: BoneUiSchema): SchemaVersionCheck {
  if (schema.schemaVersion !== KNOWN_SCHEMA_MAJOR) return 'error-bad-major';
  const minor = schema.minor ?? schema.schemaMinor ?? 1;
  return minor > KNOWN_SCHEMA_MINOR ? 'warn-unknown-minor' : 'ok';
}

/* ============================== 变体裁决（v1.1） ============================== */

/**
 * 变体裁决：命中第一个 priority 最小（缺省 100）且维度全匹配的变体，整块替换 base 对应块。
 * 同 priority 取声明顺序（Array.sort 稳定排序）。
 * 无命中返回 base（全量兼容）。缺 tier 时回落 base（禁猜）。
 * 空 match（{}）视为全匹配（兜底变体写法）。
 */
export function resolveVariant(schema: BoneUiSchema, ctx: UiContext = {}): BoneUiSchema {
  const vars = schema.variants;
  if (!vars || !vars.length) return schema;
  const matched = vars
    .filter((v) => matchVariant(v.match, ctx))
    .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
  const hit = matched[0];
  if (!hit) return schema;

  return {
    ...schema,
    views: {
      list: hit.views?.list ?? schema.views.list,
      form: hit.views?.form ?? schema.views.form,
      detail: hit.views?.detail ?? schema.views.detail,
    },
    linkageRules: hit.linkageRules ?? schema.linkageRules,
    submitRules: hit.submitRules ?? schema.submitRules,
    actionBar: hit.actionBar ?? schema.actionBar,
  };
}

function matchVariant(m?: VariantMatch, ctx: UiContext = {}): boolean {
  if (!m) return true;
  if (m.roles && m.roles.length) {
    const roles = ctx.roles ?? [];
    if (!m.roles.some((r) => roles.includes(r))) return false;
  }
  if (m.tiers && m.tiers.length && (!ctx.tier || !m.tiers.includes(ctx.tier))) return false;
  if (m.channels && m.channels.length && (!ctx.channel || !m.channels.includes(ctx.channel))) return false;
  if (m.devices && m.devices.length && (!ctx.device || !m.devices.includes(ctx.device))) return false;
  return true;
}
