import {
  type ApproveStep,
  type AssignmentRule,
  type Branch,
  type CcStep,
  type Condition,
  type ConditionStep,
  type DefinitionEdge,
  type DefinitionNode,
  type FlowDoc,
  type Step,
  type UiSection,
  type WorkflowDefinitionJson,
} from './types';
import { END_CODE, START_CODE } from './compile';

/**
 * 引擎 DSL → 文档树的解析器。
 *
 * <p>**两条路径，故意不对称**：
 *
 * 1. **快路径**：定义里带 `ui.flowDoc`（设计器编译时写入）——直接取回原文，**无损**。
 *    这是「图 → 树」这道反推题的唯一可靠解法：单出口 DAG 反推成树在一般情况下是多解的
 *    （哪个节点是分支汇合点、分支体边界在哪，都要靠可达性求交去猜）。与其写一个 shaky 的反推算法，
 *    不如把答案随定义一起存下来——BPMN 把 DI 段存进同一个 `.bpmn` 文件，是同一个道理。
 *
 * 2. **慢路径**：旧定义（迁移 / 种子数据落库，没有 `ui.flowDoc`）——按图结构重建：以「所有分支头
 *    可达集的交集」判定分支汇合点，再逐段还原。这条路**可能有损**，因此返回 `lossy: true` 并附告警；
 *    界面对 `lossy` 的定义必须禁用保存，避免「打开旧流程、随手保存、分支被抹平」。
 */
export interface ParseResult {
  doc: FlowDoc;
  /** 是否由旧数据重建（有损）。有损时界面必须禁用保存。 */
  lossy: boolean;
  warnings: string[];
}

export class FlowDocParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FlowDocParseError';
  }
}

interface ParseContext {
  key: string;
  name: string;
  nodes: Map<string, DefinitionNode>;
  outEdges: Map<string, DefinitionEdge[]>;
  assignments: Record<string, AssignmentRule[]>;
  warnings: string[];
  lossy: boolean;
}

export function parse(definition: WorkflowDefinitionJson): ParseResult {
  if (!definition || !Array.isArray(definition.nodes)) {
    throw new FlowDocParseError('流程定义缺少 nodes，无法解析');
  }
  const embedded = definition.ui?.flowDoc;
  if (embedded && isFlowDoc(embedded)) {
    return { doc: embedded, lossy: false, warnings: [] };
  }

  const context: ParseContext = {
    key: definition.key,
    name: definition.name ?? definition.key,
    nodes: new Map(definition.nodes.map((node) => [node.code, node])),
    outEdges: new Map(),
    assignments: definition.assignments ?? {},
    warnings: [],
    lossy: false,
  };
  for (const edge of definition.edges ?? []) {
    const list = context.outEdges.get(edge.from) ?? [];
    list.push(edge);
    context.outEdges.set(edge.from, list);
  }

  const startCode = resolveStartCode(context);
  if (!startCode) {
    throw new FlowDocParseError('流程定义缺少发起节点（START），无法确定入口');
  }
  const first = singleTarget(context, startCode);
  const steps = first ? parseChain(context, first, terminalCodes(context)) : [];
  context.warnings.push(
    '该定义由旧数据重建（未携带 ui.flowDoc），分支结构按图推断、可能不精确；保存前请逐节点核对',
  );
  context.lossy = true;
  return { doc: { key: context.key, name: context.name, steps }, lossy: context.lossy, warnings: context.warnings };
}

/**
 * 定位发起节点。
 *
 * <p>编译器的约定是 code 恰好为 `START`，但**种子数据与手写迁移**里常见 `start` / `begin`
 * 这类小写或自定义命名（引擎只按 `type` 认节点）。旧实现直接 `nodes.get('START')`，于是这些
 * 「运行时能跑」的流程在设计器里一律报「缺少发起节点」，整条流程打不开。按 `type` 兜底即可兼容。
 */
function resolveStartCode(context: ParseContext): string | null {
  if (context.nodes.has(START_CODE)) return START_CODE;
  for (const node of context.nodes.values()) {
    if ((node.type ?? '').toUpperCase() === 'START') return node.code;
  }
  return null;
}

/** 终结点集合：`END` 只是缺省命名，旧定义可能是 `end` 或业务化的名字。 */
function terminalCodes(context: ParseContext): Set<string> {
  const codes = new Set<string>([END_CODE]);
  for (const node of context.nodes.values()) {
    if ((node.type ?? '').toUpperCase() === 'END') codes.add(node.code);
  }
  return codes;
}

/** 沿单出口链还原一段序列，遇到 `stopCodes` 中的节点即停。 */
function parseChain(context: ParseContext, startCode: string, stopCodes: Set<string>): Step[] {
  const steps: Step[] = [];
  let code: string | null = startCode;
  const guard = new Set<string>();

  while (code && !stopCodes.has(code)) {
    if (guard.has(code)) {
      context.warnings.push(`检测到环（${code}），v1 不支持回环审批，解析在此中断`);
      context.lossy = true;
      break;
    }
    guard.add(code);

    const node = context.nodes.get(code);
    if (!node) {
      context.warnings.push(`边指向不存在的节点 ${code}，解析在此中断`);
      context.lossy = true;
      break;
    }

    if (node.type === 'APPROVE') {
      steps.push(toApproveStep(context, node));
      code = singleTarget(context, code);
      continue;
    }
    if (node.type === 'CC') {
      steps.push(toCcStep(context, node));
      code = singleTarget(context, code);
      continue;
    }
    if (node.type === 'CONDITION') {
      const { step, continuation } = parseCondition(context, node);
      steps.push(step);
      code = continuation;
      continue;
    }
    if (node.type === 'END') {
      break;
    }
    // START 出现在链中间属于非法定义，交由后端校验裁决；这里跳过以免死循环。
    context.warnings.push(`节点 ${code}（${node.type}）不应出现在链路中间，已忽略`);
    context.lossy = true;
    code = singleTarget(context, code);
  }
  return steps;
}

/** 条件节点：显式分支按序 + 默认分支（末位），并求分支汇合点作为后续入口。 */
function parseCondition(
  context: ParseContext,
  node: DefinitionNode,
): { step: ConditionStep; continuation: string | null } {
  const outs = context.outEdges.get(node.code) ?? [];
  const defaultIndex = outs.findIndex((edge) => isDefaultEdge(edge));
  const explicit = defaultIndex >= 0 ? outs.slice(0, defaultIndex) : outs;
  const defaultEdge = defaultIndex >= 0 ? outs[defaultIndex] : null;
  if (!defaultEdge) {
    context.warnings.push(`条件节点 ${node.code} 缺默认边（else），已按「无默认分支」处理`);
    context.lossy = true;
  }

  const heads = outs.map((edge) => edge.to);
  const rejoin = findRejoin(context, heads);
  // 停在这里的集合必须包含**真实**终结点（旧定义可能是 `end`），否则分支体会把结束节点当审批节点继续解析。
  const stop = terminalCodes(context);
  if (rejoin) {
    stop.add(rejoin);
  }

  const branches: Branch[] = explicit.map((edge, index) => ({
    id: `${node.code}#${index}`,
    label: edge.label ?? `分支 ${index + 1}`,
    when: (edge.when ?? null) as Condition | null,
    body: parseChain(context, edge.to, stop),
  }));
  const defaultBranch: Branch = {
    id: `${node.code}#default`,
    label: defaultEdge?.label ?? '默认',
    when: null,
    body: defaultEdge ? parseChain(context, defaultEdge.to, stop) : [],
  };
  return {
    step: { kind: 'condition', code: node.code, label: node.label ?? node.code, branches, defaultBranch },
    continuation: rejoin,
  };
}

/**
 * 求分支汇合点：所有分支头**可达集的交集**里，距任一分支头最近的那个节点。
 *
 * <p>为什么用交集：分支汇合的定义就是「从每条分支都能走到」的第一个节点。若交集为空（分支各自结尾），
 * 返回 `null`，后续链为空——这属于编译器不会产出的形态，标记有损。
 */
function findRejoin(context: ParseContext, heads: string[]): string | null {
  if (heads.length === 0) {
    return null;
  }
  const distances = heads.map((head) => bfsDistances(context, head));
  const candidates = [...distances[0].keys()].filter((code) =>
    distances.every((map) => map.has(code)),
  );
  if (candidates.length === 0) {
    context.warnings.push('分支各自终止、没有汇合点，后续链路无法还原');
    context.lossy = true;
    return null;
  }
  let best: string | null = null;
  let bestScore = Number.POSITIVE_INFINITY;
  for (const code of candidates) {
    const score = Math.min(...distances.map((map) => map.get(code) ?? Number.POSITIVE_INFINITY));
    if (score < bestScore) {
      best = code;
      bestScore = score;
    }
  }
  return best;
}

function bfsDistances(context: ParseContext, from: string): Map<string, number> {
  const distances = new Map<string, number>();
  const queue: string[] = [from];
  distances.set(from, 0);
  while (queue.length > 0) {
    const code = queue.shift() as string;
    const distance = distances.get(code) ?? 0;
    for (const edge of context.outEdges.get(code) ?? []) {
      if (!distances.has(edge.to)) {
        distances.set(edge.to, distance + 1);
        queue.push(edge.to);
      }
    }
  }
  return distances;
}

function toApproveStep(context: ParseContext, node: DefinitionNode): ApproveStep {
  const step: ApproveStep = {
    kind: 'approve',
    code: node.code,
    label: node.label ?? node.code,
    mode: node.mode ?? 'SINGLE',
    rules: context.assignments[node.code] ?? [],
  };
  if (node.quorum != null) {
    step.quorum = node.quorum;
  }
  return step;
}

function toCcStep(context: ParseContext, node: DefinitionNode): CcStep {
  return {
    kind: 'cc',
    code: node.code,
    label: node.label ?? node.code,
    rules: context.assignments[node.code] ?? [],
  };
}

function singleTarget(context: ParseContext, code: string): string | null {
  const outs = context.outEdges.get(code) ?? [];
  if (outs.length === 0) {
    return null;
  }
  if (outs.length > 1) {
    context.warnings.push(`节点 ${code} 有 ${outs.length} 条出边（仅条件节点允许多出边），取第一条`);
    context.lossy = true;
  }
  return outs[0].to;
}

function isDefaultEdge(edge: DefinitionEdge): boolean {
  return edge.when == null || (typeof edge.when === 'object' && Object.keys(edge.when).length === 0);
}

function isFlowDoc(candidate: unknown): candidate is FlowDoc {
  return (
    typeof candidate === 'object' &&
    candidate !== null &&
    'key' in candidate &&
    Array.isArray((candidate as FlowDoc).steps)
  );
}

/** 从定义里取设计态 UI 段（布局 / 折叠状态），供画布渲染；缺失则空对象。 */
export function uiOf(definition: WorkflowDefinitionJson): UiSection {
  return definition.ui ?? {};
}
