import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Drawer,
  Empty,
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
import { ReloadOutlined, NodeIndexOutlined, CheckCircleOutlined } from '@ant-design/icons';
import {
  claimApi,
  reviewTaskApi,
  type ClaimHeadProjection,
  type Decision,
  type DecisionTraceNode,
  type ReviewTaskStatus,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { explainNode, toneColor, type ExplainFact } from '../services/decisionExplain';
import { claimStatusMeta } from '../services/claimStatus';
import { rejectReasonGroups } from '../services/rejectReason';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 核赔工作台 · 待办审核页（synapClaim 一期）。
 *
 * 覆盖 RQ-MVP-005 高风险/低置信转人工主链路：待办列表 → 领取 → 人工决策（通过/拒赔/补件）
 * → ≥1 万发起双人复核 → 复核结论（44→50/45）。
 *
 * <p><b>取数走工作台契约而非案件中心</b>：列表与决策都打 `reviewTaskApi`（`/api/v1/review/tasks`），
 * 由后端 `ReviewTaskStatusMapper` 决定「任务维 → 案件状态集」。前端自己拼 `statuses` 会造成
 * 「领取后案件从待办消失」（详见 `services/claimApi.ts` 的说明）。
 */

/**
 * 任务维筛选（契约 4 值 → 3 个选项）。
 *
 * <p>`ASSIGNED` 不单列：后端把它与 `PROCESSING` 映到同一个 `ADJUSTING(41)`（案件模型没有
 * 「已分配未领取」这一态），分列会让用户以为能筛出不同数据，实际是同一批。
 *
 * <p>默认不选（`undefined`）= 后端默认待办口径（40+41），**不是**「全部状态」——
 * 把已结案案件混进待办是最常见的工单误派来源。
 */
const TASK_FILTER_OPTIONS: { value: ReviewTaskStatus; label: string }[] = [
  { value: 'WAITING', label: '待领取' },
  { value: 'PROCESSING', label: '处理中（已领取）' },
  { value: 'DONE', label: '已处理（含复核中/已结案）' },
];

/**
 * 自动通过结论的「消费结果」目前**无法在只读视图展示**：`consumptionReason`
 * （APPLIED / AUTO_APPROVE_DISABLED / CAP_EXCEEDED / ALREADY_SETTLED）只存在于
 * `POST /decision/execute` 的响应里，未落 `claim_decision_execution` 任何列。
 *
 * <p>先前页面靠「点开决策图 = 重新执行一次决策」把这个字段取回来，代价是查看动作带写副作用
 * （详见 `services/claimApi.ts` 的 A3 段说明）。现在改为只读轨迹，这个字段暂时缺席；
 * 要恢复它得给执行记录补一列，属后端取数改造，不在前端兜。
 */

/**
 * 状态文案与配色统一走 {@link claimStatusMeta}（`services/claimStatus.ts`）。
 *
 * <p>本页原有一份 `STATUS_TAG`、理赔进度页另有一份 `STATUS_LABEL`，两处条目集不一致，
 * 后端新增状态时只会有一页漏改（表现为某一页直接显示 `CLOSED_REJECTED` 这样的枚举名）。
 * 现在字典只有一份，未知状态统一降级为「原始枚举名 + 灰色」。
 */

/** 拒赔受控原因码：单一真源在 `services/rejectReason.ts`（含引擎实际会产出的 05/06）。 */
const REJECT_TYPE_GROUPS = rejectReasonGroups();

/**
 * 同一类加载失败共用 message key：重复触发时 antd 会**替换**而非堆叠多条 toast。
 *
 * 开发态 React StrictMode 会双挂载、并发触发两次 load()，此前失败会并列两条
 * 「待办加载失败」（用户侧表现为成倍刷屏）。
 */
const MSG_KEY_LOAD = 'claim-review-load';
/** 动作类失败另用一键：与加载失败区分，避免动作错误把「待办加载失败」替换掉。 */
const MSG_KEY_ACTION = 'claim-review-action';

export function ReviewWorkbenchPage(): ReactElement {
  const [rows, setRows] = useState<ClaimHeadProjection[]>([]);
  const [loading, setLoading] = useState(false);
  /** 行级在途标记：只锁当前行，避免整表按钮变灰被误认为页面卡死。 */
  const [actingId, setActingId] = useState<string | null>(null);
  const [taskFilter, setTaskFilter] = useState<ReviewTaskStatus | undefined>(undefined);
  const [decisionTarget, setDecisionTarget] = useState<ClaimHeadProjection | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [decisionForm] = Form.useForm<{ decision: Decision; approvedAmount: number; comment: string; rejectType: string }>();

  /**
   * 复核结论（44→50/45）。
   *
   * <p>这是本页此前的**功能缺口**：页面只能「发起双人复核」（42→44），却没有任何出口给出复核结论，
   * 于是案件进入复核中后永远结不了案——`auto-approve` 只覆盖 12→50，不处理 44。
   * 后端 `POST /{id}/second-review/complete` 一直存在，前端从未接。
   */
  const [reviewTarget, setReviewTarget] = useState<ClaimHeadProjection | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const onCompleteSecondReview = async (approved: boolean) => {
    if (!reviewTarget) return;
    setReviewing(true);
    try {
      await claimApi.completeSecondReview(reviewTarget.claimId, approved);
      message.success(approved ? '复核通过，案件已结案' : '复核拒绝，案件已转拒赔生效');
      setReviewTarget(null);
      void load();
    } catch (err) {
      const code = resolveErrorCode(err);
      const text =
        code === 'SC_CLAIM_REVIEWER_CONFLICT'
          ? '复核人不能是本案第一审核人（职责分离），请换账号操作'
          : code === 'SC_CLAIM_STATUS_CONFLICT'
            ? '案件状态已变化，请刷新后重试'
            : resolveErrorMessage(err, '复核结论提交失败');
      message.error({ content: text, key: MSG_KEY_ACTION });
    } finally {
      setReviewing(false);
    }
  };

  const [traceTarget, setTraceTarget] = useState<ClaimHeadProjection | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);
  /** 「无执行记录」的空态文案：与「加载失败」必须分开，否则 404 会被当成系统故障。 */
  const [traceEmpty, setTraceEmpty] = useState<string | null>(null);
  const [traceNodes, setTraceNodes] = useState<DecisionTraceNode[]>([]);

  /**
   * 决策图轨迹：**只读**。
   *
   * <p>先前这里先调 `POST /claims/{id}/decision/execute` 拿 `traceId`，于是「查看决策图」实际是
   * 「重跑一次决策」：同案已有活跃执行时直接 409 `SC_CLAIM_DECISION_ACTIVE_CONFLICT`，
   * 没活跃执行时则新开一次执行（AUTO_APPROVE 还会结案并建结算单）——查看动作产生写副作用。
   * 现在改走工作台契约 `GET /review/tasks/{id}/trace`，取最近一次的节点序列，零副作用。
   */
  const onOpenTrace = async (row: ClaimHeadProjection) => {
    setTraceTarget(row);
    setTraceLoading(true);
    setTraceEmpty(null);
    setTraceNodes([]);
    try {
      const nodes = await reviewTaskApi.trace(String(row.claimId));
      const list = nodes?.data ?? [];
      setTraceNodes(list);
      if (list.length === 0) {
        setTraceEmpty('该案有执行记录但没有轨迹节点（执行可能未走完）');
      }
    } catch (err) {
      if (resolveErrorCode(err) === 'SC_CLAIM_DECISION_NOT_FOUND') {
        // 没跑过决策 = 空态，不是故障。
        setTraceEmpty(resolveErrorMessage(err, '该案尚无决策执行记录'));
      } else {
        message.error({ content: resolveErrorMessage(err, '决策轨迹加载失败'), key: MSG_KEY_ACTION });
        // 抽屉不要留空白面板：失败原因同时在抽屉内留一行，用户关掉 toast 后仍能看到状态。
        setTraceEmpty('轨迹加载失败，请稍后重试');
      }
    } finally {
      setTraceLoading(false);
    }
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // 不传 status = 后端默认待办（40+41）。先前传 ['PENDING_ADJUST'] 只覆盖 40，
      // 领取后进入 41 的案件会整行消失，「通过/拒赔/补件」再无处可点。
      const resp = await reviewTaskApi.tasks({ status: taskFilter, page: 1, size: 50 });
      setRows(resp?.data?.records ?? []);
    } catch (err) {
      // 展示后端真实原因：createApiClient 已把 errorCode / message 挂到错误对象上，
      // 先前这里把它一律概括成「待办加载失败」，用户只能截图求助、无法自助判断。
      message.error({ content: resolveErrorMessage(err, '待办加载失败'), key: MSG_KEY_LOAD });
    } finally {
      setLoading(false);
    }
  }, [taskFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const onDecision = async () => {
    if (!decisionTarget) return;
    const values = await decisionForm.validateFields();
    setSubmitting(true);
    try {
      // 走工作台契约决策口：服务端 `ReviewDecisionAssembler#requireRejectType` 会拦「拒赔但无受控原因码」，
      // 返回 400 SC_CLAIM_REJECT_REASON_REQUIRED——下面那个分支才能真正命中。
      await reviewTaskApi.decide(decisionTarget.claimId, {
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
      const errorCode = resolveErrorCode(err);
      // 有专用中文提示的按码分支，其余交给 i18n 的错误码译文（zh-CN 已覆盖 24 条 SC_CLAIM_* ），
      // 都缺失才用兜底文案——不再把「为什么失败」一律糊成一句「提交失败」。
      const text =
        errorCode === 'SC_CLAIM_REJECT_REASON_REQUIRED'
          ? '拒赔必填五要素与受控原因码'
          : errorCode === 'SC_CLAIM_STATUS_CONFLICT'
            ? '案件状态已变化，请刷新后重试'
            : errorCode === 'SC_CLAIM_REVIEWER_CONFLICT'
              ? '审核人不能是本案复核人（职责分离）'
              : resolveErrorMessage(err, '决策提交失败');
      message.error({ content: text, key: MSG_KEY_ACTION });
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

  /**
   * 行内轻动作（领取 / 发起复核）的统一执行：行级 loading + 后端真实原因上屏。
   *
   * <p>原来这两处是裸 `await`，失败时既不提示也不刷新——Promise 在事件处理器里被丢弃，
   * 用户只看到按钮点了没反应。
   */
  const runRow = async (
    row: ClaimHeadProjection,
    action: () => Promise<unknown>,
    okMsg: string,
    fallback: string,
  ) => {
    setActingId(row.claimId);
    try {
      await action();
      message.success({ content: okMsg, key: MSG_KEY_ACTION });
      await load();
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, fallback), key: MSG_KEY_ACTION });
    } finally {
      setActingId(null);
    }
  };

  const pendingCount = rows.filter((r) => r.status === 'PENDING_ADJUST').length;
  const processingCount = rows.filter((r) => r.status === 'ADJUSTING').length;
  const riskCount = rows.filter((r) => r.claimAmount >= 10000).length;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="核赔工作台" />
      <Space size="large" wrap style={{ marginBottom: 16 }}>
        <Card size="small">
          <Statistic title="待领取" value={pendingCount} />
        </Card>
        <Card size="small">
          <Statistic title="处理中" value={processingCount} />
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
            <Select<ReviewTaskStatus | undefined>
              allowClear
              placeholder="全部待办（待领取 + 处理中）"
              style={{ width: 240 }}
              value={taskFilter}
              onChange={(v) => setTaskFilter(v)}
              options={TASK_FILTER_OPTIONS}
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
              /**
               * 页内筛选（antd 列筛选，纯视图）：
               *
               * <p>任务维把 42/44/50/45 合并成 DONE 之后，单个状态（尤其「复核中 44」）不能再由服务端
               * 单独筛。这里按**当前结果集**给出状态筛选，既补回能力，又不新增第二份「状态 ↔ 任务维」映射。
               */
              filters: [...new Set(rows.map((r) => r.status))].map((s) => ({
                text: claimStatusMeta(s).label,
                value: s,
              })),
              onFilter: (value, r) => r.status === String(value),
              render: (v: string) => {
                const meta = claimStatusMeta(v);
                return <Tag color={meta.color}>{meta.label}</Tag>;
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
                      loading={actingId === row.claimId}
                      onClick={() =>
                        void runRow(row, () => claimApi.claimAdjustTask(row.claimId), '已领取', '领取失败')
                      }
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
                      loading={actingId === row.claimId}
                      onClick={() =>
                        void runRow(
                          row,
                          () => claimApi.submitForSecondReview(row.claimId),
                          '已发起双人复核',
                          '发起双人复核失败',
                        )
                      }
                    >
                      发起双人复核
                    </Button>
                  )}
                  {row.status === 'REVIEWING' && (
                    <Button
                      type="link"
                      size="small"
                      icon={<CheckCircleOutlined />}
                      onClick={() => setReviewTarget(row)}
                    >
                      复核结论
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
                    <Select options={REJECT_TYPE_GROUPS} placeholder="选择原因码" showSearch optionFilterProp="label" />
                  </Form.Item>
                </>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`复核结论 · ${reviewTarget?.claimNo ?? ''}`}
        open={reviewTarget !== null}
        onCancel={() => setReviewTarget(null)}
        maskClosable={!reviewing}
        closable={!reviewing}
        footer={[
          <Button key="cancel" disabled={reviewing} onClick={() => setReviewTarget(null)}>
            取消
          </Button>,
          <Button
            key="reject"
            danger
            loading={reviewing}
            onClick={() => void onCompleteSecondReview(false)}
          >
            复核拒绝（转拒赔生效）
          </Button>,
          <Button
            key="pass"
            type="primary"
            loading={reviewing}
            onClick={() => void onCompleteSecondReview(true)}
          >
            复核通过（结案）
          </Button>,
        ]}
      >
        <Alert
          type="warning"
          showIcon
          message="复核人不能是本案第一审核人（职责分离，服务端按 token 主体比对）"
          description="通过即结案（44→50），拒绝即转拒赔生效（44→45）。该动作不可回退，如需纠正只能走申诉复议。"
        />
        <div style={{ marginTop: 16 }}>
          <Space size="large" wrap>
            <Typography.Text type="secondary">报案号：{reviewTarget?.claimNo ?? '-'}</Typography.Text>
            <Typography.Text type="secondary">
              申请金额：¥{Number(reviewTarget?.claimAmount ?? 0).toFixed(2)}
            </Typography.Text>
            <Typography.Text type="secondary">
              核定金额：
              {reviewTarget?.approvedAmount == null
                ? '待核定'
                : `¥${Number(reviewTarget.approvedAmount).toFixed(2)}`}
            </Typography.Text>
          </Space>
        </div>
      </Modal>

      <Drawer
        title={`决策图轨迹回放 · ${traceTarget?.claimNo ?? ''}`}
        width={560}
        open={traceTarget !== null}
        loading={traceLoading}
        onClose={() => setTraceTarget(null)}
      >
        {traceNodes.length > 0 && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={`最近一次执行 · ${traceNodes.length} 个节点（只读回放，不触发决策执行）`}
            description="AI 仅出建议与置信度，最终责任/资金结论以确定性分诊规则为准（fail-safe 降级时提示规则审查模式）。"
          />
        )}
        {traceEmpty ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={traceEmpty} />
        ) : (
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
        )}
      </Drawer>
    </div>
  );
}
