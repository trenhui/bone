import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Drawer, Empty, Form, Input, Modal, Select, Space, Switch, Table, Tabs,
  Tag, Timeline, Typography, message,
} from 'antd';
import {
  CheckOutlined, CloseOutlined, HistoryOutlined, ReloadOutlined,
} from '@ant-design/icons';
import {
  runtimeApprovalApi, runtimeRecordApi,
  type ApprovalDescribe, type ApprovalInboxRow, type ApprovalInstanceRow,
  type ApprovalTaskRow,
} from '../services/metadataApi';

/** 任务状态 → 渲染口径（PENDING 之外均为已表态 / 已作废）。 */
const STATUS_META: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'processing', label: '待我审批' },
  APPROVED: { color: 'success', label: '已通过' },
  REJECTED: { color: 'error', label: '已驳回' },
  TRANSFERRED: { color: 'warning', label: '已转交' },
  CANCELLED: { color: 'default', label: '已作废' },
};

/** 实例阶段 → 渲染口径（问题单据 Tab 用）。 */
const PHASE_META: Record<string, { color: string; label: string }> = {
  RUNNING: { color: 'processing', label: '进行中' },
  SUSPENDED: { color: 'warning', label: '已挂起' },
  APPROVED: { color: 'success', label: '已通过' },
  REJECTED: { color: 'error', label: '已驳回' },
  WITHDRAWN: { color: 'default', label: '已撤回' },
  TERMINATED: { color: 'default', label: '已终止' },
};

/** 问题单据可检索的阶段（排障入口，不是全量浏览）。 */
const PHASE_OPTIONS = ['SUSPENDED', 'REJECTED', 'TERMINATED', 'RUNNING', 'WITHDRAWN'].map(
  (value) => ({ value, label: `${value}（${PHASE_META[value]?.label ?? value}）` }),
);

/**
 * 审批意见字段名随决策方向不同（服务端入站契约）：
 * approve → comment（可空），reject → reason（必填，驳回必须给出理由）。
 * 驳回可另附 backToNodeCode = 退回指定节点（不选 = 整单驳回）。
 */
type DecisionKind = 'approve' | 'reject';

type TabKey = 'pending' | 'decided' | 'problems' | 'inbox';

/** 批量决策失败明细（全量展示，不截断——失败条数本身就是需要排查的信息）。 */
interface DecisionFailure {
  taskId: string;
  error: string;
}

const ApprovalCenterPage: React.FC = () => {
  const [tab, setTab] = useState<TabKey>('pending');
  const [rows, setRows] = useState<ApprovalTaskRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [submitting, setSubmitting] = useState(false);

  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [decisionTarget, setDecisionTarget] =
    useState<{ kind: DecisionKind; rows: ApprovalTaskRow[] } | null>(null);
  /** 驳回退回节点候选（服务端按流程定义给出；空 = 只能整单驳回）。 */
  const [backNodeOptions, setBackNodeOptions] = useState<{ value: string; label: string }[]>([]);
  const [failures, setFailures] = useState<DecisionFailure[] | null>(null);
  const [trail, setTrail] = useState<ApprovalDescribe | null>(null);
  const [trailLoading, setTrailLoading] = useState(false);

  // 转办 / 加签（办理人自助干预）
  const [assist, setAssist] =
    useState<{ kind: 'transfer' | 'addSign'; row: ApprovalTaskRow } | null>(null);

  // 问题单据（按阶段排障）
  const [phase, setPhase] = useState('SUSPENDED');
  const [problems, setProblems] = useState<ApprovalInstanceRow[]>([]);
  const [problemsTruncated, setProblemsTruncated] = useState(false);
  const [problemsLoading, setProblemsLoading] = useState(false);
  const [problemAction, setProblemAction] =
    useState<{ kind: 'resume' | 'terminate'; row: ApprovalInstanceRow } | null>(null);

  // 站内信收件箱
  const [inboxRows, setInboxRows] = useState<ApprovalInboxRow[]>([]);
  const [inboxLoading, setInboxLoading] = useState(false);
  const [unreadOnly, setUnreadOnly] = useState(false);

  const [form] = Form.useForm();
  const [assistForm] = Form.useForm();
  const [problemForm] = Form.useForm();

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const res =
        tab === 'pending' || tab === 'decided'
          ? tab === 'pending'
            ? await runtimeApprovalApi.pending(page, 20)
            : await runtimeApprovalApi.decided(page, 20)
          : null;
      if (!res) return;
      if (res.code === 200 && res.data) {
        setRows(res.data.records ?? []);
        setTotal(Number(res.data.total ?? 0));
      } else {
        setRows([]);
        setTotal(0);
      }
    } catch (e) {
      message.error(`加载失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setLoading(false);
    }
  }, [tab, page]);

  const loadProblems = useCallback(async () => {
    setProblemsLoading(true);
    try {
      const res = await runtimeApprovalApi.instancesByPhase(phase, 50);
      if (res.code === 200 && res.data) {
        setProblems(res.data.instances ?? []);
        setProblemsTruncated(Boolean(res.data.truncated));
      } else {
        setProblems([]);
        setProblemsTruncated(false);
      }
    } catch (e) {
      message.error(`问题单据加载失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setProblemsLoading(false);
    }
  }, [phase]);

  const loadInbox = useCallback(async () => {
    setInboxLoading(true);
    try {
      const res = await runtimeApprovalApi.inbox(unreadOnly);
      setInboxRows(res.code === 200 && res.data ? res.data : []);
    } catch (e) {
      message.error(`收件箱加载失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setInboxLoading(false);
    }
  }, [unreadOnly]);

  useEffect(() => {
    if (tab === 'pending' || tab === 'decided') void loadTasks();
  }, [tab, loadTasks]);

  useEffect(() => {
    if (tab === 'problems') void loadProblems();
  }, [tab, loadProblems]);

  useEffect(() => {
    if (tab === 'inbox') void loadInbox();
  }, [tab, loadInbox]);

  useEffect(() => {
    setSelectedKeys([]);
  }, [tab]);

  const summaryText = (row: ApprovalTaskRow, key: string): string => {
    const value = row.summary?.[key];
    return value === undefined || value === null ? '-' : String(value);
  };

  /**
   * 打开决策弹窗。驳回且单条时顺带拉取审批概况 → 服务端给出的「可退回节点」作为候选
   * （批量驳回不逐单拉概况，保持整单驳回口径，避免 N 次 describe）。
   *
   * <p>候选必须来自服务端定义（`returnableNodes`）：从 `trail` 里推历史节点在**单级审批**下恒为空，
   * 而「第一个审批人退回发起人补正」正是最高频的退回场景（实测下拉 0 候选、退回入口不可用）。
   */
  const openDecision = async (kind: DecisionKind, targets: ApprovalTaskRow[]) => {
    setDecisionTarget({ kind, rows: targets });
    setBackNodeOptions([]);
    form.resetFields();
    if (kind !== 'reject' || targets.length !== 1) return;
    const row = targets[0];
    if (!row.entityCode || !row.recordId) return;
    try {
      const res = await runtimeApprovalApi.describe(row.entityCode, row.recordId);
      if (res.code !== 200 || !res.data?.started) return;
      const options = (res.data.returnableNodes ?? []).map((n) => ({
        value: n.code,
        label: n.label && n.label !== n.code ? `退回：${n.label}（${n.code}）` : `退回节点：${n.code}`,
      }));
      setBackNodeOptions(options);
    } catch {
      // 概况拉不到不阻断驳回：候选留空 = 退回不可选，走整单驳回
    }
  };

  const runDecision = async (
    kind: DecisionKind,
    targets: ApprovalTaskRow[],
    comment?: string,
    backToNodeCode?: string,
  ) => {
    setSubmitting(true);
    let ok = 0;
    const failed: DecisionFailure[] = [];
    for (const row of targets) {
      if (!row.entityCode || !row.recordId) {
        failed.push({ taskId: row.taskId, error: '缺少单据坐标（entityCode/recordId），无法触发动作' });
        continue;
      }
      try {
        const params: Record<string, unknown> =
          kind === 'approve' ? { comment: comment ?? '' } : { reason: comment ?? '' };
        if (kind === 'reject' && backToNodeCode) params.backToNodeCode = backToNodeCode;
        // 幂等键：同一任务同一方向的重复点击复用同一结果（连点 / 网络重放不产生二次审批）
        const res = await runtimeRecordApi.invokeAction(
          row.entityCode,
          row.recordId,
          kind,
          params,
          `${kind}-${row.taskId}`,
        );
        if (res.code === 200) ok += 1;
        else failed.push({ taskId: row.taskId, error: `服务端返回 code=${res.code}` });
      } catch (e) {
        failed.push({ taskId: row.taskId, error: (e as Error)?.message ?? '未知错误' });
      }
    }
    setSubmitting(false);
    if (failed.length) {
      // 失败明细全量进弹窗——「哪几条失败、为什么」本身就是要排查的信息，不能只截前 3 条
      setFailures(failed);
      message.warning(`成功 ${ok} 条，失败 ${failed.length} 条`);
    } else {
      // 动词必须与真实语义对齐：带回退节点时单据仍是审批中（原地返工），
      // 报「已驳回」会让审批人以为单子已经结了，而实际又回到了上游节点等人处理。
      const verb = kind === 'approve' ? '通过' : backToNodeCode ? '退回节点' : '驳回';
      message.success(`已${verb} ${ok} 条`);
    }
    setDecisionTarget(null);
    form.resetFields();
    setSelectedKeys([]);
    await loadTasks();
  };

  const runAssist = async (values: { assigneeId?: string; comment?: string }) => {
    if (!assist) return;
    const { kind, row } = assist;
    setSubmitting(true);
    try {
      const res =
        kind === 'transfer'
          ? await runtimeApprovalApi.transfer(row.taskId, String(values.assigneeId), values.comment)
          : await runtimeApprovalApi.addSign(row.taskId, String(values.assigneeId), values.comment);
      if (res.code === 200) {
        message.success(kind === 'transfer' ? '已转办' : '已加签');
        setAssist(null);
        assistForm.resetFields();
        await loadTasks();
      } else {
        message.error(`操作失败：code=${res.code}`);
      }
    } catch (e) {
      message.error(`操作失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  const runProblemAction = async (values: { reason?: string }) => {
    if (!problemAction) return;
    const { kind, row } = problemAction;
    setSubmitting(true);
    try {
      const res =
        kind === 'resume'
          ? await runtimeApprovalApi.resume(row.instanceId, values.reason)
          : await runtimeApprovalApi.terminate(row.instanceId, String(values.reason ?? ''));
      if (res.code === 200) {
        message.success(kind === 'resume' ? '已发起恢复（重跑审批人解析）' : '已终止流程');
        setProblemAction(null);
        problemForm.resetFields();
        await loadProblems();
      } else {
        message.error(`操作失败：code=${res.code}`);
      }
    } catch (e) {
      // 恢复失败常见原因：组织数据仍未修复、解析不出审批人——引擎保持挂起，不放行
      message.error(`操作失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setSubmitting(false);
    }
  };

  const openTrail = async (row: ApprovalTaskRow) => {
    if (!row.entityCode || !row.recordId) return;
    setTrailLoading(true);
    setTrail(null);
    try {
      const res = await runtimeApprovalApi.describe(row.entityCode, row.recordId);
      if (res.code === 200) setTrail(res.data);
      else message.error('审批轨迹加载失败');
    } catch (e) {
      message.error(`审批轨迹加载失败：${(e as Error)?.message ?? '未知错误'}`);
    } finally {
      setTrailLoading(false);
    }
  };

  const selectedRows = useMemo(
    () => rows.filter((r) => selectedKeys.includes(r.taskId)),
    [rows, selectedKeys],
  );

  // 只有待办可选（已办再批一次会被服务端的 task CAS 拒绝，属于无意义操作）
  const selectable = tab === 'pending' && rows.length > 0;

  const columns = [
    {
      title: '单据',
      key: 'biz',
      render: (_: unknown, row: ApprovalTaskRow) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{summaryText(row, 'claim_no') !== '-'
            ? summaryText(row, 'claim_no')
            : summaryText(row, 'advance_no')}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {summaryText(row, 'title')}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '金额',
      key: 'amount',
      width: 140,
      render: (_: unknown, row: ApprovalTaskRow) =>
        `${summaryText(row, 'amount')} ${summaryText(row, 'currency') === '-'
          ? ''
          : summaryText(row, 'currency')}`,
    },
    {
      title: '当前节点',
      key: 'node',
      width: 140,
      render: (_: unknown, row: ApprovalTaskRow) => (
        <Tag color={row.pending ? 'processing' : 'default'}>{row.nodeCode}</Tag>
      ),
    },
    {
      title: '单据状态',
      key: 'status',
      width: 100,
      render: (_: unknown, row: ApprovalTaskRow) => summaryText(row, 'status'),
    },
    {
      title: '任务状态',
      key: 'taskStatus',
      width: 110,
      render: (_: unknown, row: ApprovalTaskRow) => {
        const meta = STATUS_META[row.status] ?? { color: 'default', label: row.status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '到达时间',
      key: 'createdAt',
      width: 180,
      render: (_: unknown, row: ApprovalTaskRow) =>
        row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN') : '-',
    },
    {
      title: '审批意见',
      key: 'comment',
      render: (_: unknown, row: ApprovalTaskRow) => row.comment || '-',
    },
    {
      title: '操作',
      key: 'ops',
      width: 300,
      render: (_: unknown, row: ApprovalTaskRow) => (
        <Space size={4} wrap>
          {row.pending && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckOutlined />}
                onClick={() => void openDecision('approve', [row])}
              >
                通过
              </Button>
              <Button
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => void openDecision('reject', [row])}
              >
                驳回
              </Button>
              <Button size="small" onClick={() => { setAssist({ kind: 'transfer', row }); assistForm.resetFields(); }}>
                转办
              </Button>
              <Button size="small" onClick={() => { setAssist({ kind: 'addSign', row }); assistForm.resetFields(); }}>
                加签
              </Button>
            </>
          )}
          <Button
            size="small"
            icon={<HistoryOutlined />}
            onClick={() => void openTrail(row)}
            disabled={!row.entityCode || !row.recordId}
          >
            轨迹
          </Button>
        </Space>
      ),
    },
  ];

  const problemColumns = [
    {
      title: '单据坐标',
      key: 'coord',
      render: (_: unknown, row: ApprovalInstanceRow) => (
        <Space direction="vertical" size={0}>
          <Typography.Text code>{row.entityCode}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.recordId}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '当前节点',
      key: 'node',
      width: 160,
      render: (_: unknown, row: ApprovalInstanceRow) => row.currentNode || '-',
    },
    {
      title: '发起人',
      key: 'startedBy',
      width: 120,
      render: (_: unknown, row: ApprovalInstanceRow) => row.startedBy || '-',
    },
    {
      title: '更新时间',
      key: 'updatedAt',
      width: 180,
      render: (_: unknown, row: ApprovalInstanceRow) =>
        row.updatedAt ? new Date(row.updatedAt).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      key: 'ops',
      width: 200,
      render: (_: unknown, row: ApprovalInstanceRow) => (
        <Space size={4}>
          {row.suspended && (
            <Button
              size="small"
              type="primary"
              onClick={() => { setProblemAction({ kind: 'resume', row }); problemForm.resetFields(); }}
            >
              恢复
            </Button>
          )}
          {!row.terminated && (
            <Button
              size="small"
              danger
              onClick={() => { setProblemAction({ kind: 'terminate', row }); problemForm.resetFields(); }}
            >
              终止
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const inboxColumns = [
    {
      title: '时间',
      key: 'createdAt',
      width: 180,
      render: (_: unknown, row: ApprovalInboxRow) =>
        row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN') : '-',
    },
    {
      title: '类型',
      key: 'type',
      width: 120,
      render: (_: unknown, row: ApprovalInboxRow) => <Tag>{row.type || '-'}</Tag>,
    },
    {
      title: '标题',
      key: 'title',
      render: (_: unknown, row: ApprovalInboxRow) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong={!row.read}>{row.title || '-'}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.body || ''}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'read',
      width: 90,
      render: (_: unknown, row: ApprovalInboxRow) =>
        row.read ? <Tag color="default">已读</Tag> : <Tag color="processing">未读</Tag>,
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Card
        title="审批中心"
        extra={
          <Space>
            {selectable && selectedKeys.length > 0 && (
              <>
                <Typography.Text type="secondary">已选 {selectedKeys.length} 条</Typography.Text>
                <Button
                  size="small"
                  type="primary"
                  onClick={() => void openDecision('approve', selectedRows)}
                >
                  批量通过
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={() => void openDecision('reject', selectedRows)}
                >
                  批量驳回
                </Button>
              </>
            )}
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => {
                if (tab === 'pending' || tab === 'decided') void loadTasks();
                else if (tab === 'problems') void loadProblems();
                else void loadInbox();
              }}
            >
              刷新
            </Button>
          </Space>
        }
      >
        <Tabs
          activeKey={tab}
          onChange={(k) => {
            setTab(k as TabKey);
            setPage(1);
          }}
          items={[
            { key: 'pending', label: '待我审批' },
            { key: 'decided', label: '我已审批' },
            { key: 'problems', label: '问题单据' },
            { key: 'inbox', label: '通知' },
          ]}
        />

        {(tab === 'pending' || tab === 'decided') && (
          <Table<ApprovalTaskRow>
            rowKey="taskId"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={rows}
            locale={{ emptyText: <Empty description="没有数据" /> }}
            rowSelection={
              selectable
                ? { selectedRowKeys: selectedKeys, onChange: (keys) => setSelectedKeys(keys as string[]) }
                : undefined
            }
            pagination={{
              current: page,
              pageSize: 20,
              total,
              onChange: (p) => setPage(p),
            }}
          />
        )}

        {tab === 'problems' && (
          <>
            <Space style={{ marginBottom: 12 }} wrap>
              <Typography.Text type="secondary">按阶段排障：</Typography.Text>
              <Select
                size="small"
                style={{ minWidth: 260 }}
                value={phase}
                options={PHASE_OPTIONS}
                onChange={(v) => {
                  setPhase(v);
                  setPage(1);
                }}
              />
              {problemsTruncated && (
                <Alert
                  type="warning"
                  showIcon
                  message="该阶段问题单不止这些（单次上限 200 条），请结合更新时间继续排查。"
                />
              )}
            </Space>
            <Table<ApprovalInstanceRow>
              rowKey="instanceId"
              size="small"
              loading={problemsLoading}
              columns={problemColumns}
              dataSource={problems}
              locale={{ emptyText: <Empty description="该阶段没有问题单据" /> }}
              pagination={false}
            />
          </>
        )}

        {tab === 'inbox' && (
          <>
            <Space style={{ marginBottom: 12 }}>
              <Typography.Text type="secondary">只看未读</Typography.Text>
              <Switch checked={unreadOnly} onChange={setUnreadOnly} />
            </Space>
            <Table<ApprovalInboxRow>
              rowKey="id"
              size="small"
              loading={inboxLoading}
              columns={inboxColumns}
              dataSource={inboxRows}
              locale={{ emptyText: <Empty description="收件箱是空的" /> }}
              pagination={{ pageSize: 20 }}
            />
          </>
        )}
      </Card>

      <Modal
        open={decisionTarget !== null}
        title={decisionTarget?.kind === 'approve' ? '审批通过' : '审批驳回'}
        confirmLoading={submitting}
        okButtonProps={{ danger: decisionTarget?.kind === 'reject' }}
        onCancel={() => {
          setDecisionTarget(null);
          form.resetFields();
        }}
        onOk={async () => {
          if (!decisionTarget) return;
          let values: { comment?: string; backToNodeCode?: string };
          try {
            values = await form.validateFields();
          } catch {
            return;
          }
          await runDecision(
            decisionTarget.kind,
            decisionTarget.rows,
            String(values.comment ?? ''),
            values.backToNodeCode,
          );
        }}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          共 {decisionTarget?.rows.length ?? 0} 条；审批人资格由服务端按{' '}
          <Typography.Text code>wf_task.assignee_id</Typography.Text>{' '}
          指派快照判定，非本人待办会被拒绝（403）。
        </Typography.Paragraph>
        <Form form={form} layout="vertical">
          <Form.Item
            name="comment"
            label={decisionTarget?.kind === 'approve' ? '审批意见' : '驳回理由'}
            rules={
              decisionTarget?.kind === 'reject'
                ? [{ required: true, message: '驳回必须填写理由' }]
                : []
            }
          >
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="请输入" />
          </Form.Item>
          {decisionTarget?.kind === 'reject' && (
            <Form.Item
              name="backToNodeCode"
              label="退回节点"
              extra="不选 = 整单驳回（流程终结，单据置为已驳回）；选中则退回该节点重新派单，单据继续处于审批中。批量驳回仅支持整单驳回。"
            >
              <Select
                allowClear
                placeholder="整单驳回"
                disabled={backNodeOptions.length === 0}
                options={backNodeOptions}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        open={failures !== null}
        title="决策失败明细"
        footer={
          <Button
            onClick={() => {
              setFailures(null);
              void loadTasks();
            }}
          >
            关闭并刷新
          </Button>
        }
        onCancel={() => setFailures(null)}
      >
        <Table<DecisionFailure>
          rowKey="taskId"
          size="small"
          pagination={false}
          dataSource={failures ?? []}
          columns={[
            { title: '任务', dataIndex: 'taskId', width: 160 },
            { title: '原因', dataIndex: 'error' },
          ]}
        />
      </Modal>

      <Modal
        open={assist !== null}
        title={assist?.kind === 'transfer' ? '转办待办' : '加签会办'}
        confirmLoading={submitting}
        onCancel={() => {
          setAssist(null);
          assistForm.resetFields();
        }}
        onOk={() => void assistForm.validateFields().then(runAssist).catch(() => undefined)}
      >
        <Typography.Paragraph type="secondary">
          {assist?.kind === 'transfer'
            ? '转办：把我的这条待办交由他人办理（原任务置「已转交」，新任务指向接手人，审计保留完整链路）。'
            : '加签：在当前节点新增一条指向被加签人的待办，节点完成判定（会签 / 单签）自动计入。'}
        </Typography.Paragraph>
        <Form form={assistForm} layout="vertical">
          <Form.Item
            name="assigneeId"
            label="接手人 / 被加签人"
            rules={[{ required: true, message: '请填写账户 id' }]}
          >
            <Input placeholder="账户 id（如 1002）" maxLength={64} />
          </Form.Item>
          <Form.Item name="comment" label={assist?.kind === 'transfer' ? '转办留言' : '加签留言'}>
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="选填，审计留痕" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={problemAction !== null}
        title={problemAction?.kind === 'resume' ? '恢复挂起流程' : '强制终止流程'}
        confirmLoading={submitting}
        okButtonProps={{ danger: problemAction?.kind === 'terminate' }}
        onCancel={() => {
          setProblemAction(null);
          problemForm.resetFields();
        }}
        onOk={() =>
          void problemForm.validateFields().then(runProblemAction).catch(() => undefined)
        }
      >
        <Typography.Paragraph type="secondary">
          {problemAction?.kind === 'resume'
            ? '按原指派规则重新解析审批人。解析仍不出人时引擎保持挂起并明确报错（请先修复组织 / 指派数据）。'
            : '不可逆处置：流程进入 TERMINATED 终态，单据不再可重提。'}
        </Typography.Paragraph>
        <Form form={problemForm} layout="vertical">
          <Form.Item
            name="reason"
            label="原因"
            rules={
              problemAction?.kind === 'terminate'
                ? [{ required: true, message: '终止必须填写原因' }]
                : []
            }
          >
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="请输入" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="审批轨迹"
        width={520}
        open={trail !== null || trailLoading}
        onClose={() => setTrail(null)}
      >
        {trailLoading && <Typography.Text type="secondary">加载中…</Typography.Text>}
        {trail && !trail.started && <Empty description="该单据未接入审批流程" />}
        {trail?.started && (
          <Timeline
            items={(trail.trail ?? []).map((t) => ({
              color:
                t.status === 'APPROVED'
                  ? 'green'
                  : t.status === 'REJECTED'
                    ? 'red'
                    : t.status === 'PENDING'
                      ? 'blue'
                      : 'gray',
              children: (
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>
                    {t.nodeCode} · {STATUS_META[t.status]?.label ?? t.status}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    办理人 {t.assigneeId}
                    {t.decidedAt ? ` · ${new Date(t.decidedAt).toLocaleString('zh-CN')}` : ''}
                  </Typography.Text>
                  {t.comment && <Typography.Text>{t.comment}</Typography.Text>}
                </Space>
              ),
            }))}
          />
        )}
        {trail?.started && (trail.pendingTasks ?? []).length > 0 && (
          <Typography.Text type="secondary">
            当前待办节点：{trail.pendingTasks?.map((t) => t.nodeCode).join('、')}
          </Typography.Text>
        )}
      </Drawer>
    </div>
  );
};

export default ApprovalCenterPage;
