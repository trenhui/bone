import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button, Card, Drawer, Empty, Form, Input, Modal, Space, Table, Tabs, Timeline,
  Tag, Typography, message,
} from 'antd';
import {
  CheckOutlined, CloseOutlined, HistoryOutlined, ReloadOutlined,
} from '@ant-design/icons';
import {
  runtimeApprovalApi, runtimeRecordApi,
  type ApprovalDescribe, type ApprovalTaskRow,
} from '../services/metadataApi';

/** 任务状态 → 渲染口径（PENDING 之外均为已表态 / 已作废）。 */
const STATUS_META: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'processing', label: '待我审批' },
  APPROVED: { color: 'success', label: '已通过' },
  REJECTED: { color: 'error', label: '已驳回' },
  TRANSFERRED: { color: 'warning', label: '已转交' },
  CANCELLED: { color: 'default', label: '已作废' },
};

/**
 * 审批意见字段名随决策方向不同（服务端入参契约）：
 * approve → comment（可空），reject → reason（必填，驳回必须给出理由）。
 */
type DecisionKind = 'approve' | 'reject';

const ApprovalCenterPage: React.FC = () => {
  const [tab, setTab] = useState<'pending' | 'decided'>('pending');
  const [rows, setRows] = useState<ApprovalTaskRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [submitting, setSubmitting] = useState(false);

  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [decisionTarget, setDecisionTarget] =
    useState<{ kind: DecisionKind; rows: ApprovalTaskRow[] } | null>(null);
  const [trail, setTrail] = useState<ApprovalDescribe | null>(null);
  const [trailLoading, setTrailLoading] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res =
        tab === 'pending'
          ? await runtimeApprovalApi.pending(page, 20)
          : await runtimeApprovalApi.decided(page, 20);
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

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setSelectedKeys([]);
  }, [tab]);

  const summaryText = (row: ApprovalTaskRow, key: string): string => {
    const value = row.summary?.[key];
    return value === undefined || value === null ? '-' : String(value);
  };

  const runDecision = async (kind: DecisionKind, targets: ApprovalTaskRow[], comment?: string) => {
    setSubmitting(true);
    let ok = 0;
    const failed: string[] = [];
    for (const row of targets) {
      if (!row.entityCode || !row.recordId) {
        failed.push(row.taskId);
        continue;
      }
      try {
        // 幂等键：同一任务同一方向的重复点击复用同一结果（连点 / 网络重放不产生二次审批）
        const res = await runtimeRecordApi.invokeAction(
          row.entityCode,
          row.recordId,
          kind,
          kind === 'approve' ? { comment: comment ?? '' } : { reason: comment ?? '' },
          `${kind}-${row.taskId}`,
        );
        if (res.code === 200) ok += 1;
        else failed.push(row.taskId);
      } catch (e) {
        failed.push(`${row.taskId}(${(e as Error)?.message ?? '异常'})`);
      }
    }
    setSubmitting(false);
    if (failed.length) {
      message.warning(`成功 ${ok} 条，失败 ${failed.length} 条：${failed.slice(0, 3).join('；')}`);
    } else {
      message.success(`已${kind === 'approve' ? '通过' : '驳回'} ${ok} 条`);
    }
    setDecisionTarget(null);
    form.resetFields();
    setSelectedKeys([]);
    await load();
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
      width: 220,
      render: (_: unknown, row: ApprovalTaskRow) => (
        <Space size={4}>
          {row.pending && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckOutlined />}
                onClick={() => setDecisionTarget({ kind: 'approve', rows: [row] })}
              >
                通过
              </Button>
              <Button
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => setDecisionTarget({ kind: 'reject', rows: [row] })}
              >
                驳回
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
                  onClick={() => setDecisionTarget({ kind: 'approve', rows: selectedRows })}
                >
                  批量通过
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={() => setDecisionTarget({ kind: 'reject', rows: selectedRows })}
                >
                  批量驳回
                </Button>
              </>
            )}
            <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>
              刷新
            </Button>
          </Space>
        }
      >
        <Tabs
          activeKey={tab}
          onChange={(k) => {
            setTab(k as 'pending' | 'decided');
            setPage(1);
          }}
          items={[
            { key: 'pending', label: '待我审批' },
            { key: 'decided', label: '我已审批' },
          ]}
        />
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
          let comment = '';
          try {
            const values = await form.validateFields();
            comment = String(values.comment ?? '');
          } catch {
            return;
          }
          await runDecision(decisionTarget.kind, decisionTarget.rows, comment);
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
