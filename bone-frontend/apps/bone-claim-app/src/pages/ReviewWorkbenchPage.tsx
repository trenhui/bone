import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, NodeIndexOutlined } from '@ant-design/icons';
import {
  claimApi,
  decisionApi,
  type ClaimHeadProjection,
  type ClaimStatusName,
  type Decision,
  type DecisionExecutionResult,
  type DecisionTraceNode,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { explainNode, toneColor, type ExplainFact } from '../services/decisionExplain';
import { resolveErrorMessage } from '@bone/shared-utils';

/**
 * 核赔工作台 · 待办审核页（synapClaim 一期）。
 *
 * 覆盖 RQ-MVP-005 高风险/低置信转人工主链路：待办列表（默认 PENDING_ADJUST）
 * → 领取 → 人工决策（通过/拒赔/补件）→ ≥1 万发起双人复核。
 * AI 建议与证据链展示为后续迭代（A2/A3 聚合落地后）。
 */

/**
 * 自动通过结论消费结果的中文说明。
 *
 * 「已自动通过但资金没动」是最容易被当成系统故障（或更糟：被当成已放款）的状态，因此每种跳过原因
 * 都要能说清「为什么」，而不是只显示一个英文码。
 */
const CONSUMPTION_LABEL: Record<string, string> = {
  APPLIED: '已自动结案并建结算单（等待资金审核/支付）',
  AUTO_APPROVE_DISABLED: '自动放款开关未开（策略待签核），案件保持待人工核赔',
  CAP_EXCEEDED: '理算金额超单笔自动放款上限，转人工核赔',
  ALREADY_SETTLED: '该案已结案，无需重复出金',
};

const STATUS_TAG: Record<ClaimStatusName, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACCEPTED: { color: 'blue', label: '已受理' },
  PENDING_PRE_EXAM: { color: 'processing', label: '待初审分配' },
  PRE_EXAMINING: { color: 'processing', label: '初审中' },
  AUTO_PRE_EXAMINING: { color: 'cyan', label: '自动初审中' },
  PRE_EXAMINED: { color: 'geekblue', label: '已初审' },
  EVIDENCE_SUBMITTING: { color: 'orange', label: '补录中' },
  PENDING_ADJUST: { color: 'gold', label: '待核赔' },
  ADJUSTING: { color: 'processing', label: '核赔中' },
  ADJUSTED: { color: 'green', label: '已审核' },
  REVIEWING: { color: 'purple', label: '复核中' },
  CLOSED_REJECTED: { color: 'red', label: '已撤销/拒赔' },
  FINISHED: { color: 'green', label: '已结案' },
  APPEALING: { color: 'volcano', label: '申诉中' },
};

const REJECT_TYPES = [
  { value: '03', label: '03 就诊医院不符合责任医院规则' },
  { value: '04', label: '04 诊断不符合责任免赔规则' },
  { value: '09', label: '09 无剩余保额（已封顶）' },
  { value: '20', label: '20 目录外费用不赔付' },
  { value: '30', label: '30 责任范围除外' },
  { value: '40', label: '40 风险标记（转人工核实）' },
];

/**
 * 同一类加载失败共用 message key：重复触发时 antd 会**替换**而非堆叠多条 toast。
 *
 * 开发态 React StrictMode 会双挂载、并发触发两次 load()，此前失败会并列两条
 * 「待办加载失败」（用户侧表现为成倍刷屏）。
 */
const MSG_KEY_LOAD = 'claim-review-load';

export function ReviewWorkbenchPage(): ReactElement {
  const [rows, setRows] = useState<ClaimHeadProjection[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<ClaimStatusName | undefined>(undefined);
  const [decisionTarget, setDecisionTarget] = useState<ClaimHeadProjection | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [decisionForm] = Form.useForm<{ decision: Decision; approvedAmount: number; comment: string; rejectType: string }>();

  const [traceTarget, setTraceTarget] = useState<ClaimHeadProjection | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);
  const [traceMeta, setTraceMeta] = useState<DecisionExecutionResult | null>(null);
  const [traceNodes, setTraceNodes] = useState<DecisionTraceNode[]>([]);

  const onOpenTrace = async (row: ClaimHeadProjection) => {
    setTraceTarget(row);
    setTraceLoading(true);
    setTraceMeta(null);
    setTraceNodes([]);
    try {
      const exec = await decisionApi.execute(String(row.claimId));
      const traceId = exec?.data?.traceId;
      if (!traceId) {
        message.error('未生成决策轨迹');
        return;
      }
      const nodes = await decisionApi.trace(String(row.claimId), traceId);
      setTraceMeta(exec?.data ?? null);
      setTraceNodes(nodes?.data ?? []);
    } catch {
      message.error('决策轨迹加载失败');
    } finally {
      setTraceLoading(false);
    }
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await claimApi.page({
        statuses: statusFilter ? [statusFilter] : ['PENDING_ADJUST'],
        page: 1,
        size: 50,
      });
      setRows(resp?.data?.records ?? []);
    } catch (err) {
      // 展示后端真实原因：createApiClient 已把 errorCode / message 挂到错误对象上，
      // 先前这里把它一律概括成「待办加载失败」，用户只能截图求助、无法自助判断。
      message.error({ content: resolveErrorMessage(err, '待办加载失败'), key: MSG_KEY_LOAD });
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const onDecision = async () => {
    if (!decisionTarget) return;
    const values = await decisionForm.validateFields();
    setSubmitting(true);
    try {
      await claimApi.decide(decisionTarget.claimId, {
        decision: values.decision,
        comment: values.comment,
        rejectType: values.rejectType,
        approvedAmount: values.approvedAmount,
      });
      message.success('决策已生效');
      setDecisionTarget(null);
      decisionForm.resetFields();
      void load();
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_REJECT_REASON_REQUIRED') {
        message.error('拒赔必填五要素与受控原因码');
      } else if (errorCode === 'SC_CLAIM_STATUS_CONFLICT') {
        message.error('案件状态已变化，请刷新后重试');
      } else {
        message.error('决策提交失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openDecision = (row: ClaimHeadProjection, preset: Decision) => {
    setDecisionTarget(row);
    decisionForm.setFieldsValue({
      decision: preset,
      approvedAmount: row.approvedAmount ?? row.claimAmount,
      comment: '',
      rejectType: undefined,
    });
  };

  const pendingCount = rows.filter((r) => r.status === 'PENDING_ADJUST').length;
  const riskCount = rows.filter((r) => r.claimAmount >= 10000).length;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="核赔工作台" />
      <Space size="large" wrap style={{ marginBottom: 16 }}>
        <Card size="small">
          <Statistic title="待核赔" value={pendingCount} />
        </Card>
        <Card size="small">
          <Statistic title="大额（≥1万，需双人复核）" value={riskCount} />
        </Card>
        <Card size="small">
          <Statistic title="列表合计" value={rows.length} />
        </Card>
      </Space>

      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="AI 仅出建议，资金与责任结论以确定性规则为准；拒赔须填五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限）。"
      />

      <Card
        size="small"
        extra={
          <Space>
            <Select<ClaimStatusName | undefined>
              allowClear
              placeholder="状态筛选"
              style={{ width: 200 }}
              value={statusFilter}
              onChange={(v) => setStatusFilter(v)}
              options={[
                { value: 'PENDING_ADJUST', label: '待核赔' },
                { value: 'ADJUSTING', label: '核赔中' },
                { value: 'ADJUSTED', label: '已审核' },
                { value: 'REVIEWING', label: '复核中' },
                { value: 'FINISHED', label: '已结案' },
                { value: 'CLOSED_REJECTED', label: '已撤销/拒赔' },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table<ClaimHeadProjection>
          rowKey="claimId"
          loading={loading}
          dataSource={rows}
          pagination={{ pageSize: 20 }}
          columns={[
            { title: '报案号', dataIndex: 'claimNo', width: 200 },
            { title: '保单号', dataIndex: 'policyNo', width: 140 },
            {
              title: '申请金额',
              dataIndex: 'claimAmount',
              width: 120,
              render: (v: number) => `¥${Number(v).toFixed(2)}`,
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 140,
              render: (v: ClaimStatusName) => {
                const tag = STATUS_TAG[v] ?? { color: 'default', label: v };
                return <Tag color={tag.color}>{tag.label}</Tag>;
              },
            },
            {
              title: '大额',
              width: 90,
              render: (_, r) => (r.claimAmount >= 10000 ? <Badge color="red" text="≥1万" /> : '-'),
            },
            { title: '第一审核人', dataIndex: 'firstReviewerId', width: 140 },
            { title: '受理时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              width: 260,
              render: (_, row) => (
                <Space size={4} wrap>
                  <Button
                    type="link"
                    size="small"
                    icon={<NodeIndexOutlined />}
                    onClick={() => void onOpenTrace(row)}
                  >
                    决策图
                  </Button>
                  {row.status === 'PENDING_ADJUST' && (
                    <Button
                      type="link"
                      size="small"
                      onClick={async () => {
                        await claimApi.claimAdjustTask(row.claimId);
                        message.success('已领取');
                        void load();
                      }}
                    >
                      领取
                    </Button>
                  )}
                  {row.status === 'ADJUSTING' && (
                    <>
                      <Button type="link" size="small" onClick={() => openDecision(row, 'APPROVED')}>
                        通过
                      </Button>
                      <Button type="link" size="small" danger onClick={() => openDecision(row, 'REJECTED')}>
                        拒赔
                      </Button>
                      <Button type="link" size="small" onClick={() => openDecision(row, 'REQUEST_MORE_EVIDENCE')}>
                        补件
                      </Button>
                    </>
                  )}
                  {row.status === 'ADJUSTED' && row.claimAmount >= 10000 && (
                    <Button
                      type="link"
                      size="small"
                      onClick={async () => {
                        await claimApi.submitForSecondReview(row.claimId);
                        message.success('已发起双人复核');
                        void load();
                      }}
                    >
                      发起双人复核
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={`审核决策 · ${decisionTarget?.claimNo ?? ''}`}
        open={decisionTarget !== null}
        onOk={onDecision}
        confirmLoading={submitting}
        onCancel={() => {
          setDecisionTarget(null);
          decisionForm.resetFields();
        }}
      >
        <Form form={decisionForm} layout="vertical">
          <Form.Item name="decision" label="决策" rules={[{ required: true }]}>
            <Select<Decision>
              options={[
                { value: 'APPROVED', label: '通过（进入理算与资金）' },
                { value: 'REJECTED', label: '拒赔（必填五要素）' },
                { value: 'REQUEST_MORE_EVIDENCE', label: '要求补件' },
              ]}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.decision !== cur.decision}
          >
            {({ getFieldValue }) =>
              getFieldValue('decision') === 'APPROVED' ? (
                <Form.Item
                  name="approvedAmount"
                  label="核定金额（≤ 申请金额）"
                  rules={[{ required: true, message: '通过必填核定金额' }]}
                >
                  <InputNumber min={0} precision={2} style={{ width: '100%' }} addonAfter="元" />
                </Form.Item>
              ) : getFieldValue('decision') === 'REJECTED' ? (
                <>
                  <Form.Item
                    name="comment"
                    label="拒赔五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限）"
                    rules={[{ required: true, message: '拒赔必填五要素' }]}
                  >
                    <Input.TextArea rows={4} placeholder="事实认定：…；条款依据：…；证据位置：…；申诉渠道：…；法定时限：…" />
                  </Form.Item>
                  <Form.Item
                    name="rejectType"
                    label="受控拒赔原因码"
                    rules={[{ required: true, message: '必须选择受控原因码（禁自由文本）' }]}
                  >
                    <Select options={REJECT_TYPES} placeholder="选择原因码" />
                  </Form.Item>
                </>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`决策图轨迹回放 · ${traceTarget?.claimNo ?? ''}`}
        width={560}
        open={traceTarget !== null}
        loading={traceLoading}
        onClose={() => setTraceTarget(null)}
      >
        {traceMeta && (
          <Alert
            type={traceMeta.decisionResult === 'AUTO_APPROVE' && traceMeta.consumptionReason !== 'APPLIED' ? 'warning' : 'info'}
            showIcon
            style={{ marginBottom: 16 }}
            message={`结论层级 ${traceMeta.decisionTier} · 结果 ${traceMeta.decisionResult} · 节点数 ${traceMeta.traceCount}`}
            description={
              traceMeta.consumptionReason
                ? `自动通过消费：${CONSUMPTION_LABEL[traceMeta.consumptionReason] ?? traceMeta.consumptionReason}`
                : 'AI 仅出建议与置信度，最终责任/资金结论以确定性分诊规则为准（fail-safe 降级时提示规则审查模式）。'
            }
          />
        )}
        <Timeline
          items={traceNodes.map((n) => ({
            children: (
              <div>
                <Space wrap>
                  <Tag color="blue">{n.nodeType}</Tag>
                  <strong>{n.nodeName}</strong>
                  <Tag color={n.actionResult === 'SUCCESS' ? 'green' : 'red'}>{n.actionResult}</Tag>
                  <Typography.Text type="secondary">{n.durationMs}ms</Typography.Text>
                </Space>
                <div style={{ marginTop: 4 }}>{n.inputSummary}</div>
                {(() => {
                  const facts: ExplainFact[] = explainNode(n);
                  return facts.length > 0 ? (
                    <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {facts.map((f) => (
                        <div key={f.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <Typography.Text type="secondary" style={{ minWidth: 104 }}>
                            {f.label}
                          </Typography.Text>
                          <Tag color={toneColor(f.tone)} style={{ marginInlineEnd: 0 }}>
                            {f.value}
                          </Tag>
                        </div>
                      ))}
                    </div>
                  ) : null;
                })()}
                {n.output && (
                  <details style={{ marginTop: 6 }}>
                    <summary style={{ cursor: 'pointer', color: '#8c8c8c', fontSize: 12 }}>
                      原始输出
                    </summary>
                    <pre
                      style={{
                        margin: '4px 0 0',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-all',
                        fontSize: 12,
                        color: '#8c8c8c',
                      }}
                    >
                      {n.output}
                    </pre>
                  </details>
                )}
              </div>
            ),
          }))}
        />
      </Drawer>
    </div>
  );
}
