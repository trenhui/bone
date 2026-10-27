import {
  type AssignmentRule,
  type Branch,
  type DefinitionEdge,
  type DefinitionNode,
  type FlowDoc,
  type LayoutPoint,
  type Step,
  type WorkflowDefinitionJson,
  ENGINE_SCHEMA_VERSION,
  RESERVED_CODES,
} from './types';

/**
 * 文档树 → 引擎 DSL 的编译器。
 *
 * <p>**核心思路**：把「串并联」结构一次性摊平。每一层序列编译时先编译它的**后继**（continuation），
 * 再让每个分支体指向同一个 continuation——这正是「分支汇合」的语义，也让「空分支体」自然退化为
 * 「条件节点直接连到后续节点」，不需要特殊分支处理。
 *
 * <p>**不变量由结构保证，而不是靠校验兜底**：
 * - 发起节点唯一、结束节点追加在末尾 ⇒ WF-002 / WF-003 天然满足；
 * - 审批 / 抄送 / 发起节点各自恰一条出边 ⇒ WF-014 / WF-015 / WF-018 天然满足；
 * - 条件节点出边 = 显式分支（按序）+ 默认分支（末位）⇒ WF-006 天然满足。
 *
 * <p>编译器<b>不</b>做业务校验：`RATIO` 忘了填 quorum、`ROLE` 忘了填 roleCode 这类问题由后端统一裁决
 * （`WF-019` / `WF-020`）。前端若自己判一遍，必然与后端漂移，且漂移方向不可控。
 */
export interface CompileOptions {
  version?: number;
  /** 画布坐标：仅存进 `ui.layout`，引擎不读。 */
  layout?: Record<string, LayoutPoint>;
  /** 是否把文档树原文写进 `ui.flowDoc`（默认 true，用于精确反解析）。 */
  keepFlowDoc?: boolean;
}

export const START_CODE = 'START';
export const END_CODE = 'END';

export class FlowDocCompileError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FlowDocCompileError';
  }
}

export function compile(doc: FlowDoc, options: CompileOptions = {}): WorkflowDefinitionJson {
  assertDocShape(doc);

  const nodes: DefinitionNode[] = [];
  const edges: DefinitionEdge[] = [];
  const assignments: Record<string, AssignmentRule[]> = {};

  nodes.push({ code: START_CODE, type: 'START', label: '发起' });
  nodes.push({ code: END_CODE, type: 'END', label: '结束' });

  const entry = emitChain(doc.steps, END_CODE, nodes, edges, assignments);
  // 入口边插到最前：递归编译是「先算后继、再落本节点」，边数组天然是倒序产出；
  // 把 START 边放到首位，让边数组读起来与流程方向一致（版本 diff 也更稳）。
  edges.unshift({ from: START_CODE, to: entry, when: null, label: null });

  const ordered = orderNodesByTraversal(nodes, edges);

  const definition: WorkflowDefinitionJson = {
    key: doc.key,
    name: doc.name,
    version: options.version ?? 1,
    engineSchemaVersion: ENGINE_SCHEMA_VERSION,
    nodes: ordered,
    edges,
    assignments,
  };

  const ui: Record<string, unknown> = {};
  if (options.layout) {
    ui.layout = options.layout;
  }
  if (options.keepFlowDoc !== false) {
    ui.flowDoc = doc;
  }
  if (Object.keys(ui).length > 0) {
    definition.ui = ui;
  }
  return definition;
}

/**
 * 编译一段序列，返回它的入口节点编码。
 *
 * <p>递归形状：`entry = 第一个 step`，它的后继是「剩余序列」的入口。因此必须先算后继，再落第一条边的目标。
 */
function emitChain(
  steps: Step[],
  tail: string,
  nodes: DefinitionNode[],
  edges: DefinitionEdge[],
  assignments: Record<string, AssignmentRule[]>,
): string {
  if (steps.length === 0) {
    return tail;
  }
  const [head, ...rest] = steps;
  const after = emitChain(rest, tail, nodes, edges, assignments);

  if (head.kind === 'condition') {
    nodes.push({ code: head.code, type: 'CONDITION', label: head.label });
    // 顺序即语义：显式分支按文档顺序落边，默认边必须最后一条（WF-006）。
    for (const branch of head.branches) {
      const target = emitChain(branch.body, after, nodes, edges, assignments);
      edges.push({ from: head.code, to: target, when: branch.when, label: branch.label });
    }
    const defaultValue = emitChain(head.defaultBranch.body, after, nodes, edges, assignments);
    edges.push({ from: head.code, to: defaultValue, when: null, label: head.defaultBranch.label });
    return head.code;
  }

  if (head.kind === 'cc') {
    nodes.push({ code: head.code, type: 'CC', label: head.label });
    edges.push({ from: head.code, to: after, when: null, label: null });
    if (head.rules.length > 0) {
      assignments[head.code] = head.rules;
    }
    return head.code;
  }

  // approve：quorum 只在 RATIO 下写出。非 RATIO 带 quorum 会被后端 WF-019 判为「忽略」级别的违规，
  // 与其让脏字段进库，不如在编译期就丢掉——引擎本来就不读它。
  const node: DefinitionNode = { code: head.code, type: 'APPROVE', mode: head.mode, label: head.label };
  if (head.mode === 'RATIO' && typeof head.quorum === 'number') {
    node.quorum = head.quorum;
  }
  nodes.push(node);
  edges.push({ from: head.code, to: after, when: null, label: null });
  if (head.rules.length > 0) {
    assignments[head.code] = head.rules;
  }
  return head.code;
}

/**
 * 按「从 START 出发的可达遍历顺序」重排节点数组。
 *
 * <p>引擎不看节点顺序，但人和 diff 看。递归编译天然产生「后继先入数组」的倒序，直接落库会让
 * 版本 diff 里每个节点都显示为「移动过」。按遍历顺序重排后，同一份文档每次编译产出的 JSON 完全一致
 * （这是 `compile(doc)` 可做结构相等断言的前提）。
 */
function orderNodesByTraversal(nodes: DefinitionNode[], edges: DefinitionEdge[]): DefinitionNode[] {
  const byCode = new Map(nodes.map((node) => [node.code, node]));
  const ordered: DefinitionNode[] = [];
  const seen = new Set<string>();
  const queue: string[] = [START_CODE];
  while (queue.length > 0) {
    const code = queue.shift() as string;
    if (seen.has(code)) {
      continue;
    }
    seen.add(code);
    const node = byCode.get(code);
    if (node) {
      ordered.push(node);
    }
    for (const edge of edges) {
      if (edge.from === code) {
        queue.push(edge.to);
      }
    }
  }
  // 理论上不会有不可达节点（编译器只产出连通的树），兜底保留以免静默丢节点。
  for (const node of nodes) {
    if (!seen.has(node.code)) {
      ordered.push(node);
    }
  }
  return ordered;
}

/** 结构前置断言：这里只拦「编译器无法自洽表达」的输入，业务规则仍归后端。 */
function assertDocShape(doc: FlowDoc): void {
  if (!doc || typeof doc.key !== 'string' || doc.key.trim() === '') {
    throw new FlowDocCompileError('流程定义 key 不能为空');
  }
  const codes = new Set<string>();
  const visit = (steps: Step[], path: string) => {
    for (const step of steps) {
      if (!step.code || step.code.trim() === '') {
        throw new FlowDocCompileError(`${path} 存在缺少编码的节点`);
      }
      if ((RESERVED_CODES as readonly string[]).includes(step.code)) {
        throw new FlowDocCompileError(`节点编码 ${step.code} 是编译器保留字（START / END 自动生成）`);
      }
      if (codes.has(step.code)) {
        throw new FlowDocCompileError(`节点编码重复：${step.code}`);
      }
      codes.add(step.code);
      if (step.kind === 'condition') {
        visit(step.branches.flatMap((branch: Branch) => branch.body), `${step.code} 的分支`);
        visit(step.defaultBranch.body, `${step.code} 的默认分支`);
      }
    }
  };
  visit(doc.steps, '主链');
}
