import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Card,
  Input,
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

/**
 * 资金工作台（synapClaim A7，手写不低代码化）。
 *
 * 方案明确规定：P0-3（真实出金 stub）/P0-5（无限流）未闭环前，资金页保持手写、不低代码化——
 * 结算单是状态机（软占→硬预占/复核→支付），占额是跨聚合 REQUIRES_NEW 写，不变量在领域层。
 * 本页读走 settlementApi / quotaApi（领域 controller），写/动走同一 controller（自带幂等键 + 细码权限）。
 */

const REVIEWER_ID = 'reviewer-001';

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
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '1';
  const [input, setInput] = useState(claimId);
  const [accountType, setAccountType] = useState<AccountType>('PERSONAL');
  const [settlement, setSettlement] = useState<SettlementResult | null>(null);
  const [quota, setQuota] = useState<QuotaSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, q] = await Promise.all([settlementApi.latest(claimId), quotaApi.get(claimId)]);
      setSettlement(s?.data ?? null);
      setQuota(q?.data ?? null);
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_QUOTA_NOT_FOUND') {
        setQuota(null);
      } else {
        message.error('资金数据加载失败');
      }
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success(okMsg);
      void load();
    } catch {
      message.error('操作失败');
    } finally {
      setActing(false);
    }
  };

  const onCreate = () =>
    run(
      () =>
        settlementApi.create(
          claimId,
          { attempt: (settlement?.attempt ?? 0) + 1, accountType },
          REVIEWER_ID,
        ),
      '结算单已建（软占额度）',
    );

  const onAuditPass = () =>
    run(
      () =>
        settlementApi.auditPass(
          claimId,
          { reviewerIds: ['reviewer-002', 'reviewer-003'], firstReviewerId: REVIEWER_ID },
          REVIEWER_ID,
        ),
      '审核通过（硬预占）',
    );

  const onPay = () => run(() => settlementApi.pay(claimId, REVIEWER_ID), '支付成功（额度 frozen→claimed）');
  const onCancel = () =>
    run(() => settlementApi.cancel(claimId, { payFailed: false }, REVIEWER_ID), '已撤销/拒赔（释放预占）');
  const onRefund = () => run(() => settlementApi.refund(claimId, REVIEWER_ID), '已退票退款（红冲并回冲额度）');

  const onSearch = () => {
    const id = input.trim();
    if (!id) return;
    setSearchParams({ claimId: id });
  };

  const stage = settlement?.fundingStage;
  const step = stageStep(stage);
  const terminal = isTerminal(stage);

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="资金工作台（A7）"
        extra={
          <Space>
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={onSearch}
              placeholder="案件ID"
              style={{ width: 140 }}
            />
            <Button icon={<ReloadOutlined />} onClick={onSearch}>
              查询
            </Button>
          </Space>
        }
      >
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
                审核通过（硬预占）
              </Button>
            )}
            {isOccupied(stage) && (
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
      </Card>
    </div>
  );
}
