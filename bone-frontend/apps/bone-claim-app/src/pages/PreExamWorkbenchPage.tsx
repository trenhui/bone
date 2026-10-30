import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined, ReloadOutlined, ThunderboltOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { claimApi, type ClaimHeadProjection, type ClaimStatusName } from '../services/claimApi';
import { PRE_EXAM_TODO_STATUSES, claimStatusMeta, statusOptions } from '../services/claimStatus';
import { PageHeader } from '../components/PageHeader';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 初审工作台（打通案件 7→10→11/13→12→40 这一段）。
 *
 * <p><b>为什么必须补这一页</b>：后端 `ClaimCaseController` 提供
 * `POST /{id}/allocate`、`/{id}/pre-exam/start`、`/{id}/pre-exam/complete`、`/{id}/escalate` 四个动作，
 * 而前端 `claimApi` 虽然定义了前三个方法，**没有任何页面调用它们**（全仓 grep 只命中 `claimApi.ts` 自身），
 * 且「完成初审」`completePreExam` 连方法都没有。结果是刚受理的案件（7/10/11）在 UI 上既看不见也推不动，
 * 只能靠手工打接口——作业链路在这里断了一整段。
 *
 * <p><b>职责边界</b>（与核赔工作台分工）：初审只做「材料是否齐备 + 核定金额」，不碰资金；
 * 金额一旦转人工核赔（`escalate` → 40），后续由核赔工作台走决策与双人复核。
 *
 * <p><b>操作人身份</b>：全部动作由服务端从已验签 token 取 `CurrentOperator`，本页不传、也不允许传操作人。
 */

/** 同类失败共用一个 message key：重复触发时 antd 替换而非堆叠（StrictMode 双挂载会并发两次）。 */
const MSG_KEY_LOAD = 'claim-preexam-load';
const MSG_KEY_ACTION = 'claim-preexam-action';

/** 待分配 / 待初审 / 初审中 三档计数口径（列表可能被状态筛选收窄，故单独说明）。 */
const COUNT_GROUPS: { key: string; label: string; statuses: ClaimStatusName[] }[] = [
  { key: 'accepted', label: '待分配', statuses: ['ACCEPTED'] },
  { key: 'pending', label: '待初审', statuses: ['PENDING_PRE_EXAM'] },
  { key: 'examining', label: '初审中（含自动化）', statuses: ['PRE_EXAMINING', 'AUTO_PRE_EXAMINING'] },
];

export function PreExamWorkbenchPage(): ReactElement {
  const [rows, setRows] = useState<ClaimHeadProjection[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<ClaimStatusName | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  /** 行级在途动作：只锁当前行，避免整表按钮全灰（用户会以为页面卡死）。 */
  const [actingId, setActingId] = useState<string | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});

  const [completeTarget, setCompleteTarget] = useState<ClaimHeadProjection | null>(null);
  const [completing, setCompleting] = useState(false);
  const [form] = Form.useForm<{ approvedAmount: number }>();

  const effectiveStatuses = useMemo<ClaimStatusName[]>(
    () => (statusFilter ? [statusFilter] : PRE_EXAM_TODO_STATUSES),
    [statusFilter],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await claimApi.page({ statuses: effectiveStatuses, page, size });
      setRows(resp?.data?.records ?? []);
      setTotal(resp?.data?.total ?? 0);
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '初审待办加载失败'), key: MSG_KEY_LOAD });
    } finally {
      setLoading(false);
    }
  }, [effectiveStatuses, page, size]);

  /**
   * 三档计数：各查一次 `size=1` 只取 total，避免把全量记录拉回前端。
   * 任一档失败不影响主列表（计数是辅助信息，不该让整页报错）。
   */
  const loadCounts = useCallback(async () => {
    try {
      const results = await Promise.all(
        COUNT_GROUPS.map((g) => claimApi.page({ statuses: g.statuses, page: 1, size: 1 })),
      );
      const next: Record<string, number> = {};
      COUNT_GROUPS.forEach((g, i) => {
        next[g.key] = results[i]?.data?.total ?? 0;
      });
      setCounts(next);
    } catch {
      setCounts({});
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadCounts();
  }, [loadCounts]);

  /** 统一动作执行：行级 loading + 成功后刷新列表与计数 + 后端真实原因上屏。 */
  const run = async (
    row: ClaimHeadProjection,
    action: () => Promise<unknown>,
    okMsg: string,
    fallbackErr: string,
  ) => {
    setActingId(row.claimId);
    try {
      await action();
      message.success({ content: okMsg, key: MSG_KEY_ACTION });
      await Promise.all([load(), loadCounts()]);
    } catch (err) {
      const code = resolveErrorCode(err);
      // 状态冲突是初审最高频的失败：多半是别人已经推进过这单，提示要能指向「刷新」而不是「重试」
      const text =
        code === 'SC_CLAIM_STATUS_CONFLICT'
          ? '案件状态已变化（可能已被他人推进），请刷新后重试'
          : resolveErrorMessage(err, fallbackErr);
      message.error({ content: text, key: MSG_KEY_ACTION });
    } finally {
      setActingId(null);
    }
  };

  const onAllocate = (row: ClaimHeadProjection) =>
    run(row, () => claimApi.allocate(row.claimId), '已分配初审', '分配初审失败');

  const onStart = (row: ClaimHeadProjection, automated: boolean) =>
    run(
      row,
      () => claimApi.startPreExam(row.claimId, automated),
      automated ? '已进入自动化初审' : '已开始初审',
      '开始初审失败',
    );

  const onEscalate = (row: ClaimHeadProjection) =>
    run(row, () => claimApi.escalate(row.claimId), '已转人工核赔', '转人工核赔失败');

  const openComplete = (row: ClaimHeadProjection) => {
    setCompleteTarget(row);
    form.setFieldsValue({ approvedAmount: row.approvedAmount ?? row.claimAmount });
  };

  const onComplete = async () => {
    if (!completeTarget) return;
    const values = await form.validateFields();
    setCompleting(true);
    try {
      await claimApi.completePreExam(completeTarget.claimId, values.approvedAmount);
      message.success({ content: '初审已完成（核定金额已回填）', key: MSG_KEY_ACTION });
      setCompleteTarget(null);
      form.resetFields();
      await Promise.all([load(), loadCounts()]);
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '完成初审失败'), key: MSG_KEY_ACTION });
    } finally {
      setCompleting(false);
    }
  };

  const rowBusy = (row: ClaimHeadProjection) => actingId === row.claimId;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="初审工作台"
        description="材料齐备与核定金额的把关环节；初审完成即回填核定金额，需要资金决策的转人工核赔。"
      />

      <Space size="large" wrap style={{ marginBottom: 16 }}>
        {COUNT_GROUPS.map((g) => (
          <Card size="small" key={g.key}>
            <Statistic title={g.label} value={counts[g.key] ?? '-'} />
          </Card>
        ))}
      </Space>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="初审不做资金动作：核定金额只影响后续理算基准，真正的占额与出金在资金工作台。"
        description="「已签收」案件需先分配初审；「待初审分配」可开始初审；初审完成后可转人工核赔（≥1 万需双人复核）。"
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
              onChange={(v) => {
                setStatusFilter(v);
                setPage(1);
              }}
              options={statusOptions(PRE_EXAM_TODO_STATUSES)}
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
          scroll={{ x: 1100 }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
          columns={[
            { title: '报案号', dataIndex: 'claimNo', width: 200, fixed: 'left' },
            { title: '保单号', dataIndex: 'policyNo', width: 150 },
            {
              title: '申请金额',
              dataIndex: 'claimAmount',
              width: 130,
              align: 'right',
              render: (v: number) => `¥${Number(v ?? 0).toFixed(2)}`,
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 150,
              render: (v: ClaimStatusName) => {
                const meta = claimStatusMeta(v);
                return <Tag color={meta.color}>{meta.label}</Tag>;
              },
            },
            {
              title: '大额',
              width: 100,
              render: (_, r) =>
                Number(r.claimAmount) >= 10000 ? <Badge color="red" text="≥1万" /> : <span>-</span>,
            },
            { title: '渠道', dataIndex: 'sourceChannel', width: 110 },
            { title: '受理时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              width: 330,
              fixed: 'right',
              render: (_, row) => (
                <Space size={4} wrap>
                  {row.status === 'ACCEPTED' && (
                    <Popconfirm
                      title="分配初审"
                      description="确认材料齐备并分配初审？"
                      okText="分配"
                      cancelText="取消"
                      onConfirm={() => onAllocate(row)}
                    >
                      <Button type="link" size="small" loading={rowBusy(row)}>
                        分配初审
                      </Button>
                    </Popconfirm>
                  )}
                  {row.status === 'PENDING_PRE_EXAM' && (
                    <>
                      <Button
                        type="link"
                        size="small"
                        loading={rowBusy(row)}
                        onClick={() => onStart(row, false)}
                      >
                        开始初审
                      </Button>
                      <Popconfirm
                        title="自动化初审"
                        description="交给决策图自动初审？结论仍受确定性规则约束。"
                        okText="开始"
                        cancelText="取消"
                        onConfirm={() => onStart(row, true)}
                      >
                        <Button type="link" size="small" icon={<ThunderboltOutlined />} loading={rowBusy(row)}>
                          自动化
                        </Button>
                      </Popconfirm>
                    </>
                  )}
                  {(row.status === 'PRE_EXAMINING' || row.status === 'AUTO_PRE_EXAMINING') && (
                    <>
                      <Button
                        type="link"
                        size="small"
                        icon={<CheckCircleOutlined />}
                        loading={rowBusy(row)}
                        onClick={() => openComplete(row)}
                      >
                        完成初审
                      </Button>
                      <Popconfirm
                        title="转人工核赔"
                        description="初审后仍需资金/责任判断，转核赔工作台处理？"
                        okText="转核赔"
                        cancelText="取消"
                        onConfirm={() => onEscalate(row)}
                      >
                        <Button type="link" size="small" icon={<UserSwitchOutlined />} loading={rowBusy(row)}>
                          转核赔
                        </Button>
                      </Popconfirm>
                    </>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={`完成初审 · ${completeTarget?.claimNo ?? ''}`}
        open={completeTarget !== null}
        onOk={onComplete}
        confirmLoading={completing}
        okText="提交核定金额"
        onCancel={() => {
          setCompleteTarget(null);
          form.resetFields();
        }}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="核定金额将作为后续理算的金额基准"
          description="通过时核定金额不得超过申请金额（服务端不变量）。金额由核赔员判断，AI 不参与。"
        />
        <Form form={form} layout="vertical">
          <Form.Item label="申请金额">
            <Typography.Text strong>
              ¥{Number(completeTarget?.claimAmount ?? 0).toFixed(2)}
            </Typography.Text>
          </Form.Item>
          <Form.Item
            name="approvedAmount"
            label="核定金额（≤ 申请金额）"
            rules={[
              { required: true, message: '请填写核定金额' },
              {
                validator: (_, value: number) => {
                  if (value == null) return Promise.resolve();
                  if (value < 0) return Promise.reject(new Error('核定金额不能为负数'));
                  const claimAmount = Number(completeTarget?.claimAmount ?? 0);
                  if (value > claimAmount) {
                    return Promise.reject(new Error(`不得超过申请金额 ¥${claimAmount.toFixed(2)}`));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} addonAfter="元" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
