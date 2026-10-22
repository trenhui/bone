import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Input, Space, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  adjustApi,
  type AdjustmentLine,
  type AdjustmentResult,
} from '../services/claimApi';
import { SchemaRenderer, resolveVisibleActions } from '@bone/ui-schema';
import type { BoneUiSchema } from '@bone/ui-schema';
import adjustSchema from '../schemas/adjust-attribution.ui-schema.json';

/**
 * 理算归因页（synapClaim A6，只读展示）—— **由 UI Schema JSON 真正驱动**。
 *
 * 展示体（分区/列/标签/六归因 CHILD_TABLE/金额/MONEY）全部来自
 * `src/schemas/adjust-attribution.ui-schema.json`，经本地 SchemaRenderer 渲染；
 * 后端 DTO 仅通过 `toSchemaData` 适配成 schema 的 fieldCode 键值。
 *
 * 操作性动作（执行理算/重算/确认）也由 schema 的 `actionBar` 声明、经共享引擎
 * `resolveVisibleActions` 裁决可见性，再由页面层走 action 通道（受控幂等）执行——
 * 展示与动作均 schema 驱动，单一真源在 `adjust-attribution.ui-schema.json`。
 */

const REVIEWER_ID = 'reviewer-001'; // 演示环境固定核赔员；生产取登录态

/** actionBar 动作码 → 展示标签（展示层映射，判定逻辑由 schema + 引擎单一真源负责）。 */
const ACTION_LABELS: Record<string, string> = {
  execute: '执行理算',
  recalculate: '重算',
  confirm: '核赔确认',
};

/** 后端 DTO（camelCase）→ schema fieldCode（snake_case）适配层。 */
function toSchemaData(r: AdjustmentResult): Record<string, unknown> {
  return {
    total_amount: r.totalAmount,
    formula_hash: r.formulaHash,
    snapshot: r.snapshot,
    lines: (r.lines ?? []).map((l: AdjustmentLine) => ({
      line_type: l.lineType,
      liability_uuid: l.liabilityUuid,
      fee_calculate: l.feeCalculate,
      fee_deduct: l.feeDeduct,
      ratio_payout: l.ratioPayout,
      fee_occupied: l.feeOccupied,
      fee_payout: l.feePayout,
      fee_remain: l.feeRemain,
      zero_reason: l.zeroReason,
      reject_type: l.rejectType,
      formula_hash: l.formulaHash,
    })),
  };
}

export function AdjustmentAttributionPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '1';
  const [input, setInput] = useState(claimId);
  const [result, setResult] = useState<AdjustmentResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await adjustApi.latest(claimId);
      setResult(resp?.data ?? null);
    } catch {
      message.error('理算结果加载失败');
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const runCalculate = async (mode: 'calculate' | 'recalculate') => {
    setActing(true);
    try {
      const resp = await (mode === 'calculate' ? adjustApi.calculate(claimId) : adjustApi.recalculate(claimId));
      setResult(resp?.data ?? null);
      message.success(mode === 'calculate' ? '理算完成（结果已冻结）' : '重算完成');
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED') {
        message.error('证据集未锁定，无法理算（请先锁定证据）');
      } else if (errorCode === 'SC_CLAIM_LIABILITY_NOT_FOUND') {
        message.error('无生效责任配置，无法理算（绝不默认 90%）');
      } else {
        message.error('理算失败');
      }
    } finally {
      setActing(false);
    }
  };

  const runConfirm = async () => {
    setActing(true);
    try {
      await adjustApi.confirm(claimId, REVIEWER_ID);
      message.success('核赔已确认（结果锁定）');
      void load();
    } catch {
      message.error('确认失败');
    } finally {
      setActing(false);
    }
  };

  const onSearch = () => {
    const id = input.trim();
    if (!id) return;
    setSearchParams({ claimId: id });
  };

  // 动作由 schema.actionBar 声明、共享引擎裁决可见性（单一真源）；页面仅按码执行 action 通道。
  // 无 visibleWhen 时全部可见；若后续按状态门控（如 REJECTING 才显拒绝动作），只改 schema 即可。
  const visibleActions = useMemo(
    () => resolveVisibleActions(result ? toSchemaData(result) : {}, adjustSchema.actionBar ?? [], {}),
    [result],
  );
  const handleAction = (code: string) => {
    if (code === 'execute') void runCalculate('calculate');
    else if (code === 'recalculate') void runCalculate('recalculate');
    else if (code === 'confirm') void runConfirm();
  };

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="理算归因（A6 · schema 驱动）"
        extra={
          <Space wrap>
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
            {visibleActions.map((a, i) => (
              <Button
                key={a.actionCode}
                type={i === 0 ? 'primary' : 'default'}
                loading={acting && a.actionCode === 'execute'}
                disabled={acting}
                onClick={() => void handleAction(a.actionCode)}
              >
                {ACTION_LABELS[a.actionCode] ?? a.actionCode}
              </Button>
            ))}
          </Space>
        }
      >
        {result == null ? (
          <Alert
            type="info"
            showIcon
            message="尚未理算"
            description="该案暂无理算结果。请先确保证据集已锁定，再点击「执行理算」。"
          />
        ) : (
          <SchemaRenderer
            schema={adjustSchema as unknown as BoneUiSchema}
            data={toSchemaData(result)}
            viewMode="form"
            loading={loading}
          />
        )}
      </Card>
    </div>
  );
}
