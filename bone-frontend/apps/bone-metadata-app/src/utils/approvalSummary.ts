import type { ApprovalSummaryRow } from '../services/metadataApi';

/**
 * 审批摘要 → 列表单元格文案（纯函数，单一真源）。
 *
 * <p><b>为什么审批人不放单据字段</b>：审批的 1:N 事实（节点 × 轮次 × 办理人）在引擎台账
 * {@code wf_task} 里，一张单据同节点可能同时挂着多位办理人（会签 / 加签 / 改派）。单据上
 * 一个 {@code approver_id} 标量只能表达「一个人」，会把这个事实抹平，而且改派只改
 * {@code wf_task}、不回写字段，字段必然陈旧。故列表这一列一律从引擎投影取。
 *
 * <p><b>为什么不显示账户 id</b>：{@code 9104} 对业务用户零信息量（DAMA：业务用户通过业务名
 * 定位实体）。姓名解析不出来时（无 iam 账号读权限 / 目录不可用）降级为「N 人待办」，
 * 宁可少一个名字，也不把内部账户号印在业务列表上。
 */

/** 单元格语义色（antd Tag color）。`null` = 无审批流，渲染破折号不占视觉位。 */
export type ApprovalTone = 'success' | 'processing' | 'warning' | 'error' | 'default' | null;

export interface ApprovalCellView {
  tone: ApprovalTone;
  /** 单元格主文案 */
  label: string;
  /** Tooltip：当前节点（中文名）+ 办理人明细 */
  detail: string;
}

/** 与引擎 {@code WorkflowInstance.Phase} 同名口径（列表只看这五态 + 未进流程）。 */
const PHASE_LABEL: Record<string, { tone: ApprovalTone; label: string }> = {
  APPROVED: { tone: 'success', label: '已通过' },
  REJECTED: { tone: 'error', label: '已驳回' },
  WITHDRAWN: { tone: 'default', label: '已撤回' },
  TERMINATED: { tone: 'default', label: '已终止' },
  SUSPENDED: { tone: 'warning', label: '待指派' },
};

/**
 * 把摘要投影成单元格视图。
 *
 * @param row 引擎摘要（未进流程时为 undefined）
 * @param names 账户 id → 姓名（解析不出来传空表）
 */
export function approvalCellOf(
  row: ApprovalSummaryRow | null | undefined,
  names: Record<string, string> = {},
): ApprovalCellView {
  if (!row || !row.started) {
    return { tone: null, label: '—', detail: '该记录尚未进入审批流' };
  }
  const phase = (row.phase || '').toUpperCase();
  const nodeLabel = row.currentNodeLabel || row.currentNode || '';
  const pending = row.pendingAssignees ?? [];
  const humanNames = pending.map((id) => names[id] || '').filter((n) => n !== '');

  const fixed = PHASE_LABEL[phase];
  if (fixed) {
    const detail =
      phase === 'SUSPENDED'
        ? '审批人解析不出，单据已挂起：需管理员在审批中心指派办理人，或修正指派规则'
        : nodeLabel
          ? `当前节点：${nodeLabel}`
          : '流程已结束';
    return { tone: fixed.tone, label: fixed.label, detail };
  }

  // RUNNING：这一列的价值就在于「现在卡在谁那里」，所以主文案给办理人。
  if (pending.length === 0) {
    return {
      tone: 'processing',
      label: '流转中',
      detail: nodeLabel ? `正在进入下一节点：${nodeLabel}` : '流程流转中',
    };
  }
  if (humanNames.length === 0) {
    return {
      tone: 'processing',
      label: `${pending.length} 人待办`,
      detail: nodeLabel ? `当前节点：${nodeLabel}（无账号目录读权限，未解析姓名）` : '待办中',
    };
  }
  const label =
    humanNames.length === 1 ? humanNames[0] : `${humanNames[0]} 等 ${humanNames.length} 人`;
  return {
    tone: 'processing',
    label,
    detail: nodeLabel ? `当前节点：${nodeLabel} · 办理人：${humanNames.join('、')}` : humanNames.join('、'),
  };
}
