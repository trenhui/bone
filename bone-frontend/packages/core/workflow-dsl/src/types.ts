/**
 * 工作流定义 DSL 的类型镜像 + 设计器内部文档树（FlowDoc）。
 *
 * <p>**为什么需要这一层**：后端 `WorkflowDefinition` 的图形形态是 `{nodes, edges}`——一张单出口 DAG。
 * 如果界面直接编辑这两个数组，那么「在中间插入一个审批节点」这种最常见的操作，就要手写一遍图算法：
 * 找前驱、改前驱的 `to`、追加新边、维持「条件节点默认边必须在末位」（WF-006）、判断断头（WF-005）与孤岛（WF-009）。
 * 每一处都是一类 bug。
 *
 * <p>改用文档树后，这些结构不变量**在类型层就成立**：`ConditionStep` 一定有 `defaultBranch`，
 * 因此编译出来必然「恰一条默认边且在末位」，不需要运行时检查。
 *
 * <p>START 与 END 刻意<b>不</b>出现在文档树里：发起节点唯一（WF-002）、结束节点至少一个（WF-003），
 * 二者都不该是用户可编辑的对象。编译器自动补。让用户能删掉发起节点，是给校验器凭空制造工作。
 */

/** 引擎当前能解释的定义格式版本（与后端 `WorkflowDefinition.CURRENT_ENGINE_SCHEMA_VERSION` 对齐）。 */
export const ENGINE_SCHEMA_VERSION = 1;

export type NodeType = 'START' | 'APPROVE' | 'CONDITION' | 'CC' | 'END';

export type ApprovalMode = 'SINGLE' | 'ALL' | 'ANY' | 'RATIO';

export type AssignmentStrategy =
  | 'LINE_MANAGER'
  | 'DEPT_HEAD'
  | 'LEVEL_UP'
  | 'ROLE'
  | 'USER'
  | 'FIELD'
  | 'INITIATOR_SELECT';

/**
 * 各策略在 `params` 里的**必需键**（单真源）。
 *
 * <p>键名不是猜的，逐字对齐后端 `RecordFieldApproverResolver.resolve` 的读取口径。最容易踩的是
 * `USER` 读的是单数 `userId`——写成 `userIds` 时后端读到 `null`、解析出空列表、节点**静默挂起**，
 * 而发布期校验（WF-013）只看策略名不看参数，全程绿灯。后端已补 WF-020 拦这类问题，
 * 前端表单也必须从这张表取键名，否则两边各写一遍迟早分叉。
 */
export const PARAM_KEY_BY_STRATEGY: Record<AssignmentStrategy, string | null> = {
  LINE_MANAGER: null,
  DEPT_HEAD: null,
  LEVEL_UP: 'level',
  ROLE: 'roleCode',
  USER: 'userId',
  FIELD: 'fieldCode',
  INITIATOR_SELECT: 'fieldCode',
};

/** 结构化条件（与后端 `StructuredConditionEvaluator` 的 13 个算子逐一对应，不得扩展）。 */
export interface ConditionAtom {
  fieldCode: string;
  operator:
    | 'isNull'
    | 'isNotNull'
    | 'eq'
    | 'neq'
    | 'gt'
    | 'gte'
    | 'lt'
    | 'lte'
    | 'in'
    | 'notIn'
    | 'between'
    | 'earlierThan'
    | 'laterThan';
  value?: unknown;
  /** `'field'` 时右侧取 `compareField` 的值（字段对比字段），而不是常量 `value`。 */
  valueType?: 'field';
  compareField?: string;
}

export interface ConditionGroup {
  all?: Condition[];
  any?: Condition[];
  not?: Condition;
}

export type Condition = ConditionAtom | ConditionGroup;

export interface AssignmentRule {
  strategy: AssignmentStrategy;
  params: Record<string, unknown>;
}

export interface DefinitionNode {
  code: string;
  type: NodeType;
  mode?: ApprovalMode | null;
  label?: string | null;
  quorum?: number | null;
}

export interface DefinitionEdge {
  from: string;
  to: string;
  when?: Condition | null;
  label?: string | null;
}

export interface LayoutPoint {
  x: number;
  y: number;
}

/**
 * 引擎**不读**、设计器专用的那一段。
 *
 * <p>先例是 BPMN 2.0 的 `BPMNDiagram`（DI）段：布局与语义同存一个 artifact，引擎解析时忽略，二者同版本同回滚，
 * 不会出现「定义回滚了、坐标没回滚」的错位。后端用 Spring 默认的 `FAIL_ON_UNKNOWN_PROPERTIES=false` 容忍它，
 * 并有契约测试钉住——否则将来谁换了严格 mapper，存量定义会集体读不回来。
 */
export interface UiSection {
  layout?: Record<string, LayoutPoint>;
  /** 文档树原文：`parse` 的精确来源，避免「图 → 树」的有损反推。 */
  flowDoc?: FlowDoc;
  collapsed?: string[];
}

export interface WorkflowDefinitionJson {
  key: string;
  name?: string;
  version: number;
  engineSchemaVersion: number;
  nodes: DefinitionNode[];
  edges: DefinitionEdge[];
  assignments: Record<string, AssignmentRule[]>;
  ui?: UiSection;
}

// ====================== 文档树 ======================

export interface ApproveStep {
  kind: 'approve';
  code: string;
  label: string;
  mode: ApprovalMode;
  /** 仅 RATIO 有意义：至少 N 人通过即通过。 */
  quorum?: number;
  rules: AssignmentRule[];
}

export interface CcStep {
  kind: 'cc';
  code: string;
  label: string;
  rules: AssignmentRule[];
}

/** 一条条件分支。`when` 为 `null` 表示默认边（else），只出现在 `defaultBranch` 上。 */
export interface Branch {
  id: string;
  label: string;
  when: Condition | null;
  body: Step[];
}

export interface ConditionStep {
  kind: 'condition';
  code: string;
  label: string;
  /** 显式分支（≥1 才合理，但编译器不做断言——交给后端 WF-006 统一裁决）。 */
  branches: Branch[];
  /** 默认分支：必存在，编译时固定排在出边末位。 */
  defaultBranch: Branch;
}

export type Step = ApproveStep | CcStep | ConditionStep;

export interface FlowDoc {
  key: string;
  name: string;
  steps: Step[];
}

/** 编译器保留的节点编码：用户不得占用。 */
export const RESERVED_CODES = ['START', 'END'] as const;

/**
 * 后端发布期校验的一条违规（`WorkflowDefinitionValidator.Violation` 的镜像）。
 *
 * <p>`locator` 是节点编码，用于把清单项高亮到画布对应节点——**不要**靠解析 `message` 文本反推节点，
 * 那会让后端改一句文案就变成前端故障。
 */
export interface Violation {
  code: string;
  message: string;
  locator?: string | null;
}
