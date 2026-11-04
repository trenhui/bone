/**
 * AI 参与决策告知卡片（金发〔2026〕8号 §21 显著标识 / §22 人工最终决策 / PIPL 第 24 条）。
 *
 * <p>挂在核赔工作台「决策图轨迹回放」抽屉内，纯只读：不触发决策执行、不发起审核动作、不改金额。
 *
 * <p>三条硬约束：
 * 1. **三态不可压平**：`UNDETERMINED` 显示「无法确认」，绝不渲染成「AI 未参与」或「无风险」；
 * 2. **取数失败也 fail-closed**：接口异常时提示「无法确认」，不静默隐藏、不臆造结论；
 * 3. **未知受控码降级显示原始码**，不编造中文把未知状态伪装成正常态。
 */

import { useEffect, useState, type ReactElement } from 'react';
import { Alert, Card, Space, Spin, Tag, Typography } from 'antd';
import {
  aiDisclosureApi,
  involvementMeta,
  rightLabel,
  type AiDisclosure,
} from '../services/aiDisclosure';

interface AiDisclosureCardProps {
  claimId?: string | number | null;
}

const joinOrDash = (values: string[] | undefined): string =>
  values && values.length > 0 ? values.join('、') : '—';

export function AiDisclosureCard({ claimId }: AiDisclosureCardProps): ReactElement | null {
  const [data, setData] = useState<AiDisclosure | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = claimId === null || claimId === undefined ? '' : String(claimId);

  useEffect(() => {
    if (!key) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }
    let alive = true;
    setLoading(true);
    setError(null);
    aiDisclosureApi
      .disclosure(key)
      .then((res) => {
        if (!alive) {
          return;
        }
        const payload = res?.data ?? null;
        setData(payload);
        if (!payload) {
          setError('披露数据为空');
        }
      })
      .catch(() => {
        if (alive) {
          setData(null);
          setError('AI 参与决策告知暂不可用');
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [key]);

  if (!key) {
    return null;
  }

  const meta = data ? involvementMeta(data.involvement) : null;
  const rows: [string, string][] = data
    ? [
      ['追溯 ID', data.traceId || '—'],
      ['判定原因码', data.involvementReason || '—'],
      ['AI 角色', data.aiRole || '—'],
      ['决策结论', data.evidence.decisionResult || '—'],
      ['分诊原因', data.evidence.triageReason || '—'],
      ['结论消费', data.evidence.consumptionReason || '—'],
      [
        '规则 / 图版本',
        `${data.evidence.ruleVersion || '—'} / ${data.evidence.graphVersion || '—'}`,
      ],
      ['模型版本（执行快照）', data.evidence.declaredModelVersion || '—'],
      ['模型版本（节点自报）', data.evidence.reportedModelVersion || '—'],
      [
        '提示词版本（快照/自报）',
        `${data.evidence.declaredPromptVersion || '—'} / ${data.evidence.reportedPromptVersion || '—'}`,
      ],
      ['AI 生成字段', joinOrDash(data.evidence.generatedFields)],
      ['拒赔原因码', joinOrDash(data.evidence.rejectReasons)],
      ['证据缺口', joinOrDash(data.evidence.traceGaps)],
      ['轨迹节点数', String(data.evidence.traceNodeCount)],
    ]
    : [];

  return (
    <Card size="small" title="AI 参与决策告知" style={{ marginBottom: 16 }}>
      <Spin spinning={loading}>
        {error && !data ? (
          <Alert
            type="warning"
            showIcon
            message="无法确认 AI 是否参与"
            description={`${error}。不得据此认定 AI 未参与或风险已排除。`}
          />
        ) : null}
        {meta && data ? (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Alert
              type={meta.tone}
              showIcon
              message={
                <Space wrap>
                  <span>{data.label}</span>
                  <Tag
                    color={meta.tone === 'warning' ? 'orange' : 'blue'}
                    style={{ marginInlineEnd: 0 }}
                  >
                    {data.involvement}
                  </Tag>
                </Space>
              }
              description={meta.hint}
            />
            <Typography.Text>{data.humanFinalDecision}</Typography.Text>
            <Space wrap size={[4, 4]}>
              {data.rights.map((right) => (
                <Tag key={right} color="blue">
                  {rightLabel(right)}
                </Tag>
              ))}
            </Space>
            <div>
              {rows.map(([label, value]) => (
                <div key={label} style={{ display: 'flex', gap: 8, fontSize: 12 }}>
                  <Typography.Text type="secondary" style={{ minWidth: 150 }}>
                    {label}
                  </Typography.Text>
                  <Typography.Text style={{ wordBreak: 'break-all' }}>{value}</Typography.Text>
                </div>
              ))}
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {data.retentionNote}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {data.scopeNote}
            </Typography.Text>
          </Space>
        ) : null}
      </Spin>
    </Card>
  );
}
