import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
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
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, NodeIndexOutlined, CheckCircleOutlined, RobotOutlined } from '@ant-design/icons';
import {
  claimApi,
  reviewTaskApi,
  normalizeTotal,
  stableClaimActionKey,
  type ClaimHeadProjection,
  type Decision,
  type DecisionExecutionSummary,
  type DecisionTraceNode,
  type ReviewTaskStatus,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { ClaimIdLink } from '../components/ClaimIdLink';
import { PolicyNoLink } from '../components/PolicyNoLink';
import { AiDisclosureCard } from '../components/AiDisclosureCard';
import { AiSuggestionCard } from '../components/AiSuggestionCard';
import { explainNode, toneColor, type ExplainFact } from '../services/decisionExplain';
import { claimStatusMeta } from '../services/claimStatus';
import { consumptionReasonMeta } from '../services/decisionConsumption';
import { rejectReasonGroups } from '../services/rejectReason';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { Auth, AuthButton } from '@bone/ui';
import {
  MAX_BATCH_DECISION,
  type BatchDecisionItem,
  type BatchDecisionResp,
} from '../services/reviewTask';
import { PERMISSIONS } from '../constants/permissions';
import { useDualReviewThreshold } from '../hooks/useDualReviewThreshold';
import { MoneyText } from '../components/Money';
import { formatMoney } from '../services/money';
import { reviewFatigueApi, sessionContinuousMinutes } from '../services/reviewFatigue';
import type { ReviewFatigueEvaluation } from '../services/types';

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
 * 自动通过结论的「消费结果」由 `GET /review/tasks/{id}/execution` 提供（后端落
 * `claim_decision_execution.consumption_reason`），文案统一走 `services/decisionConsumption.ts`。
 *
 * <p>为什么不在前端从现有字段推算：原因码取决于**执行当时**的自动放款开关与单笔上限，
 * 事后用当前配置重算得到的是「现在重跑会怎样」，而不是「当时为什么没放款」。可推导的假象
 * 比缺字段更危险，故取持久化事实。
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
  /** 批量处置选择：rowKey 即 claimId；后端整批上限 20，前端同步收口。 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [batching, setBatching] = useState(false);
  const [taskFilter, setTaskFilter] = useState<ReviewTaskStatus | undefined>(undefined);
  /**
   * 服务端分页状态（page / pageSize / total）。
   *
   * <p><b>为什么必须真分页</b>：此前固定取 `size: 50` 再由 antd 按 `pageSize: 20` 前端切片，
   * 第 3 页起就是空白，而 `total` 仍显示全部——用户看到「共 N 条」却翻不到，属数据正确性缺陷而非体验问题
   * （design §13.4 / D5：total 由服务端同一 COUNT(*) 口径产出，前端只传 page/size）。
   */
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  /**
   * 任务维服务端计数（三个独立 `size:1` 查询的 total）。
   *
   * <p>此前三个 KPI 与「列表合计」都从**当前页** `rows` 统计：翻到第 2 页待领取直接变 0，
   * 与同页「共 N 条」（服务端 COUNT(*)）自相矛盾，属于会误导排班的数字。计数必须与列表
   * 同源（后端任务维），拿不到就显示「—」，不用 0 冒充。
   */
  const [counts, setCounts] = useState<Partial<Record<ReviewTaskStatus, number | null>>>({
    WAITING: null,
    PROCESSING: null,
    DONE: null,
  });
  const [countsLoading, setCountsLoading] = useState(false);
  /** AI 结论抽屉：把建议从案件 360 抬到核赔主决策面，但不改变任何结论与金额。 */
  const [aiTarget, setAiTarget] = useState<ClaimHeadProjection | null>(null);

  /**
   * 三个任务维计数并行取，互不连坐。
   *
   * <p>用 `size:1` 只为拿 `total`，不拉数据行（避免为了三个数字把整页数据取三遍）。
   * 任一失败只把该维标为 null（显示「—」），不影响其他维与列表。
   */
  const loadCounts = useCallback(async () => {
    setCountsLoading(true);
    const results = await Promise.allSettled([
      reviewTaskApi.tasks({ status: 'WAITING', size: 1 }),
      reviewTaskApi.tasks({ status: 'PROCESSING', size: 1 }),
      reviewTaskApi.tasks({ status: 'DONE', size: 1 }),
    ]);
    const pick = (r: PromiseSettledResult<{ code?: number; data?: { total?: string | number } }>) =>
      r.status === 'fulfilled' && r.value?.code === 200 ? normalizeTotal(r.value.data?.total) : null;
    setCounts({
      WAITING: pick(results[0]),
      PROCESSING: pick(results[1]),
      DONE: pick(results[2]),
    });
    setCountsLoading(false);
  }, []);
  const [decisionTarget, setDecisionTarget] = useState<ClaimHeadProjection | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [decisionForm] = Form.useForm<{
    decision: Decision;
    approvedAmount: number;
    comment: string;
    rejectType: string;
    factFindings: string;
    clauseBasis: string;
    evidenceRefs: string;
    appealChannel: string;
    legalDeadline: string;
  }>();
  /** 双人复核金额门取运行时配置真源；加载失败时隐藏所有依赖该阈值的标记，绝不回落硬编码。 */
  const { threshold: dualReviewAmount, error: thresholdError } = useDualReviewThreshold();
  const thresholdLabel =
    dualReviewAmount == null ? '阈值未知' : `≥¥${dualReviewAmount.toLocaleString('zh-CN')}`;

  /**
   * 审核疲劳评估（只读 soft prompt）。
   *
   * <p>连续时长取本机会话的近似值；服务端不持有班次事实，班末分钟不传。
   * 评估失败一律回落 null（不提示），绝不阻断待办加载或人工终审。
   */
  const [fatigue, setFatigue] = useState<ReviewFatigueEvaluation | null>(null);

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
      void loadCounts();
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
  /** 本次执行的只读摘要（含「自动通过为什么没放款」的受控原因）；无执行记录时为 null。 */
  const [traceExecution, setTraceExecution] = useState<DecisionExecutionSummary | null>(null);

  /**
   * 决策图轨迹 + 执行摘要：**都只读**。
   *
   * <p>先前这里先调 `POST /claims/{id}/decision/execute` 拿 `traceId`，于是「查看决策图」实际是
   * 「重跑一次决策」：同案已有活跃执行时直接 409 `SC_CLAIM_DECISION_ACTIVE_CONFLICT`，
   * 没活跃执行时则新开一次执行（AUTO_APPROVE 还会结案并建结算单）——查看动作产生写副作用。
   * 现在改走工作台契约 `GET /review/tasks/{id}/trace` + `/execution`，零副作用。
   *
   * <p>两个请求**并行且互不连坐**：摘要拿不到不该把已取到的轨迹一起丢掉（反之亦然）。
   * 二者对「没跑过决策」都回 `SC_CLAIM_DECISION_NOT_FOUND`，故只要有一路是 404 就按空态处理，
   * 不弹故障 toast——「没执行过」是正常业务状态，不是系统错误。
   */
  const onOpenTrace = async (row: ClaimHeadProjection) => {
    setTraceTarget(row);
    setTraceLoading(true);
    setTraceEmpty(null);
    setTraceNodes([]);
    setTraceExecution(null);
    const claimId = String(row.claimId);
    const [nodesRes, execRes] = await Promise.allSettled([
      reviewTaskApi.trace(claimId),
      reviewTaskApi.execution(claimId),
    ]);

    const notFound = (r: PromiseSettledResult<unknown>) =>
      r.status === 'rejected' && resolveErrorCode(r.reason) === 'SC_CLAIM_DECISION_NOT_FOUND';

    const nodes = nodesRes.status === 'fulfilled' ? (nodesRes.value?.data ?? []) : [];
    const execution = execRes.status === 'fulfilled' ? (execRes.value?.data ?? null) : null;
    setTraceNodes(nodes);
    setTraceExecution(execution);

    if (nodes.length === 0 && (notFound(nodesRes) || notFound(execRes))) {
      setTraceEmpty('该案尚无决策执行记录（决策流程尚未跑到决策图节点）');
    } else if (nodesRes.status === 'rejected' && !notFound(nodesRes)) {
      message.error({ content: resolveErrorMessage(nodesRes.reason, '决策轨迹加载失败'), key: MSG_KEY_ACTION });
      // 抽屉不要留空白面板：失败原因同时在抽屉内留一行，用户关掉 toast 后仍能看到状态。
      setTraceEmpty('轨迹加载失败，请稍后重试');
    } else if (nodes.length === 0) {
      setTraceEmpty('该案有执行记录但没有轨迹节点（执行可能未走完）');
    }
    setTraceLoading(false);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // 不传 status = 后端默认待办（40+41）。先前传 ['PENDING_ADJUST'] 只覆盖 40，
      // 领取后进入 41 的案件会整行消失，「通过/拒赔/补件」再无处可点。
      const resp = await reviewTaskApi.tasks({ status: taskFilter, page, size: pageSize });
      setRows(resp?.data?.records ?? []);
      // total 取后端同源 COUNT(*)：前端不再用 rows.length 冒充总数（那会让「共 1 条」永远正确）。
      setTotal(normalizeTotal(resp?.data?.total));
    } catch (err) {
      // 展示后端真实原因：createApiClient 已把 errorCode / message 挂到错误对象上，
      // 先前这里把它一律概括成「待办加载失败」，用户只能截图求助、无法自助判断。
      message.error({ content: resolveErrorMessage(err, '待办加载失败'), key: MSG_KEY_LOAD });
    } finally {
      setLoading(false);
    }
  }, [taskFilter, page, pageSize]);

  /** 切换任务维筛选回到第 1 页：否则从第 3 页切到只有 1 页数据的筛选条件会看到空表。 */
  useEffect(() => {
    setPage(1);
  }, [taskFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadCounts();
  }, [loadCounts]);

  // 疲劳评估与待办加载互不连坐：评估失败只隐藏提示，不清空已取到的待办。
  useEffect(() => {
    let cancelled = false;
    reviewFatigueApi
      .evaluation({ continuousMinutes: sessionContinuousMinutes() })
      .then((resp) => {
        if (!cancelled) setFatigue(resp?.data ?? null);
      })
      .catch(() => {
        if (!cancelled) setFatigue(null);
      });
    return () => {
      cancelled = true;
    };
  }, [load]);

  const onDecision = async () => {
    if (!decisionTarget) return;
    const values = await decisionForm.validateFields();
    setSubmitting(true);
    try {
      // 走工作台契约决策口：服务端 `ReviewDecisionAssembler#requireRejectType` 会拦「拒赔但无受控原因码」，
      // 返回 400 SC_CLAIM_REJECT_REASON_REQUIRED——下面那个分支才能真正命中。
      await reviewTaskApi.decide(
        decisionTarget.claimId,
        {
          decision: values.decision,
          comment: values.comment,
          rejectType: values.rejectType,
          approvedAmount: values.approvedAmount,
          // 后端 ReviewDecisionAssembler 以结构化五要素优先拼 comment；自由文本仅作补充，不替代原因码。
          rejection:
            values.decision === 'REJECTED'
              ? {
                factFindings: values.factFindings,
                clauseBasis: values.clauseBasis,
                evidenceRefs: (values.evidenceRefs ?? '')
                  .split(/[,，;；\s]+/)
                  .map((v) => v.trim())
                  .filter(Boolean),
                appealChannel: values.appealChannel,
                legalDeadline: values.legalDeadline,
              }
              : undefined,
        },
        stableClaimActionKey(decisionTarget.claimId, 'review-decide', decisionTarget.status),
      );
      message.success('决策已生效');
      setDecisionTarget(null);
      decisionForm.resetFields();
      void load();
      void loadCounts();
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
      factFindings: '',
      clauseBasis: '',
      evidenceRefs: '',
      appealChannel: '',
      legalDeadline: '',
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
      void loadCounts();
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, fallback), key: MSG_KEY_ACTION });
    } finally {
      setActingId(null);
    }
  };

  /** 批量请求出口：成功/失败数用 message 汇总，业务失败体按 code 交调用方逐条展示。 */
  const callBatch = async (items: BatchDecisionItem[]) => {
    const resp = await reviewTaskApi.batchDecide(items);
    if (resp?.code !== 200) {
      message.error({ content: resp?.message || '批量决策失败', key: MSG_KEY_ACTION });
      return null;
    }
    const failed = resp.data?.failed ?? [];
    const succeeded = resp.data?.succeeded?.length ?? 0;
    message.success({ content: `成功 ${succeeded} / 失败 ${failed.length}`, key: MSG_KEY_ACTION });
    return { succeeded, failed };
  };

  function showBatchFailures(failed: BatchDecisionResp['failed'], decision: Decision) {
    Modal.warning({
      title: `${failed.length} 单未处置（保留选中，可重试）`,
      content: (
        <List
          size="small"
          dataSource={failed}
          renderItem={(f) => (
            <List.Item
              actions={[
                <Button key="retry" size="small" type="link"
                  onClick={() => void retryFailed(String(f.claimId), decision)}>
                  重试
                </Button>,
              ]}
            >
              <Typography.Text>{String(f.claimId)} · {f.code} · {f.message}</Typography.Text>
            </List.Item>
          )}
        />
      ),
    });
  }

  async function runBatch(decision: Decision) {
    const ids = selectedRowKeys.map(String);
    if (ids.length === 0) return;
    if (ids.length > MAX_BATCH_DECISION) {
      message.warning(`单次批量最多 ${MAX_BATCH_DECISION} 单，请减少选择后重试`);
      return;
    }
    setBatching(true);
    try {
      const outcome = await callBatch(ids.map((claimId) => ({ claimId, decision })));
      if (!outcome) return;
      if (outcome.failed.length > 0) {
        setSelectedRowKeys(outcome.failed.map((f) => String(f.claimId)));
        showBatchFailures(outcome.failed, decision);
      } else {
        setSelectedRowKeys([]);
      }
      void load();
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '批量决策失败'), key: MSG_KEY_ACTION });
    } finally {
      setBatching(false);
    }
  }

  async function retryFailed(claimId: string, decision: Decision) {
    setBatching(true);
    try {
      const outcome = await callBatch([{ claimId, decision }]);
      if (!outcome) return;
      if (outcome.failed.length === 0) {
        setSelectedRowKeys((prev) => prev.filter((key) => key !== claimId));
        void load();
      } else {
        showBatchFailures(outcome.failed, decision);
      }
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '重试失败'), key: MSG_KEY_ACTION });
    } finally {
      setBatching(false);
    }
  }

  const navigate = useNavigate();
  /**
   * 仅「本页大额」保留当前页统计：后端任务维不支持按金额过滤，跨页大额数拿不到。
   * 文案必须写明「本页」，否则又会被当成全局指标。
   */
  const pageRiskCount =
    dualReviewAmount == null
      ? 0
      : rows.filter((r) => Number(r.claimAmount) >= dualReviewAmount).length;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="核赔工作台"
        extra={(
          <Space>
            <Button onClick={() => navigate('/blind-labels')}>盲标</Button>
            <Button onClick={() => navigate('/ai-governance')}>AI 治理</Button>
          </Space>
        )}
      />
      <Space size="large" wrap style={{ marginBottom: 16 }}>
        <Card size="small">
          <Statistic title="待领取" value={counts.WAITING ?? '—'} />
        </Card>
        <Card size="small">
          <Statistic title="处理中" value={counts.PROCESSING ?? '—'} />
        </Card>
        <Card size="small">
          <Statistic title="已处理（含复核中/结案）" value={counts.DONE ?? '—'} />
        </Card>
        <Card size="small">
          <Statistic title={`本页大额（${thresholdLabel}，需双人复核）`} value={pageRiskCount} />
        </Card>
        <Card size="small">
          <Statistic title="列表合计（服务端）" value={total} />
        </Card>
        {countsLoading ? (
          <Typography.Text type="secondary">统计刷新中…</Typography.Text>
        ) : null}
      </Space>

      {thresholdError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="双人复核金额门加载失败：大额标记与计数暂不可用"
          description="后端仍按真实阈值判定；请刷新页面重试，勿按缺失标记判断案件是否需复核。"
        />
      )}
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="AI 仅出建议，资金与责任结论以确定性规则为准；拒赔须填五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限）。"
      />

      {fatigue && (fatigue.overloaded || fatigue.restPrompt || !fatigue.sensitiveEligible) && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="审核疲劳提示（仅提示，不阻断提交）"
          description={
            <Space direction="vertical" size={2}>
              {fatigue.overloaded && (
                <span>
                  您当日完成量已达单班上限（{fatigue.config.maxPerShift} 单），新任务将优先回池转派给其他复核人。
                </span>
              )}
              {fatigue.restPrompt && (
                <span>
                  本机会话连续审核已达提示阈值（{fatigue.config.restPromptMinutes} 分钟），建议短暂休息。
                </span>
              )}
              {!fatigue.sensitiveEligible && (
                <span>临近班末且有大额案件，请确认有充足时间复核；本提示不改变派单与终审。</span>
              )}
              <span>不清空队列、不禁用提交、不替代人工终审。</span>
            </Space>
          }
        />
      )}

      {fatigue && Object.keys(fatigue.config.rejected ?? {}).length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="审核疲劳配置部分未生效，已按基线处理"
          description={Object.entries(fatigue.config.rejected)
            .map(([key, reason]) => `${key}：${reason}`)
            .join('；')}
        />
      )}

      {selectedRowKeys.length > 0 && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Space wrap>
            <Typography.Text strong>已选 {selectedRowKeys.length} 单</Typography.Text>
            <Typography.Text type="secondary">单次上限 {MAX_BATCH_DECISION} 单</Typography.Text>
            <Auth code={PERMISSIONS.REVIEW_WRITE}>
              <Space wrap>
                <Popconfirm
                  title={`批量通过 ${selectedRowKeys.length} 单？`}
                  description="逐案写入通过决策；不满足前置状态的案件会进入失败列表供重试。"
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => void runBatch('APPROVED')}
                >
                  <Button type="primary" loading={batching}
                    disabled={selectedRowKeys.length > MAX_BATCH_DECISION}>批量通过</Button>
                </Popconfirm>
                <Popconfirm
                  title={`批量拒赔 ${selectedRowKeys.length} 单？`}
                  description="逐案写入拒赔决策；拒赔五要素须在单案中已完成填写。"
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => void runBatch('REJECTED')}
                >
                  <Button danger loading={batching}
                    disabled={selectedRowKeys.length > MAX_BATCH_DECISION}>批量拒赔</Button>
                </Popconfirm>
              </Space>
            </Auth>
          </Space>
        </Card>
      )}

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
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys.map(String)),
          }}
          pagination={{
            // 服务端分页：current/pageSize 由本页状态驱动，total 用后端返回值。
            // 禁止再写 `pagination={{ pageSize: 20 }}`（那是对已取回的 50 条做前端切片）。
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (nextPage, nextSize) => {
              setPage(nextPage);
              if (nextSize !== pageSize) {
                setPageSize(nextSize);
                setPage(1);
              }
            },
          }}
          columns={[
            {
              // 1:N：报案号（RP）是一次事故告知，一个报案下可有多笔赔案。
              title: '报案号',
              dataIndex: 'reportNo',
              width: 250,
              ellipsis: { showTitle: false },
              render: (v: string | null) =>
                v ? <Tooltip title={v}>{v}</Tooltip> : <span style={{ color: '#999' }}>—</span>,
            },
            {
              // 赔案号（CL）是每笔赔付的处理单元，点它下钻案件详情（下钻用 claimId）。
              title: '赔案号',
              dataIndex: 'claimNo',
              width: 250,
              ellipsis: { showTitle: false },
              render: (v: string, row: ClaimHeadProjection) => (
                <ClaimIdLink claimId={row.claimId} text={v} testId={`review-case-link-${row.claimId}`} />
              ),
            },
            {
              title: '保单号',
              dataIndex: 'policyNo',
              width: 180,
              ellipsis: { showTitle: false },
              // 点平台保单号下钻保单详情（新标签页；保司保单号不唯一，不接此链接）。
              render: (v: string, row: ClaimHeadProjection) => (
                <PolicyNoLink policyNo={v ?? row.policyNo} testId={`policy-no-link-${row.policyNo}`} />
              ),
            },
            {
              title: '申请金额',
              dataIndex: 'claimAmount',
              width: 120,
              render: (v: number) => formatMoney(v),
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
              render: (_, r) =>
                dualReviewAmount == null
                  ? '阈值未知'
                  : Number(r.claimAmount) >= dualReviewAmount
                    ? <Badge color="red" text={thresholdLabel} />
                    : '-',
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
                  <Button
                    type="link"
                    size="small"
                    icon={<RobotOutlined />}
                    data-testid="review-ai-suggestion"
                    aria-label={`查看案件 ${row.claimNo} 的 AI 建议`}
                    onClick={() => setAiTarget(row)}
                  >
                    AI 建议
                  </Button>
                  {row.status === 'PENDING_ADJUST' && (
                    <AuthButton
                      code={PERMISSIONS.REVIEW_WRITE}
                      type="link"
                      size="small"
                      loading={actingId === row.claimId}
                      onClick={() =>
                        void runRow(row, () => claimApi.claimAdjustTask(row.claimId), '已领取', '领取失败')
                      }
                    >
                      领取
                    </AuthButton>
                  )}
                  {row.status === 'ADJUSTING' && (
                    <>
                      <AuthButton
                        code={PERMISSIONS.REVIEW_WRITE}
                        type="link"
                        size="small"
                        onClick={() => openDecision(row, 'APPROVED')}
                      >
                        通过
                      </AuthButton>
                      <AuthButton
                        code={PERMISSIONS.REVIEW_WRITE}
                        type="link"
                        size="small"
                        danger
                        onClick={() => openDecision(row, 'REJECTED')}
                      >
                        拒赔
                      </AuthButton>
                      <AuthButton
                        code={PERMISSIONS.REVIEW_WRITE}
                        type="link"
                        size="small"
                        onClick={() => openDecision(row, 'REQUEST_MORE_EVIDENCE')}
                      >
                        补件
                      </AuthButton>
                    </>
                  )}
                  {row.status === 'ADJUSTED' && dualReviewAmount != null && Number(row.claimAmount) >= dualReviewAmount && (
                    <AuthButton
                      code={PERMISSIONS.REVIEW_WRITE}
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
                    </AuthButton>
                  )}
                  {row.status === 'REVIEWING' && (
                    <AuthButton
                      code={PERMISSIONS.REVIEW_WRITE}
                      type="link"
                      size="small"
                      icon={<CheckCircleOutlined />}
                      onClick={() => setReviewTarget(row)}
                    >
                      复核结论
                    </AuthButton>
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
        onCancel={() => {
          setDecisionTarget(null);
          decisionForm.resetFields();
        }}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setDecisionTarget(null);
              decisionForm.resetFields();
            }}
          >
            取消
          </Button>,
          <AuthButton
            key="ok"
            data-testid="review-decision-confirm"
            code={PERMISSIONS.REVIEW_WRITE}
            type="primary"
            loading={submitting}
            onClick={onDecision}
          >
            确认决策
          </AuthButton>,
        ]}
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
                    name="factFindings"
                    label="① 事实认定"
                    rules={[{ required: true, whitespace: true, message: '拒赔必填：事实认定' }]}
                  >
                    <Input.TextArea rows={2} placeholder="可核对的案件事实（不要写情绪化措辞）" />
                  </Form.Item>
                  <Form.Item
                    name="clauseBasis"
                    label="② 条款依据"
                    rules={[{ required: true, whitespace: true, message: '拒赔必填：条款依据' }]}
                  >
                    <Input placeholder="具体条款号与约定摘要" />
                  </Form.Item>
                  <Form.Item
                    name="evidenceRefs"
                    label="③ 证据位置（多个用逗号分隔）"
                    rules={[
                      { required: true, whitespace: true, message: '拒赔必填：证据位置' },
                      {
                        validator: (_, value: string | undefined) => {
                          const refs = (value ?? '')
                            .split(/[,，;；\s]+/)
                            .map((v) => v.trim())
                            .filter(Boolean);
                          const ok =
                            refs.length > 0 &&
                            refs.length <= 20 &&
                            refs.every((r) => /^EV-\d{3,6}(#p[1-9]\d{0,3})?$/.test(r));
                          return ok
                            ? Promise.resolve()
                            : Promise.reject(
                                new Error('须为可定位原件的位置，如 EV-001 或 EV-001#p2（#p 为影像页码）'),
                              );
                        },
                      },
                    ]}
                  >
                    <Input placeholder="如 EV-001, EV-004#p2；#p 为影像页码，须可在证据集定位" />
                  </Form.Item>
                  <Form.Item
                    name="appealChannel"
                    label="④ 申诉渠道"
                    rules={[{ required: true, whitespace: true, message: '拒赔必填：申诉渠道' }]}
                  >
                    <Input placeholder="客户可使用的申诉/复议入口" />
                  </Form.Item>
                  <Form.Item
                    name="legalDeadline"
                    label="⑤ 法定时限"
                    rules={[{ required: true, whitespace: true, message: '拒赔必填：法定时限' }]}
                  >
                    <Input placeholder="如 收到通知书起 30 日内" />
                  </Form.Item>
                  <Form.Item name="comment" label="补充说明（可选）">
                    <Input.TextArea rows={2} placeholder="补充五要素之外的说明；不替代受控原因码" />
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
          <AuthButton
            key="reject"
            code={PERMISSIONS.REVIEW_WRITE}
            danger
            loading={reviewing}
            onClick={() => void onCompleteSecondReview(false)}
          >
            复核拒绝（转拒赔生效）
          </AuthButton>,
          <AuthButton
            key="pass"
            code={PERMISSIONS.REVIEW_WRITE}
            type="primary"
            loading={reviewing}
            onClick={() => void onCompleteSecondReview(true)}
          >
            复核通过（结案）
          </AuthButton>,
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
              申请金额：<MoneyText value={reviewTarget?.claimAmount} />
            </Typography.Text>
            <Typography.Text type="secondary">
              核定金额：<MoneyText value={reviewTarget?.approvedAmount} unknownText="待核定" />
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
        {/* §21 生成内容显著标识：与轨迹并列只读展示，绝不触发决策执行 */}
        <AiDisclosureCard claimId={traceTarget?.claimId} />
        {(() => {
          // 自动通过结论的消费结果是这一屏最该被看见的事实：结论说「可自动放款」而资金没动，
          // 才是真正会变成客诉的状态。没有消费动作（结论非自动通过）时不渲染，避免噪声。
          const meta = consumptionReasonMeta(traceExecution?.consumptionReason);
          if (!meta) {
            return null;
          }
          return (
            <Alert
              type={meta.tone === 'success' ? 'success' : meta.tone === 'warning' ? 'warning' : 'info'}
              showIcon
              style={{ marginBottom: 16 }}
              message={
                <Space wrap>
                  <span>自动放款结论</span>
                  <Tag
                    color={meta.tone === 'success' ? 'green' : meta.tone === 'warning' ? 'orange' : 'default'}
                    style={{ marginInlineEnd: 0 }}
                  >
                    {meta.label}
                  </Tag>
                </Space>
              }
              description={meta.hint}
            />
          );
        })()}
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

      {/*
        AI 结论进入主决策面：此前只有案件 360 能看到 AI 参与披露，核赔员在待办队列里必须多跳一次。
        这里嵌入同一组件（同一契约、同一 fail-closed 口径），不与决策按钮联动，
        避免"看一眼 AI 建议"被误解为已经采纳。
      */}
      <Drawer
        title={`AI 结论 · ${aiTarget?.claimNo ?? ''}`}
        width={560}
        open={aiTarget !== null}
        destroyOnHidden
        onClose={() => setAiTarget(null)}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="AI 仅出建议，不改变案件结论与金额"
          description="采纳/驳回只回流 AI 质量指标（命中率、改单率），不触发通过、拒赔或资金动作；最终结论仍须人工在决策弹窗内提交。"
        />
        {aiTarget ? <AiSuggestionCard claimId={String(aiTarget.claimId)} scene="review" /> : null}
      </Drawer>
    </div>
  );
}
