import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Drawer,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined, ThunderboltOutlined, UserSwitchOutlined } from '@ant-design/icons';
import {
  claimApi,
  normalizeTotal,
  type ClaimHeadProjection,
  type ClaimStatusName,
} from '../services/claimApi';
import { PRE_EXAM_TODO_STATUSES, claimStatusMeta, statusOptions } from '../services/claimStatus';
import { PageHeader } from '../components/PageHeader';
import { ClaimQueryBar } from '../components/ClaimQueryBar';
import {
  ClaimQueryFields,
  formValuesToQueryString,
  queryParamsFromState,
  statusesFromFilter,
  type ClaimQueryFormValues,
} from '../components/ClaimQueryFields';
import { useClaimQuery } from '../hooks/useClaimQuery';
import { useSavedViews } from '../hooks/useSavedViews';
import { ClaimIdLink } from '../components/ClaimIdLink';
import { PolicyNoLink } from '../components/PolicyNoLink';
import { EvidenceGallery } from '../components/EvidenceGallery';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { Auth, AuthButton } from '@bone/ui';
import { PERMISSIONS } from '../constants/permissions';
import { useDualReviewThreshold } from '../hooks/useDualReviewThreshold';
import { MoneyText } from '../components/Money';
import { formatMoney } from '../services/money';

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
  /** 大额门来自运行时配置；加载失败时隐藏标记，不回落硬编码阈值。 */
  const { threshold: dualReviewAmount, error: thresholdError } = useDualReviewThreshold();
  const [rows, setRows] = useState<ClaimHeadProjection[]>([]);
  const [total, setTotal] = useState(0);
  /**
   * 查询状态进 URL（统一查询基座）：关键字 / 状态 / 案件ID / 时间 / 金额等可分享、可前进后退恢复。
   *
   * <p>默认视图仍走原待办口径（初审四态）；一旦输入任一查询条件，切到服务端
   * `claimApi.query` 做真过滤，并按条件决定是否放宽默认状态——否则用案件ID查
   * 非初审态案件会得到空结果，用户以为「查询坏了」。
   */
  const {
    query,
    setKeyword,
    setFilter,
    setPage,
    setSize,
    reset,
    applyQueryString,
    currentQueryString,
  } = useClaimQuery();
  const savedViews = useSavedViews();
  const statusFilter = statusesFromFilter(query.filters.status);
  const { page, size } = query;
  const [loading, setLoading] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  /** 行级在途动作：只锁当前行，避免整表按钮全灰（用户会以为页面卡死）。 */
  const [actingId, setActingId] = useState<string | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});

  /** 影像件抽屉目标案件：只读查看，不影响任何待办动作。 */
  const [evidenceClaimId, setEvidenceClaimId] = useState<string | null>(null);
  const [completeTarget, setCompleteTarget] = useState<ClaimHeadProjection | null>(null);
  const [completing, setCompleting] = useState(false);
  const [form] = Form.useForm<{ approvedAmount: number }>();

  const effectiveStatuses = useMemo<ClaimStatusName[]>(
    () => (statusFilter?.length ? statusFilter : PRE_EXAM_TODO_STATUSES),
    [statusFilter],
  );
  const hasQuery =
    Boolean(query.keyword) ||
    Object.keys(query.filters).some((key) => key !== 'status' || Boolean(statusFilter?.length));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = hasQuery
        ? await claimApi.query({
            ...queryParamsFromState(query),
            statuses: statusFilter?.length ? statusFilter : undefined,
          })
        : await claimApi.page({ statuses: effectiveStatuses, page, size });
      setRows(resp?.data?.records ?? []);
      setTotal(normalizeTotal(resp?.data?.total));
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '初审待办加载失败'), key: MSG_KEY_LOAD });
    } finally {
      setLoading(false);
    }
  }, [hasQuery, query, statusFilter, effectiveStatuses, page, size]);

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
        next[g.key] = normalizeTotal(results[i]?.data?.total);
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

      {thresholdError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="双人复核金额门加载失败：大额标记暂不可用"
          description="请刷新页面重试；后端仍按真实阈值判定。"
        />
      )}
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
          <ClaimQueryBar
            keyword={query.keyword}
            onKeyword={setKeyword}
            placeholder="案件ID / 赔案号 / 保单号 / 发票号"
            onRefresh={() => void load()}
            views={savedViews.views}
            onApplyView={(view) => applyQueryString(view.queryString)}
            onSaveView={(name) => savedViews.save(name, currentQueryString())}
            onRemoveView={savedViews.remove}
            onTogglePin={savedViews.togglePin}
            actions={
              <Space size={8} wrap>
                <Select<ClaimStatusName[]>
                  mode="multiple"
                  allowClear
                  maxTagCount="responsive"
                  placeholder="状态筛选"
                  style={{ width: 220 }}
                  value={statusFilter ?? []}
                  onChange={(v) => setFilter('status', v.join(','))}
                  options={statusOptions(PRE_EXAM_TODO_STATUSES)}
                />
                <Button onClick={() => setAdvancedOpen(true)}>高级查询</Button>
                {hasQuery && <Button onClick={reset}>清空条件</Button>}
              </Space>
            }
          />
        }
      >
        <Table<ClaimHeadProjection>
          rowKey="claimId"
          loading={loading}
          dataSource={rows}
          // 列宽合计 1690：报案号/赔案号/保单号是长标识符，scroll.x 必须 >= 列宽之和，
          // 否则 antd 切 fixed table layout 会压缩列宽，长号撑破 nowrap 链接、
          // 溢出覆盖相邻列（截图「报案号+保单号粘连」）。见 ClaimIdLink 的 maxWidth 兜底。
          scroll={{ x: 1760 }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              // setSize 内部已回第 1 页；只发一次 URL 更新，避免连发两次产生中间态。
              if (s !== size) setSize(s);
              else setPage(p);
            },
          }}
          columns={[
            {
              // 1:N：报案号（RP）是一次事故告知，一个报案下可有多笔赔案。
              title: '报案号',
              dataIndex: 'reportNo',
              width: 250,
              fixed: 'left',
              ellipsis: { showTitle: false },
              render: (v: string | null) =>
                v ? (
                  <Tooltip title={v}>{v}</Tooltip>
                ) : (
                  <span style={{ color: '#999' }}>—</span>
                ),
            },
            {
              // 赔案号（CL）是每笔赔付的处理单元，点它下钻案件详情（下钻用 claimId）。
              title: '赔案号',
              dataIndex: 'claimNo',
              width: 250,
              ellipsis: { showTitle: false },
              render: (v: string, row: ClaimHeadProjection) => (
                <ClaimIdLink claimId={row.claimId} text={v} testId={`pre-exam-case-link-${row.claimId}`} />
              ),
            },
            {
              title: '保单号',
              dataIndex: 'policyNo',
              width: 180,
              ellipsis: { showTitle: false },
              render: (v: string, row: ClaimHeadProjection) => (
                <PolicyNoLink policyNo={v ?? row.policyNo} testId={`policy-no-link-${row.policyNo}`} />
              ),
            },
            {
              title: '申请金额',
              dataIndex: 'claimAmount',
              width: 130,
              align: 'right',
              render: (v: number) => <MoneyText value={v} />,
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
                dualReviewAmount == null ? (
                  <span>阈值未知</span>
                ) : Number(r.claimAmount) >= dualReviewAmount ? (
                  <Badge color="red" text={`≥¥${dualReviewAmount.toLocaleString('zh-CN')}`} />
                ) : (
                  <span>-</span>
                ),
            },
            { title: '渠道', dataIndex: 'sourceChannel', width: 110 },
            { title: '受理时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              width: 400,
              fixed: 'right',
              render: (_, row) => (
                <Space size={4} wrap>
                  {row.status === 'ACCEPTED' && (
                    <Auth code={PERMISSIONS.REVIEW_WRITE}>
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
                    </Auth>
                  )}
                  {row.status === 'PENDING_PRE_EXAM' && (
                    <>
                      <AuthButton
                        code={PERMISSIONS.REVIEW_WRITE}
                        type="link"
                        size="small"
                        loading={rowBusy(row)}
                        onClick={() => onStart(row, false)}
                      >
                        开始初审
                      </AuthButton>
                      <Auth code={PERMISSIONS.REVIEW_WRITE}>
                        <Popconfirm
                          title="自动化初审"
                          description="交给决策图自动初审？结论仍受确定性规则约束。"
                          okText="开始"
                          cancelText="取消"
                          onConfirm={() => onStart(row, true)}
                        >
                          <Button
                            type="link"
                            size="small"
                            icon={<ThunderboltOutlined />}
                            loading={rowBusy(row)}
                          >
                            自动化
                          </Button>
                        </Popconfirm>
                      </Auth>
                    </>
                  )}
                  {(row.status === 'PRE_EXAMINING' || row.status === 'AUTO_PRE_EXAMINING') && (
                    <>
                      <AuthButton
                        code={PERMISSIONS.REVIEW_WRITE}
                        type="link"
                        size="small"
                        icon={<CheckCircleOutlined />}
                        loading={rowBusy(row)}
                        onClick={() => openComplete(row)}
                      >
                        完成初审
                      </AuthButton>
                      <Auth code={PERMISSIONS.REVIEW_WRITE}>
                        <Popconfirm
                          title="转人工核赔"
                          description="初审后仍需资金/责任判断，转核赔工作台处理？"
                          okText="转核赔"
                          cancelText="取消"
                          onConfirm={() => onEscalate(row)}
                        >
                          <Button
                            type="link"
                            size="small"
                            icon={<UserSwitchOutlined />}
                            loading={rowBusy(row)}
                          >
                            转核赔
                          </Button>
                        </Popconfirm>
                      </Auth>
                    </>
                  )}
                  <Button
                    type="link"
                    size="small"
                    data-testid={`pre-exam-evidence-${row.claimId}`}
                    onClick={() => setEvidenceClaimId(row.claimId)}
                  >
                    影像件
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Drawer
        title="初审工作台 · 高级查询"
        width={720}
        open={advancedOpen}
        onClose={() => setAdvancedOpen(false)}
        destroyOnHidden
      >
        <ClaimQueryFields
          query={query}
          statusChoices={PRE_EXAM_TODO_STATUSES}
          onSubmit={(values: ClaimQueryFormValues) => {
            applyQueryString(formValuesToQueryString(values));
            setAdvancedOpen(false);
          }}
          onReset={() => {
            reset();
            setAdvancedOpen(false);
          }}
        />
      </Drawer>

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
              <MoneyText value={completeTarget?.claimAmount} />
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
                    return Promise.reject(new Error(`不得超过申请金额 ${formatMoney(claimAmount)}`));
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

      <EvidenceGallery
        claimId={evidenceClaimId}
        open={Boolean(evidenceClaimId)}
        onClose={() => setEvidenceClaimId(null)}
      />
    </div>
  );
}
