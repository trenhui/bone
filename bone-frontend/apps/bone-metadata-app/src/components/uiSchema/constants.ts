/**
 * UI Schema 配置器（Designer）常量与元数据。
 *
 * 设计纪律：
 * - 所有"用户可见文案"集中在此，避免各面板硬编码导致文案漂移（协议文档 §0 单一真源精神）。
 * - 控件类型/操作符/聚合函数的**语义说明**与 `uiSchemaEngine.ts` 保持一致；此处只补充 UI 呈现信息
 *   （分组、图标、中文标签），不重复定义行为。
 */
import {
  BorderOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  FieldNumberOutlined,
  FileTextOutlined,
  FontSizeOutlined,
  FundOutlined,
  LinkOutlined,
  PaperClipOutlined,
  PieChartOutlined,
  SlidersOutlined,
  TableOutlined,
  ThunderboltOutlined,
  WalletOutlined,
  WarningOutlined,
  HistoryOutlined,
  CalculatorOutlined,
} from '@ant-design/icons';
import type { ControlKind, Operator, SubmitFunction } from '../../utils/uiSchemaEngine';

/* ============================== 控件类型 ============================== */

export interface KindMeta {
  value: ControlKind;
  label: string;
  /** 分组（调色板按组展示） */
  group: string;
  icon: React.ComponentType;
  /** 一句话说明（悬浮提示） */
  hint: string;
}

export const CONTROL_KINDS: KindMeta[] = [
  { value: 'INPUT', label: '单行文本', group: '基础', icon: FontSizeOutlined, hint: '短文本，如名称、单号' },
  { value: 'TEXTAREA', label: '多行文本', group: '基础', icon: FileTextOutlined, hint: '长文本，如说明、备注' },
  { value: 'NUMBER', label: '数字', group: '基础', icon: FieldNumberOutlined, hint: '整数/小数' },
  { value: 'SLIDER', label: '滑块', group: '基础', icon: SlidersOutlined, hint: '区间取值' },
  { value: 'MONEY', label: '金额', group: '基础', icon: WalletOutlined, hint: '带币格式化与精度容差' },

  { value: 'ENUM', label: '枚举选择', group: '选择', icon: CheckSquareOutlined, hint: '从元数据域 enumValues 取值' },
  { value: 'REFERENCE', label: '关联引用', group: '选择', icon: LinkOutlined, hint: '引用其它实体的记录' },
  { value: 'SWITCH', label: '开关', group: '选择', icon: ThunderboltOutlined, hint: '布尔值' },

  { value: 'DATE', label: '日期', group: '日期', icon: ClockCircleOutlined, hint: 'yyyy-MM-dd' },
  { value: 'DATETIME', label: '日期时间', group: '日期', icon: ClockCircleOutlined, hint: 'yyyy-MM-dd HH:mm' },

  { value: 'CHILD_TABLE', label: '子表', group: '关系', icon: TableOutlined, hint: '明细行，聚合规则的数据源' },
  { value: 'ATTACHMENT', label: '附件', group: '关系', icon: PaperClipOutlined, hint: '影像/凭证上传' },

  { value: 'STAT', label: '统计卡', group: '展示', icon: FundOutlined, hint: '关键指标卡片' },
  { value: 'CHART', label: '图表', group: '展示', icon: PieChartOutlined, hint: '可视化图形' },
  { value: 'TIMELINE', label: '时间轴', group: '展示', icon: HistoryOutlined, hint: '审批流/轨迹' },
  { value: 'COMPUTED', label: '计算值', group: '展示', icon: CalculatorOutlined, hint: '只读派生值' },
  { value: 'QUOTE', label: '引用块', group: '展示', icon: BorderOutlined, hint: '高亮引用文本' },
  { value: 'ALERT', label: '提示条', group: '展示', icon: WarningOutlined, hint: '告示/风险提示' },
];

/** 分组顺序（调色板展示用） */
export const KIND_GROUPS = ['基础', '选择', '日期', '关系', '展示'];

export const KIND_MAP: Record<string, KindMeta> = Object.fromEntries(
  CONTROL_KINDS.map((k) => [k.value, k]),
);

/**
 * 控件类型下拉的分组 options（antd Select 的 `options` 形态）。
 *
 * **必须是模块级单例**（P1-5 性能）：结构面板每个控件行都渲染一个 antd `<Select>`，
 * 若在 JSX 里写 `options={KIND_GROUPS.map(...)}`，这份数组会在
 * 「每个控件行 × 每次渲染」被重建一遍——而它的内容完全由静态常量决定。
 * antd Select 每次收到新 options 引用都会重建内部 flatten 结果与缓存，
 * 于是 N 个控件 = 每帧 N 次无谓重建。
 * 实测（bench/ui-schema-select-options.bench.test.tsx，jsdom 挂载中位数）：
 * 40/80/160 控件下静态引用比逐行重建快 2.08x / 3.11x / 2.37x。
 *
 * 放在constants.ts 而非组件内，是为了让「选项真源」与 CONTROL_KINDS 挨在一起，
 * 避免优化散落在调用点、日后新增控件类型时又写回内联重建。
 */
export const CONTROL_KIND_OPTIONS: { label: string; options: { value: string; label: string }[] }[] =
  KIND_GROUPS.map((g) => ({
    label: g,
    options: CONTROL_KINDS.filter((k) => k.group === g).map((k) => ({ value: k.value, label: k.label })),
  }));

/** 元数据字段类型 → 默认控件类型（新建控件时预填，用户可改） */
export const FIELD_TYPE_TO_KIND: Record<string, ControlKind> = {
  VARCHAR: 'INPUT',
  TEXT: 'TEXTAREA',
  INT: 'NUMBER',
  BIGINT: 'NUMBER',
  DECIMAL: 'MONEY',
  BOOLEAN: 'SWITCH',
  DATE: 'DATE',
  DATETIME: 'DATETIME',
  ENUM: 'ENUM',
  REFERENCE: 'REFERENCE',
  CHILD_TABLE: 'CHILD_TABLE',
  ATTACHMENT: 'ATTACHMENT',
};

/* ============================== 分区形态（协议 v1.1 §10.2） ============================== */

/** 分区类型（section.kind）：默认 form；其余为展示型区块 */
export const SECTION_KINDS = [
  { value: 'form', label: '表单区', hint: '常规字段分区（默认）' },
  { value: 'dashboard', label: '统计卡区', hint: '关键指标卡片（STAT 控件）' },
  { value: 'timeline', label: '时间轴', hint: '版本链 / 决策轨迹' },
  { value: 'compare', label: '对照区', hint: '三一致三栏对照 / 对账' },
  { value: 'report', label: '报告区', hint: '数字注入报告（CHART/COMPUTED）' },
  { value: 'tasks', label: '待办区', hint: '任务卡片列表' },
] as const;

/** 分区布局（section.layout）：默认 single；split 用于审查分栏 */
export const SECTION_LAYOUTS = [
  { value: 'single', label: '单栏', hint: '常规纵向堆叠（默认）' },
  { value: 'split-left-40', label: '左 40% 分栏', hint: '左窄右宽：风险面板 + 原文' },
  { value: 'split-left-60', label: '左 60% 分栏', hint: '左宽右窄：原文 + 处置面板' },
] as const;

/** 显示模式（displayModes：create/edit/view 子集，config/preview 不进运行时渲染） */
export const DISPLAY_MODES = [
  { value: 'create', label: '新建' },
  { value: 'edit', label: '编辑' },
  { value: 'view', label: '查看' },
] as const;

/* ============================== 操作符 ============================== */

export interface OperatorMeta {
  value: Operator;
  label: string;
  /** 是否需要右值（isNull/isNotNull 不需要） */
  needsValue: boolean;
}

export const OPERATORS: OperatorMeta[] = [
  { value: 'eq', label: '等于', needsValue: true },
  { value: 'neq', label: '不等于', needsValue: true },
  { value: 'gt', label: '大于', needsValue: true },
  { value: 'gte', label: '大于等于', needsValue: true },
  { value: 'lt', label: '小于', needsValue: true },
  { value: 'lte', label: '小于等于', needsValue: true },
  { value: 'in', label: '属于（多选）', needsValue: true },
  { value: 'notIn', label: '不属于', needsValue: true },
  { value: 'between', label: '介于', needsValue: true },
  { value: 'isNull', label: '为空', needsValue: false },
  { value: 'isNotNull', label: '不为空', needsValue: false },
  { value: 'earlierThan', label: '早于', needsValue: true },
  { value: 'laterThan', label: '晚于', needsValue: true },
];

export const OPERATOR_MAP: Record<string, OperatorMeta> = Object.fromEntries(
  OPERATORS.map((o) => [o.value, o]),
);

export const opLabel = (op?: Operator): string => OPERATOR_MAP[op ?? '']?.label ?? String(op ?? '?');

/* ============================== 运算符 × 字段类型收窄 ============================== */

/**
 * 按元数据字段类型收窄可用运算符（对标 Salesforce 筛选器 / 宜搭条件）：
 * 文本字段不该出现 between/gt（字符串比较「看起来生效」实则误配），
 * 数值/日期才有序比较意义。类型未知（虚拟控件/关联列）时不限制——禁猜。
 */
const OPS_TEXT: Operator[] = ['eq', 'neq', 'in', 'notIn', 'isNull', 'isNotNull'];
const OPS_NUMBER: Operator[] = ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'between', 'isNull', 'isNotNull'];
const OPS_DATE: Operator[] = ['eq', 'neq', 'between', 'earlierThan', 'laterThan', 'isNull', 'isNotNull'];
const OPS_BOOL: Operator[] = ['eq', 'neq', 'isNull', 'isNotNull'];

/** 字段类型 → 运算符白名单（大小写不敏感；未列出类型返回 null=不限制） */
const OPERATORS_BY_TYPE: Record<string, Operator[]> = {
  VARCHAR: OPS_TEXT, CHAR: OPS_TEXT, TEXT: OPS_TEXT, ENUM: OPS_TEXT, REFERENCE: OPS_TEXT,
  UUID: OPS_TEXT, JSON: OPS_TEXT,
  INT: OPS_NUMBER, BIGINT: OPS_NUMBER, DECIMAL: OPS_NUMBER, FLOAT: OPS_NUMBER, DOUBLE: OPS_NUMBER,
  MONEY: OPS_NUMBER, NUMBER: OPS_NUMBER,
  DATE: OPS_DATE, DATETIME: OPS_DATE, TIMESTAMP: OPS_DATE, TIME: OPS_DATE,
  BOOLEAN: OPS_BOOL, BOOL: OPS_BOOL,
};

/**
 * 该字段类型可用的运算符列表（保持 OPERATORS 声明序）。
 * @param fieldType 元数据字段 type（如 'VARCHAR'）；undefined/未知 = 全量不限制
 */
export function operatorsForType(fieldType?: string): OperatorMeta[] {
  if (!fieldType) return OPERATORS;
  const allowed = OPERATORS_BY_TYPE[fieldType.toUpperCase()];
  if (!allowed) return OPERATORS;
  return OPERATORS.filter((o) => allowed.includes(o.value));
}

/** 当前运算符是否适用于该字段类型（类型未知恒 true） */
export function operatorAllowedFor(op: Operator | undefined, fieldType?: string): boolean {
  if (!op || !fieldType) return true;
  const allowed = OPERATORS_BY_TYPE[fieldType.toUpperCase()];
  if (!allowed) return true;
  return allowed.includes(op);
}

/* ============================== 聚合函数 ============================== */

export interface FnMeta {
  value: SubmitFunction;
  label: string;
  /** 左值说明 */
  left: string;
  /** 右值说明（null = 右值来自 fields/params，不需 compare） */
  right: string | null;
  hint: string;
}

export const FUNCTIONS: FnMeta[] = [
  { value: 'sum', label: '求和', left: '指定字段之和', right: '比较值', hint: '如：明细金额合计 = 申报金额' },
  { value: 'count', label: '计数', left: '非空元素个数', right: '比较值', hint: '如：明细行数 ≥ 1' },
  { value: 'max', label: '最大值', left: '字段最大值', right: '比较值', hint: '如：单张发票金额 ≤ 1000' },
  { value: 'min', label: '最小值', left: '字段最小值', right: '比较值', hint: '如：最小金额 ≥ 0' },
  { value: 'compare', label: '直接比较', left: '字段原值', right: '比较值', hint: '如：开始日期早于结束日期' },
  { value: 'balance', label: '借贷平衡', left: 'fields[0] 之和', right: 'fields[1] 之和', hint: '两组字段各自求和后比对' },
  { value: 'consistency', label: '一致性', left: 'fields[0]（标量或求和）', right: '其余字段之和', hint: '如：发票金额 = 清单行合计' },
  { value: 'completeness', label: '要素完整性', left: '非空要素数', right: 'params.minReasons（缺省字段数）', hint: '如：拒赔至少填 N 项理由' },
  { value: 'unique', label: '唯一性', left: '组合键', right: null, hint: '本单判重；跨单查重由后端承担' },
];

export const FUNCTION_MAP: Record<string, FnMeta> = Object.fromEntries(
  FUNCTIONS.map((f) => [f.value, f]),
);

/** 需要 compare 右值的函数（引擎：缺 compare 即配置错误 fail-closed） */
export const FN_NEEDS_COMPARE: SubmitFunction[] = ['sum', 'count', 'max', 'min', 'compare'];

/* ============================== 校验级别 ============================== */

export const VERIFY_TYPES = [
  { value: 'ERROR', label: '强校验', color: 'red', hint: '阻断提交' },
  { value: 'WARNING', label: '弱校验', color: 'orange', hint: '弹确认后可继续' },
] as const;

/* ============================== 下拉 options 单例（P1-5 性能） ============================== */
/*
 * 规则：本文件导出的**静态**元数据，一律在此预派生一份 antd Select/Segmented 的
 * `options` 单例，组件里直接引用、不得内联 `.map()` 重建。
 *
 * 原因：antd 的 Select/Segmented 对 `options` 做内部 flatten 与缓存，**引用一变即失效**
 * （其 memo 依赖 options 引用）。面板里凡是「列表每一行一个下拉」的布局
 * （结构面板每控件一个类型下拉、规则面板每条规则三个下拉），内联 `.map()` 就等于
 * 「每个元素 × 每帧」重建一次options 树。N 个控件 =每帧 N 次无谓重建。
 *
 * 判据（何时该提升）：`options={X.map(...)}` 且 X 为本文件静态导出 ⇒ 提升；
 * X 依赖 props/state（如 ConditionEditor 的 operatorsForType(fieldType)）⇒ 在组件内
 * `useMemo`，不是模块单例。
 *
 * 实测见 bench/ui-schema-select-options.bench.test.tsx：40/80/160 行下 2.08x / 3.11x / 2.37x。
 */

/** FUNCTIONS 的 Select options（每条提交校验规则一个函数下拉） */
export const FUNCTION_OPTIONS = FUNCTIONS.map((f) => ({ value: f.value, label: f.label }));

/** OPERATORS 的 Select options（每条提交校验规则一个比较符下拉） */
export const OPERATOR_OPTIONS = OPERATORS.map((o) => ({ value: o.value, label: o.label }));

/** VERIFY_TYPES 的 Segmented options（每条提交校验规则一个级别切换） */
export const VERIFY_TYPE_OPTIONS = VERIFY_TYPES.map((v) => ({ value: v.value, label: v.label }));

/** 分区形态下拉 / 分区栏数下拉（每个分区头各一个，量级远小于控件行，仍统一提升） */
export const SECTION_KIND_OPTIONS = SECTION_KINDS.map((k) => ({ value: k.value, label: k.label }));
export const SECTION_LAYOUT_OPTIONS = SECTION_LAYOUTS.map((l) => ({ value: l.value, label: l.label }));

/** 显示模式多选（面板级，仅 1 处；一并提升保持口径一致） */
export const DISPLAY_MODE_OPTIONS = DISPLAY_MODES.map((m) => ({ value: m.value, label: m.label }));

/* ============================== 联动动作 ============================== */

export const THEN_ACTIONS = [
  { value: 'visible', label: '可见性', on: '显示', off: '隐藏' },
  { value: 'required', label: '必填', on: '必填', off: '非必填' },
  { value: 'readonly', label: '只读', on: '只读', off: '可编辑' },
  { value: 'disabled', label: '禁用', on: '禁用', off: '启用' },
] as const;

/* ============================== 协议 props 白名单 ============================== */

/**
 * 协议允许出现在 `control.props` 的键（与 schema.json 的 control.props 白名单同口径）。
 * 自检对白名单外的键报 ERROR——协议根/对象均 additionalProperties:false，越界键会被拒绝。
 */
/**
 * 协议 props 白名单 —— 与真源 bone-ui-schema.schema.json 的 definitions/control/properties/props
 * 严格一一对应（additionalProperties=false）。同步请两边同改，避免前后端校验口径漂移。
 */
export const PROP_WHITELIST = [
  'ai', 'anchorRef', 'chartType', 'childEntity', 'columns', 'computedFn', 'computedFrom',
  'currencyField', 'dataRef', 'labelField', 'lineage', 'mask', 'max', 'maxCount', 'maxLength',
  'maxSizeMB', 'min', 'pattern', 'precision', 'quoteText', 'roles', 'rows', 'span', 'step', 'targetEntity',
  'tier', 'valueField', 'warningText',
] as const;

/** 尚未进协议白名单的候选 props（§13.4 已知豁免）：lint 按 WARNING 报出而非 ERROR */
export const PROPS_PENDING_REVIEW = ['optionsRef'];
