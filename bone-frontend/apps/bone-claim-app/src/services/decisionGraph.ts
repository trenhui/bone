/**
 * 决策图编排的**展示/即时反馈**元数据（不是第二套配置真源）。
 *
 * <p>节点顺序、类型与「唯一可跳过 AiAssess」的事实真源在后端
 * `com.bone.claim.domain.model.decision.DecisionGraph`：
 *   - `canonical()` L56-64 固定 5 节点主链；
 *   - `SKIPPABLE_NODES = Set.of(AI_ASSESS)` L34；
 *   - 写侧 `ClaimFlowConfigApplicationService.save()`、读侧 `DecisionFlowConfigPortAdapter.skippableNodes()`、
 *     执行侧 `DecisionGraphExecutor` 均调用上述真源。
 *
 * <p>本文件只是把同一份事实**投影**给前端做即时反馈，用途有两个：
 *   1. 渲染只读的节点顺序列表（用户能看到有哪些节点、为什么不能改）；
 *   2. 在提交前给出「节点不存在 / 必需节点不可跳过」的即时提示，避免一次无意义的往返。
 *
 * <p>边界纪律：这里**不做最终裁决**。服务端 `save()` 才是唯一门禁；前端校验口径若与后端漂移，
 * 以服务端拒绝为准，页面必须原样展示服务端错误码与消息，且不得清空现网配置。
 * 顺序不可编辑、节点不可注入/删除——本文件不提供任何重排/增删 API。
 */

/** 决策图节点（只读展示用；顺序即 canonical 顺序）。 */
export interface DecisionGraphNode {
  /** 与后端 `DecisionGraph.Node.name()` 逐字一致。 */
  name: string;
  /** 中文展示名（仅前端展示，不参与任何判定）。 */
  label: string;
  /** 与后端 `DecisionNodeType` 对齐的展示值。 */
  nodeType: 'OCR' | 'RULE' | 'LLM' | 'DECISION';
  /** 是否在后端 `SKIPPABLE_NODES` 白名单内。 */
  skippable: boolean;
}

/**
 * canonical 5 节点主链（顺序与后端 `DecisionGraph.canonical()` 一一对应）。
 *
 * <p>用 `as const` 冻结，页面只读取、不做任何变更操作。
 */
export const DECISION_GRAPH_NODES: readonly DecisionGraphNode[] = [
  { name: 'EvidenceLoad', label: '证据加载', nodeType: 'OCR', skippable: false },
  { name: 'RuleCheck', label: '规则校验', nodeType: 'RULE', skippable: false },
  { name: 'AiAssess', label: 'AI 评估', nodeType: 'LLM', skippable: true },
  { name: 'Adjust', label: '理算', nodeType: 'RULE', skippable: false },
  { name: 'Arbitrate', label: '决策归档', nodeType: 'DECISION', skippable: false },
] as const;

/** 唯一可跳过的节点白名单（投影自后端 `DecisionGraph.SKIPPABLE_NODES`）。 */
export const SKIPPABLE_NODES: readonly string[] = DECISION_GRAPH_NODES.filter((n) => n.skippable).map(
  (n) => n.name,
);

/** 节点名集合，用于「节点存在性」校验。 */
const CANONICAL_NAMES: ReadonlySet<string> = new Set(DECISION_GRAPH_NODES.map((n) => n.name));

/**
 * 提交前的即时反馈校验：返回人类可读的错误列表，空数组表示本地校验通过。
 *
 * <p>只校验三件事，与后端 `ClaimFlowConfigApplicationService.save()` 的口径同构：
 *   1. 节点存在性（必须是 canonical 图里的节点）；
 *   2. 跳过集合合法性（必须在 SKIPPABLE_NODES 白名单内）；
 *   3. 必需节点不可跳过（非白名单节点即必需节点，白名单目前只有 AiAssess）。
 *
 * <p>不做「第二套口径」：不在这里放宽或收紧可跳过范围；最终以服务端拒绝为准。
 * 入参可能是表单里尚未去重的字符串数组，逐项校验即可（后端会按 Set 存储）。
 */
export function validateSkipNodes(skipNodes: readonly string[] | null | undefined): string[] {
  if (skipNodes == null) {
    return ['skipNodes 不能为空（传空数组表示恢复默认配置）'];
  }
  const errors: string[] = [];
  const seen = new Set<string>();
  for (const raw of skipNodes) {
    const node = typeof raw === 'string' ? raw.trim() : String(raw);
    if (!CANONICAL_NAMES.has(node)) {
      errors.push(`节点「${node || '空字符串'}」不在决策图中（节点不存在）`);
      continue;
    }
    if (seen.has(node)) {
      errors.push(`节点「${node}」重复，请只提交一次`);
      continue;
    }
    seen.add(node);
    if (!SKIPPABLE_NODES.includes(node)) {
      errors.push(`必需节点「${node}」不可跳过（当前仅 ${SKIPPABLE_NODES.join('/')} 可跳过）`);
    }
  }
  return errors;
}
