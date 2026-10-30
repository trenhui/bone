import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { Alert, Button, Card, Space, Statistic, Tag, Tooltip, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  adjustApi,
  type AdjustmentLine,
  type AdjustmentResult,
} from '../services/claimApi';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { adjustmentLineTypeLabel, rejectReasonLabel, zeroReasonLabel } from '../services/rejectReason';
import { SchemaRenderer, resolveVisibleActions } from '@bone/ui-schema';
import type { BoneUiSchema } from '@bone/ui-schema';
import adjustSchema from '../schemas/adjust-attribution.ui-schema.json';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 理算归因页（synapClaim A6，只读展示）—— **由 UI Schema JSON 真正驱动**。
 *
 * <p>展示体（分区/列/标签/六归因 CHILD_TABLE/金额/MONEY）全部来自
 * `src/schemas/adjust-attribution.ui-schema.json`，经本地 SchemaRenderer 渲染；
 * 后端 DTO 仅通过 `toSchemaData` 适配成 schema 的 fieldCode 键值。
 *
 * <p>操作性动作（执行理算/重算/确认）也由 schema 的 `actionBar` 声明、经共享引擎
 * `resolveVisibleActions` 裁决可见性，再由页面层走 action 通道（受控幂等）执行——
 * 展示与动作均 schema 驱动，单一真源在 `adjust-attribution.ui-schema.json`。
 *
 * <p><b>本次增强（可解释性）</b>：原页面把 `zero_reason` / `reject_type` / `bucket` 这些**受控码原样上屏**
 * （业务看到的是 `ZERO_DEDUCT`、`06`、`REASONABLE`），并且丢掉了后端返回的 `deductibleApplied` 与
 * `accumulationAvailable`。核赔员因此无法回答「这单为什么只赔这么多」和「资金为什么点不动」。
 * 现在：码表转中文、行快照解析出费用池/医保内外/赔付比例、汇总合计上屏、累计账缺失显式告警。
 */

/** actionBar 动作码 → 展示标签（展示层映射，判定逻辑由 schema + 引擎单一真源负责）。 */
const ACTION_LABELS: Record<string, string> = {
  execute: '执行理算',
  recalculate: '重算',
  confirm: '核赔确认',
};

/**
 * 同类失败共用 message key：重复触发时 antd **替换**而非堆叠 toast。
 * 开发态 StrictMode 双挂载会并发触发两次加载，此前失败会并列成两条。
 */
const MSG_KEY_LOAD = 'claim-adjust-load';
const MSG_KEY_ACTION = 'claim-adjust-action';

/**
 * 行快照（`input_snapshot`）解析结果。
 *
 * <p>后端 {@code Adjustment#inputSnapshotOf} 写入
 * `{bucket, bucketLabel, scope, ratioPercent}`；旧数据只有 `{bucket, scope}`，
 * 故 bucketLabel / ratioPercent 一律按可选处理——**旧行降级为显示英文桶码，而不是空白**。
 */
interface LineSnapshot {
  bucket?: string;
  bucketLabel?: string;
  scope?: string;
  ratioPercent?: number | null;
}

function parseSnapshot(raw?: string): LineSnapshot {
  if (!raw || !raw.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as LineSnapshot) : {};
  } catch {
    // 快照是历史数据，解析失败不该让整页报错；退回原始码展示即可
    return {};
  }
}

/** 医保内外（引擎写 `IN` / `OUT`）。 */
const SCOPE_LABEL: Record<string, string> = { IN: '医保内', OUT: '医保外' };

/** 补零：合计用 Number 累加即可（展示层，不承担金额精度责任；精确值以 total_amount 为准）。 */
const num = (v?: number | null): number => (v == null ? 0 : Number(v));

/** 后端 DTO（camelCase）→ schema fieldCode（snake_case）适配层。 */
function toSchemaData(r: AdjustmentResult): Record<string, unknown> {
  return {
    total_amount: r.totalAmount,
    formula_hash: r.formulaHash,
    snapshot: r.snapshot,
    lines: (r.lines ?? []).map((l: AdjustmentLine) => {
      const snap = parseSnapshot(l.inputSnapshot);
      return {
        line_type: l.lineType,
        line_type_label: adjustmentLineTypeLabel(l.lineType),
        liability_uuid: l.liabilityUuid,
        // 费用池：优先用后端落库的中文桶名；旧数据只有英文码时降级显示原始码
        bucket_label: snap.bucketLabel ?? snap.bucket ?? '-',
        scope_label: snap.scope ? (SCOPE_LABEL[snap.scope] ?? snap.scope) : '-',
        ratio_percent: snap.ratioPercent == null ? '-' : `${(Number(snap.ratioPercent) * 100).toFixed(2)}%`,
        fee_calculate: l.feeCalculate,
        fee_deduct: l.feeDeduct,
        ratio_payout: l.ratioPayout,
        fee_occupied: l.feeOccupied,
        fee_payout: l.feePayout,
        fee_remain: l.feeRemain,
        // 受控码 → 中文；未知码回退原始码（降级可见，不编造理由）
        zero_reason: l.zeroReason ? zeroReasonLabel(l.zeroReason) : '-',
        reject_type: l.rejectType ? rejectReasonLabel(l.rejectType) : '-',
        formula_hash: l.formulaHash,
      };
    }),
  };
}

/** 六归因汇总（按后端返回明细逐项累加）。 */
interface AttributionSummary {
  feeCalculate: number;
  feeDeduct: number;
  feePayout: number;
  zeroLineCount: number;
  lineCount: number;
}

function summarize(lines: AdjustmentLine[]): AttributionSummary {
  return lines.reduce<AttributionSummary>(
    (acc, l) => ({
      feeCalculate: acc.feeCalculate + num(l.feeCalculate),
      feeDeduct: acc.feeDeduct + num(l.feeDeduct),
      feePayout: acc.feePayout + num(l.feePayout),
      zeroLineCount: acc.zeroLineCount + (num(l.feePayout) === 0 ? 1 : 0),
      lineCount: acc.lineCount + 1,
    }),
    { feeCalculate: 0, feeDeduct: 0, feePayout: 0, zeroLineCount: 0, lineCount: 0 },
  );
}

const money = (v: number): string => `¥${v.toFixed(2)}`;

export function AdjustmentAttributionPage(): ReactElement {
  const { claimId, input, setInput, submit } = useClaimIdParam();
  const [result, setResult] = useState<AdjustmentResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    // 未指定案件：保持空态（引导选择），不发请求、不报「加载失败」。
    if (!claimId) {
      setResult(null);
      return;
    }
    setLoading(true);
    try {
      const resp = await adjustApi.latest(claimId);
      setResult(resp?.data ?? null);
    } catch (err) {
      message.error({
        content: resolveErrorMessage(err, '理算结果加载失败'),
        key: MSG_KEY_LOAD,
      });
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const runCalculate = async (mode: 'calculate' | 'recalculate') => {
    if (!claimId) {
      message.warning({ content: '请先查询并选择一个案件', key: MSG_KEY_ACTION });
      return;
    }
    setActing(true);
    try {
      const resp = await (mode === 'calculate' ? adjustApi.calculate(claimId) : adjustApi.recalculate(claimId));
      setResult(resp?.data ?? null);
      message.success({
        content: mode === 'calculate' ? '理算完成（结果已冻结）' : '重算完成',
        key: MSG_KEY_ACTION,
      });
    } catch (err) {
      const errorCode = resolveErrorCode(err);
      if (errorCode === 'SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED') {
        message.error({ content: '证据集未锁定，无法理算（请先锁定证据）', key: MSG_KEY_ACTION });
      } else if (errorCode === 'SC_CLAIM_LIABILITY_NOT_FOUND') {
        message.error({ content: '无生效责任配置，无法理算（绝不默认 90%）', key: MSG_KEY_ACTION });
      } else {
        message.error({ content: resolveErrorMessage(err, '理算失败'), key: MSG_KEY_ACTION });
      }
    } finally {
      setActing(false);
    }
  };

  const runConfirm = async () => {
    if (!claimId) {
      message.warning({ content: '请先查询并选择一个案件', key: MSG_KEY_ACTION });
      return;
    }
    setActing(true);
    try {
      await adjustApi.confirm(claimId);
      message.success({ content: '核赔已确认（结果锁定）', key: MSG_KEY_ACTION });
      await load();
    } catch (err) {
      message.error({ content: resolveErrorMessage(err, '确认失败'), key: MSG_KEY_ACTION });
    } finally {
      setActing(false);
    }
  };

  // 动作由 schema.actionBar 声明、共享引擎裁决可见性（单一真源）；页面仅按码执行 action 通道。
  // 无 visibleWhen 时全部可见；若后续按状态门控（如 REJECTING 才显拒绝动作），只改 schema 即可。
  const schemaData = useMemo(() => (result ? toSchemaData(result) : {}), [result]);
  const visibleActions = useMemo(
    () => resolveVisibleActions(schemaData, adjustSchema.actionBar ?? [], {}),
    [schemaData],
  );

  const handleAction = (code: string) => {
    if (code === 'execute') void runCalculate('calculate');
    else if (code === 'recalculate') void runCalculate('recalculate');
    else if (code === 'confirm') void runConfirm();
  };

  const summary = useMemo(() => summarize(result?.lines ?? []), [result]);

  /**
   * 明细合计与汇总金额是否一致。
   *
   * <p>两者不一致意味着「逐行归因解释不了总额」，这正是审计最需要发现的状态（例如某行被截断、
   * 或零基数池未出行）。此处只提示、不阻断——阻断会让页面在有历史脏数据时完全不可用。
   */
  const payoutMismatch =
    result != null &&
    result.lines.length > 0 &&
    Math.abs(summary.feePayout - num(result.totalAmount)) > 0.01;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="理算归因"
        description="逐行拆解「赔付金额怎么算出来的」：合理费用 → 免赔扣减 → 按比例应付 → 占额 → 实付 → 剩余额度。"
      />
      <Card
        title="理算明细"
        extra={
          <Space wrap>
            <CaseIdBar value={input} onChange={setInput} onSubmit={submit} loading={loading} />
            <Button icon={<ReloadOutlined />} onClick={() => void load()} disabled={!claimId}>
              刷新
            </Button>
            {visibleActions.map((a, i) => (
              <Button
                key={a.actionCode}
                type={i === 0 ? 'primary' : 'default'}
                loading={acting}
                // 未选案件时禁用：理算/确认都针对具体案件，点了只会白跑一次请求再报错。
                disabled={acting || !claimId}
                onClick={() => void handleAction(a.actionCode)}
              >
                {ACTION_LABELS[a.actionCode] ?? a.actionCode}
              </Button>
            ))}
          </Space>
        }
      >
        {!claimId ? (
          <CaseNotSelected hint="输入案件ID后查询理算结果，或直接执行理算" />
        ) : result == null ? (
          <Alert
            type="info"
            showIcon
            message="尚未理算"
            description="该案暂无理算结果。请先确保证据集已锁定，再点击「执行理算」。"
          />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {/*
              累计账不可用 = 跨案累计（年度免赔/额度）没读到。理算结果仍可看，但资金阶段会 fail-closed
              拒绝占额。不上屏的话，用户只会遇到「资金工作台点不动」而没有任何线索。
            */}
            {result.accumulationAvailable === false && (
              <Alert
                type="warning"
                showIcon
                message="跨案累计账未解析到（历史账本缺失）"
                description="本次理算结果可查看，但资金阶段会拒绝占额（账实不明不记账）。请联系管理员核对责任账户累计数据。"
              />
            )}
            {payoutMismatch && (
              <Alert
                type="warning"
                showIcon
                message="归因明细合计与理算总额不一致"
                description={`明细实付合计 ${money(summary.feePayout)}，理算总额 ${money(
                  num(result.totalAmount),
                )}。逐行归因解释不了总额，请复核规则版本或联系研发排查。`}
              />
            )}

            <Card type="inner" title="归因汇总">
              <Space size="large" wrap>
                <Statistic title="赔付总额" value={money(num(result.totalAmount))} />
                <Statistic title="合理费用合计" value={money(summary.feeCalculate)} />
                <Statistic title="免赔扣减合计" value={money(summary.feeDeduct)} />
                <Tooltip title="本次实际消抵的年度免赔额；资金阶段须累加进责任账，否则次案会重扣全额免赔">
                  <Statistic
                    title="本次免赔消抵"
                    value={
                      result.deductibleApplied == null ? '未回传' : money(num(result.deductibleApplied))
                    }
                  />
                </Tooltip>
                <Statistic title="明细行数" value={summary.lineCount} />
                <Statistic
                  title="零赔付行"
                  value={summary.zeroLineCount}
                  valueStyle={summary.zeroLineCount > 0 ? { color: '#cf1322' } : undefined}
                  suffix={summary.zeroLineCount > 0 ? '行（见归因码）' : ''}
                />
              </Space>
              {result.liabilityUuid && (
                <div style={{ marginTop: 12 }}>
                  <Typography.Text type="secondary">
                    命中责任：<Tag color="blue">{result.liabilityUuid}</Tag>
                  </Typography.Text>
                </div>
              )}
            </Card>

            <SchemaRenderer
              schema={adjustSchema as unknown as BoneUiSchema}
              data={schemaData}
              viewMode="form"
              loading={loading}
            />
          </Space>
        )}
      </Card>
    </div>
  );
}
