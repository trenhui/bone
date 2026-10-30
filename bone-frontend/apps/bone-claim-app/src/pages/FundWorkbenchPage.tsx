import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Button,
  Card,
  Select,
  Space,
  Statistic,
  Steps,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  CheckCircleOutlined,
  BankOutlined,
  StopOutlined,
  RollbackOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  quotaApi,
  settlementApi,
  type AccountType,
  type QuotaSummary,
  type SettlementResult,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 资金工作台（synapClaim A7，手写不低代码化）。
 *
 * 方案明确规定：P0-3（真实出金 stub）/P0-5（无限流）未闭环前，资金页保持手写、不低代码化——
 * 结算单是状态机（软占→硬预占/复核→支付），占额是跨聚合 REQUIRES_NEW 写，不变量在领域层。
 * 本页读走 settlementApi / quotaApi（领域 controller），写/动走同一 controller（自带幂等键 + 细码权限）。
 *
 * 操作人身份不在前端：审核人/复核人都是各自登录账号的 token，页面只表达「谁点了哪个动作」。
 */

/** 结算单状态机关键节点（用于 Steps 展示）。 */
const STAGE_STEPS = ['建单(软占)', '硬预占/复核', '支付'];

const STAGE_META: Record<string, { color: string; label: string }> = {
  SOFT_RESERVED: { color: 'blue', label: '软预占' },
  HARD_RESERVED: { color: 'geekblue', label: '硬预占' },
  REVIEW_FREEZE: { color: 'gold', label: '复核冻结' },
  PAID: { color: 'green', label: '已支付' },
  RELEASED: { color: 'default', label: '已释放' },
  FAILED: { color: 'red', label: '支付失败' },
  REFUNDED: { color: 'orange', label: '已退票退款' },
};

const isTerminal = (stage?: string) => ['RELEASED', 'FAILED', 'REFUNDED'].includes(stage ?? '');
const isOccupied = (stage?: string) => ['HARD_RESERVED', 'REVIEW_FREEZE'].includes(stage ?? '');

const fmt = (v?: number): string => `¥${(Number(v ?? 0) || 0).toFixed(2)}`;

function stageStep(stage?: string): { current: number; status: 'process' | 'finish' | 'wait' } {
  switch (stage) {
  case 'SOFT_RESERVED':
    return { current: 0, status: 'process' };
  case 'REVIEW_FREEZE':
  case 'HARD_RESERVED':
    return { current: 1, status: 'process' };
  case 'PAID':
  case 'RELEASED':
  case 'FAILED':
  case 'REFUNDED':
    return { current: 2, status: 'finish' };
  default:
    return { current: 0, status: 'wait' };
  }
}

export function FundWorkbenchPage(): ReactElement {
  const { claimId, input, setInput, submit } = useClaimIdParam();
  const [accountType, setAccountType] = useState<AccountType>('PERSONAL');
  const [settlement, setSettlement] = useState<SettlementResult | null>(null);
  const [quota, setQuota] = useState<QuotaSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    // 缺省**不要**用占位 '1'：URL 未带 claimId 时会自动查一个未必存在的案件，
    // 后端返回 404 SC_CLAIM_NOT_FOUND 被兜底成「资金数据加载失败」——把「没数据」报成「加载出错」。
    if (!claimId) {
      setSettlement(null);
      setQuota(null);
      return;
    }
    setLoading(true);
    try {
      const [s, q] = await Promise.all([settlementApi.latest(claimId), quotaApi.get(claimId)]);
      setSettlement(s?.data ?? null);
      setQuota(q?.data ?? null);
    } catch (err) {
      const code = resolveErrorCode(err);
      if (code === 'SC_CLAIM_QUOTA_NOT_FOUND') {
        setQuota(null);
      } else if (code === 'SC_CLAIM_NOT_FOUND') {
        // 查无此案 = 空态，不是故障。报成「加载失败」会让用户在没有任何错误时以为系统坏了。
        setSettlement(null);
        setQuota(null);
        message.info('未查询到该案件（或不属于当前租户）');
      } else {
        // 其余按 i18n 码表出文案（原先一律吞成「资金数据加载失败」，把 409/422 的语义全抹平）
        message.error(resolveErrorMessage(err, '资金数据加载失败'));
      }
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    // 未指定案件时不自动查（否则会用占位 ID 打后端并弹「加载失败」）。
    if (claimId) {
      void load();
    }
  }, [claimId, load]);

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success(okMsg);
      void load();
    } catch (err) {
      message.error(resolveErrorMessage(err, '操作失败'));
    } finally {
      setActing(false);
    }
  };

  const onCreate = () =>
    run(
      () => settlementApi.create(claimId, { attempt: (settlement?.attempt ?? 0) + 1, accountType }),
      '结算单已建（软占额度）',
    );

  const onAuditPass = () =>
    run(() => settlementApi.auditPass(claimId), '审核通过（小额硬预占；大额进入复核冻结）');

  /** 大额第二道认证：必须由另一账号登录后点击（同一 token 会被服务端拒绝）。 */
  const onConfirmReview = () =>
    run(() => settlementApi.confirmReview(claimId), '复核放行（硬预占）');

  const onPay = () => run(() => settlementApi.pay(claimId), '支付成功（额度 frozen→claimed）');
  const onCancel = () =>
    run(() => settlementApi.cancel(claimId, { payFailed: false }), '已撤销/拒赔（释放预占）');
  const onRefund = () => run(() => settlementApi.refund(claimId), '已退票退款（红冲并回冲额度）');

  const stage = settlement?.fundingStage;
  const step = stageStep(stage);
  const terminal = isTerminal(stage);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="资金工作台" />
      <Card
        title="结算单与额度"
        extra={
          <CaseIdBar
            value={input}
            onChange={setInput}
            onSubmit={submit}
            loading={loading}
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={() => void load()}
                disabled={!claimId}
                loading={loading}
              >
                刷新
              </Button>
            }
          />
        }
      >
        {!claimId ? (
          <CaseNotSelected hint="资金视图按案件归集，输入案件ID后即可查看结算单与额度" />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Steps current={step.current} status={step.status} items={STAGE_STEPS.map((t) => ({ title: t }))} />

            <Space wrap>
              {settlement == null && (
                <>
                  <Select<AccountType>
                    value={accountType}
                    onChange={setAccountType}
                    style={{ width: 160 }}
                    options={[
                      { value: 'PERSONAL', label: '个人账户' },
                      { value: 'PUBLIC', label: '对公账户' },
                      { value: 'NO_ACCOUNT', label: '无账户' },
                    ]}
                  />
                  <Button type="primary" icon={<PlusOutlined />} loading={acting} onClick={onCreate}>
                  建结算单
                  </Button>
                </>
              )}
              {stage === 'SOFT_RESERVED' && (
                <Button type="primary" icon={<CheckCircleOutlined />} loading={acting} onClick={onAuditPass}>
                审核通过
                </Button>
              )}
              {stage === 'REVIEW_FREEZE' && (
                <Button type="primary" icon={<CheckCircleOutlined />} loading={acting} onClick={onConfirmReview}>
                复核放行（第二人）
                </Button>
              )}
              {stage === 'HARD_RESERVED' && (
                <Button type="primary" icon={<BankOutlined />} loading={acting} onClick={onPay}>
                支付
                </Button>
              )}
              {(stage === 'SOFT_RESERVED' || isOccupied(stage)) && (
                <Button icon={<StopOutlined />} loading={acting} onClick={onCancel}>
                撤销/拒赔
                </Button>
              )}
              {stage === 'PAID' && (
                <Button icon={<RollbackOutlined />} loading={acting} onClick={onRefund}>
                退票退款
                </Button>
              )}
              {terminal && stage && (
                <Tag color={STAGE_META[stage]?.color ?? 'default'}>{STAGE_META[stage]?.label ?? stage}</Tag>
              )}
            </Space>

            {settlement && (
              <Space size="large" wrap>
                <Statistic title="结算单号" value={settlement.settlementNo} valueStyle={{ fontSize: 14 }} />
                <Statistic title="结算金额" value={fmt(settlement.amount)} />
                <Statistic title="占额周期" value={settlement.attempt} />
                <Statistic
                  title="资金阶段"
                  value={STAGE_META[stage ?? '']?.label ?? stage}
                  valueStyle={{ fontSize: 14 }}
                />
              </Space>
            )}

            <Card type="inner" title="额度账户（A5 三值模型）" loading={loading}>
              {quota ? (
                <Space size="large" wrap>
                  <Statistic title="初始额度" value={fmt(quota.initial)} />
                  <Statistic title="已扣减(claimed)" value={fmt(quota.claimed)} />
                  <Statistic title="已冻结(frozen)" value={fmt(quota.frozen)} />
                  <Statistic title="可用额度" value={fmt(quota.available)} valueStyle={{ color: '#3f8600' }} />
                </Space>
              ) : (
                <Typography.Text type="secondary">该案件无个人额度账户</Typography.Text>
              )}
            </Card>

            {quota && quota.transactions.length > 0 && (
              <Card type="inner" title="额度流水（append-only）">
                <Timeline
                  items={[...quota.transactions].reverse().map((t) => ({
                    color: t.direction === 'OCCUPY' ? 'green' : 'red',
                    children: (
                      <span>
                        <Tag color={t.txnType === 'PAID' ? 'green' : t.txnType === 'RELEASE' ? 'red' : 'blue'}>
                          {t.txnType}
                        </Tag>
                        {fmt(t.amount)} ｜ 后值 {fmt(t.afterAmount)} ｜{' '}
                        <Typography.Text type="secondary">{t.idempotencyKey}</Typography.Text>
                      </span>
                    ),
                  }))}
                />
              </Card>
            )}
          </Space>
        )}
      </Card>
    </div>
  );
}
