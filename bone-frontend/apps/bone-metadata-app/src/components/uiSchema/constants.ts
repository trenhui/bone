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
  'maxSizeMB', 'min', 'precision', 'quoteText', 'roles', 'rows', 'step', 'targetEntity',
  'tier', 'valueField', 'warningText',
] as const;

/** 尚未进协议白名单的候选 props（§13.4 已知豁免）：lint 按 WARNING 报出而非 ERROR */
export const PROPS_PENDING_REVIEW = ['optionsRef'];
