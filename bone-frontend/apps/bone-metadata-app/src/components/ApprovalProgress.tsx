import React, { useEffect, useState } from 'react';
import { Alert, Empty, Space, Tag, Timeline, Tooltip, Typography } from 'antd';
import { formatDate } from '@bone/shared-utils';
import { loadApproverNames } from '../utils/approverDirectory';
import type { ApprovalDescribe } from '../services/metadataApi';

const { Text } = Typography;

/**
 * 流程阶段 → 展示口径（颜色 + 文案）。
 *
 * <p>与引擎 {@code WorkflowInstance.Phase} 一一对应：RUNNING 用 processing（有下一步在等），
 * SUSPENDED 用 orange（不是失败，是「卡住等人」，与 REJECTED 的红必须区分开，否则用户以为单子废了），
 * WITHDRAWN/TERMINATED 用 default（中性终止，不该像失败一样刺眼）。
 */
const PHASE_META: Record<string, { color: string; label: string; hint: string }> = {
  RUNNING: { color: 'processing', label: '审批中', hint: '流程正在流转，等待当前节点办理' },
  SUSPENDED: { color: 'orange', label: '已挂起', hint: '审批人解析不出（未填审批人 / 审批人就是发起人本人 / 组织目录未接入），单据已挂起，联系管理员指派后继续' },
  APPROVED: { color: 'green', label: '已通过', hint: '全部节点已通过，流程终结' },
  REJECTED: { color: 'red', label: '已驳回', hint: '审批人驳回，流程终结；修改后可重新提交（新实例）' },
  WITHDRAWN: { color: 'default', label: '已撤回', hint: '发起人撤回，待办已作废；修改后可重新提交（新实例）' },
  TERMINATED: { color: 'default', label: '已终止', hint: '管理员强制终止，流程终结' },
};

/** 任务决策 → 颜色/文案（trail 每一行）。 */
const DECISION_META: Record<string, { color: string; label: string }> = {
  APPROVE: { color: 'green', label: '通过' },
  REJECT: { color: 'red', label: '驳回' },
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: '待办',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已作废',
};

function asText(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  const s = String(value);
  return s.trim() === '' ? null : s;
}

/**
 * 单据审批进度（引擎真源投影，非前端推断）。
 *
 * <p><b>为什么要有这一块</b>：单据列表只显示 {@code status=SUBMITTED}，用户看不到「卡在谁那里」——
 * 报销单/采购单提交后最常被问的三个问题（到哪一步了 / 谁在审 / 为什么退回）在界面上都无处可查，
 * 只能去审批中心按待办反查。审批中心的 {@code describe} 已经把实例 + 当前待办 + 完整轨迹投影出来，
 * 详情抽屉直接复用同一份真源，避免前端用动作留痕去猜流程状态。
 *
 * <p><b>为什么不显示在列表</b>：describe 是逐单据调用（一次一条实例 + 轨迹），列表逐行调用即成 N+1；
 * 详情抽屉是单条视图，天然只查一次。
 *
 * <p><b>失败静默</b>：绝大多数记录从未进过审批流（started=false）或当前账号无实体读权限，
 * 这些都不是错误——组件对空实例返回 null，不占位、不抛错、不阻断详情其余内容。
 */
const ApprovalProgress: React.FC<{ data: ApprovalDescribe | null; loading?: boolean }> = ({
  data,
  loading,
}) => {
  // 姓名目录走进程内缓存（列表已经拉过一次），这里不会多发请求；拿不到就回落为「待办人」占位，
  // 绝不把 9104 这类账户 id 印给业务用户。
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => {
    let alive = true;
    void loadApproverNames().then((map) => {
      if (alive) setNames(map);
    });
    return () => {
      alive = false;
    };
  }, []);

  const labelOf = (code: string) => data?.nodeLabels?.[code] || code;
  const nameOf = (id?: string | null) => (id ? names[id] || '待办人' : '待指派');

  if (loading) {
    return (
      <div>
        <Typography.Title level={5} style={{ marginTop: 0 }}>
          审批进度
        </Typography.Title>
        <Timeline pending="加载审批进度…" />
      </div>
    );
  }
  // started=false / 无 instance：这条记录根本没走过审批流，不占位。
  if (!data?.started || !data.instance) return null;

  const instance = data.instance as Record<string, unknown>;
  const phase = asText(instance.phase) ?? 'RUNNING';
  const meta = PHASE_META[phase] ?? { color: 'default', label: phase, hint: '' };
  const currentNode = asText(instance.currentNode);
  const startedBy = asText(instance.startedBy);
  const startedAt = asText(instance.startedAt);
  const pendingTasks = data.pendingTasks ?? [];
  const trail = (data.trail ?? []).filter((t) => !t.pending);
  // 实例是否仍在流转：用于区分「终态驳回」与「退回指定节点后的原地返工」（两者的任务决策都是 REJECT）。
  const instanceAlive = phase === 'RUNNING' || phase === 'SUSPENDED';

  return (
    <div>
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        审批进度
      </Typography.Title>

      <Space size={8} wrap style={{ marginBottom: 8 }}>
        <Tooltip title={meta.hint}>
          <Tag color={meta.color} style={{ marginInlineEnd: 0 }}>
            {meta.label}
          </Tag>
        </Tooltip>
        {currentNode && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            当前节点 <Text code>{labelOf(currentNode)}</Text>
          </Text>
        )}
        {asText(instance.definitionKey) && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {asText(instance.definitionKey)} v{asText(instance.definitionVersion) ?? '?'}
          </Text>
        )}
        {(startedBy || startedAt) && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            发起人 {startedBy ? nameOf(startedBy) : '—'}
            {startedAt ? ` · ${formatDate(startedAt)}` : ''}
          </Text>
        )}
      </Space>

      {phase === 'SUSPENDED' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message="流程已挂起，等待管理员指派办理人"
          description={
            <>
              {/* 真实原因优先（服务端读回挂起告警正文）：没有它，用户只看到「挂起」二字，
                  无从判断该去改单据的审批人字段、还是找管理员改派——这正是挂起单最常见的卡点。 */}
              {asText(data.suspendReason) && (
                <div style={{ marginBottom: 4 }}>{asText(data.suspendReason)}</div>
              )}
              <div>
                引擎按指派规则解析不出审批人时不会静默放行（跳过节点等于少一道审批）。
                请联系管理员在审批中心「问题单据」里指派办理人，或补填单据上的「指定审批人」后恢复流程；
                发起人也可直接撤回后重新提交。
              </div>
            </>
          }
        />
      )}

      {pendingTasks.length > 0 && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            待办 {pendingTasks.length} 人
            {pendingTasks.length > 1 ? '（会签/加签，全部处理完才进入下一节点）' : ''}
          </Text>
          {pendingTasks.map((t) => (
            <div key={t.taskId} style={{ fontSize: 12, lineHeight: '20px' }}>
              <Tag color="blue" style={{ marginInlineEnd: 6 }}>
                待办
              </Tag>
              <Text>{labelOf(t.nodeCode)}</Text>
              <Text type="secondary"> · 办理人 {nameOf(t.assigneeId)}</Text>
            </div>
          ))}
        </div>
      )}

      {trail.length === 0 ? (
        <Empty description="尚无审批记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Timeline
          items={trail.map((t) => {
            const decision = asText(t.decision);
            const dMeta = decision ? DECISION_META[decision] : undefined;
            const cancelled = asText(t.status) === 'CANCELLED';
            // 驳回且实例仍在流转 =「退回指定节点」的原地返工，不是终态驳回。
            // 照旧渲染红色的「驳回」会让发起人以为单子已经废了，而实际正等着上游节点重新处理。
            const sentBack = decision === 'REJECT' && instanceAlive;
            const rowMeta = sentBack ? { color: 'orange', label: '退回返工' } : dMeta;
            return {
              color: rowMeta?.color ?? (cancelled ? 'gray' : 'blue'),
              children: (
                <div>
                  <Space size={8} wrap>
                    <Text strong>{labelOf(t.nodeCode)}</Text>
                    <Tag color={rowMeta?.color ?? (cancelled ? 'default' : 'blue')}>
                      {rowMeta?.label ?? STATUS_LABEL[asText(t.status) ?? ''] ?? '—'}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      办理人 {nameOf(t.assigneeId)}
                    </Text>
                    {t.decidedAt && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {formatDate(t.decidedAt)}
                      </Text>
                    )}
                  </Space>
                  {asText(t.comment) && (
                    <div style={{ fontSize: 12, color: 'rgba(0,0,0,.65)', marginTop: 2, wordBreak: 'break-all' }}>
                      意见：{asText(t.comment)}
                    </div>
                  )}
                </div>
              ),
            };
          })}
        />
      )}
    </div>
  );
};

export default ApprovalProgress;
