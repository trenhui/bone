import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import {
  Alert, Button, Card, Descriptions, Input, Modal, Radio, Space, Spin, Tag, Typography, message,
} from 'antd';
import { aiDisclosureApi, involvementMeta, type AiDisclosure } from '../services/aiDisclosure';
import { reviewTaskApi } from '../services/reviewTask';
import type { DecisionExecutionSummary } from '../services/types';
import { resolveErrorMessage } from '@bone/shared-utils';
import {
  REASON_LABELS, REASON_TYPES, buildDispositionEvent, buildImpressionEvent,
  emitWorkbenchEvent, type AiDisposition, type EventContext, type ReasonType,
} from '../workbench/telemetry';
import { persistWorkbenchEvent } from '../workbench/persist';

/**
 * AI 结论卡 —— 把 AI 从「藏在角落的面板」放进主决策面（方案 L1，最高 ROI）。
 *
 * 三条不可让步的约束
 * ------------------
 * 1. **四态 fail-closed**：只有 `AI_ASSISTED` 才出现采纳/驳回动作。`AI_STUB` / `AI_UNAVAILABLE` /
 *    `UNDETERMINED` 一律不允许被翻译成「无风险」或「AI 未参与」（沿用 aiDisclosure.ts 的语义）。
 * 2. **不编造置信度**：后端 disclosure / execution 没有置信度字段，就明写「未提供，不做估算」，
 *    而不是显示一个好看的 0.87。行业要求展示置信度以缓解自动化偏见，前提是数据真实存在。
 * 3. **不假装落库**：采纳/驳回产生结构化本地事件（曝光 → 裁决，带受控原因码、traceId、时间），
 *    由 `persistWorkbenchEvent` 异步写入后端 append-only 表；写库失败只提示、绝不谎称已落库（真机联调见设计提案 v2.1）。
 *    金额与资金全程不进入本卡，事件体永不含金额字段，后端 `@JsonAnySetter` 双保险拒绝任何越界字段。
 *
 * 业界依据：Insurance Thought Leadership 2026 把「AI 建议采纳率」列为领先指标，把用户绕开系统
 * 列为预警；Duck Creek 指导要求达到 90% 人机一致率后再开 STP；NAIC Model Bulletin 要求
 * 每条 AI 影响的不利决定可解释、可审计、有人类复核 ⇒ 驳回/修改强制 ReasonType。
 */
export interface AiSuggestionCardProps {
  claimId: string;
  scene?: EventContext['scene'];
}

export function AiSuggestionCard({ claimId, scene = 'case' }: AiSuggestionCardProps): ReactElement {
  const [disclosure, setDisclosure] = useState<AiDisclosure | null>(null);
  const [execution, setExecution] = useState<DecisionExecutionSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [disposition, setDisposition] = useState<AiDisposition>('ADOPT');
  const [reasonCode, setReasonCode] = useState<ReasonType | undefined>(undefined);
  const [comment, setComment] = useState('');
  const [lastRecord, setLastRecord] = useState<string | null>(null);
  const impressionSent = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    // 两个端点都是真实存在的只读契约；用 allSettled 保证一个 404 不拖垮整张卡。
    const [d, e] = await Promise.allSettled([
      aiDisclosureApi.disclosure(claimId),
      reviewTaskApi.execution(claimId),
    ]);
    if (d.status === 'fulfilled' && d.value?.code === 200) setDisclosure(d.value.data ?? null);
    else if (d.status === 'rejected') setError(resolveErrorMessage(d.reason, 'AI 参与披露加载失败'));
    if (e.status === 'fulfilled' && e.value?.code === 200) setExecution(e.value.data ?? null);
    setLoading(false);
  }, [claimId]);

  useEffect(() => {
    impressionSent.current = false;
    setDisclosure(null);
    setExecution(null);
    setLastRecord(null);
    void load();
  }, [load]);

  // 曝光是采纳率的分母：只在卡片真正渲染出建议后记录一次，避免 StrictMode 双挂载重复计数。
  useEffect(() => {
    if (!disclosure || scene === 'blind_label' || impressionSent.current) return;
    const built = buildImpressionEvent({
      claimId, traceId: disclosure.traceId, involvement: disclosure.involvement, scene,
    });
    if (built.ok) {
      emitWorkbenchEvent(built.event);
      impressionSent.current = true;
      // 异步落库（best-effort）：失败只记日志，不影响卡片展示，绝不谎称已落库。
      void persistWorkbenchEvent(built.event).then((pr) => {
        if (!pr.ok && pr.error && pr.error !== 'no-window') {
          console.warn('[AiSuggestionCard] 曝光事件写库失败：', pr.error);
        }
      });
    }
  }, [disclosure, scene, claimId]);

  const involvement = disclosure ? involvementMeta(disclosure.involvement) : null;
  const canDispose = disclosure?.involvement === 'AI_ASSISTED' && scene !== 'blind_label';
  const needsReason = disposition === 'REJECT' || disposition === 'MODIFY';

  const openDisposition = (next: AiDisposition) => {
    setDisposition(next);
    setReasonCode(undefined);
    setComment('');
    setOpen(true);
  };

  const submitDisposition = async () => {
    const built = buildDispositionEvent({
      claimId,
      traceId: disclosure?.traceId,
      involvement: disclosure?.involvement,
      scene,
      disposition,
      reasonCode,
      comment: comment.trim() || undefined,
      modifiedFields: disposition === 'MODIFY' ? ['decisionResult'] : undefined,
    });
    if (!built.ok) {
      message.error(built.error);
      return;
    }
    emitWorkbenchEvent(built.event);
    const base = `${built.event.disposition} · ${built.event.reasonCode ?? '无需原因码'} · ${built.event.at}`;
    const pr = await persistWorkbenchEvent(built.event);
    if (pr.ok) {
      setLastRecord(`${base} · ${pr.replayed ? '幂等重放（已存在同事件）' : '已落库'}`);
      message.success(pr.replayed ? '裁决事件已落库（幂等重放）' : '裁决事件已落库，服务端采纳率口径已更新');
    } else {
      setLastRecord(`${base} · 本地已记录但写库失败：${pr.error ?? '未知错误'}`);
      message.warning(`本地裁决事件已生成，但后端回流写库失败：${pr.error ?? '未知错误'}`);
    }
    setOpen(false);
  };

  return (
    <Card
      title={<Space>AI 结论卡<Tag color="blue">AI 参与显著标识</Tag></Space>}
      extra={<Button size="small" onClick={() => void load()} loading={loading}>刷新</Button>}
      size="small"
    >
      {loading ? <Spin size="small" tip="读取决策事实…" /> : null}
      {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} /> : null}

      {!loading && !disclosure ? (
        <Alert
          type="warning"
          showIcon
          message="暂无可判定的 AI 参与事实"
          description="未取到决策披露记录。按 fail-closed 口径，这不代表「AI 未参与」，也不提供采纳动作。"
        />
      ) : null}

      {disclosure && involvement ? (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type={involvement.tone}
            showIcon
            message={involvement.label}
            description={involvement.hint}
          />
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="决策结论">
              {execution?.decisionResult || disclosure.evidence.decisionResult || '未提供'}
            </Descriptions.Item>
            <Descriptions.Item label="置信度">
              <Typography.Text type="secondary">后端未提供，不做估算</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="决策图版本">{disclosure.evidence.graphVersion || '—'}</Descriptions.Item>
            <Descriptions.Item label="规则版本">{disclosure.evidence.ruleVersion || '—'}</Descriptions.Item>
            <Descriptions.Item label="轨迹节点数">{disclosure.evidence.traceNodeCount}</Descriptions.Item>
            <Descriptions.Item label="traceId">{disclosure.traceId || '—'}</Descriptions.Item>
            <Descriptions.Item label="AI 角色" span={2}>{disclosure.aiRole || '—'}</Descriptions.Item>
            <Descriptions.Item label="人工最终决策" span={2}>{disclosure.humanFinalDecision || '—'}</Descriptions.Item>
            <Descriptions.Item label="依据字段" span={2}>
              {disclosure.evidence.generatedFields.length > 0
                ? disclosure.evidence.generatedFields.join('、')
                : '本次执行未登记生成字段'}
            </Descriptions.Item>
          </Descriptions>

          {!canDispose ? (
            <Alert
              type="warning"
              showIcon
              message="当前不提供采纳 / 驳回"
              description="只有「真实模型参与（AI_ASSISTED）」且不在盲标禁区时，才允许产生 AI 裁决回流；其余状态必须人工在作业页完成终审。"
            />
          ) : (
            <Space wrap>
              <Button type="primary" onClick={() => openDisposition('ADOPT')}>采纳</Button>
              <Button danger onClick={() => openDisposition('REJECT')}>驳回</Button>
              <Button onClick={() => openDisposition('MODIFY')}>修改后采纳</Button>
              <Typography.Text type="secondary">金额与资金不在此卡决策</Typography.Text>
            </Space>
          )}

          {lastRecord ? (
            <Alert type="success" showIcon message="本地裁决事件已生成" description={lastRecord} />
          ) : null}

          <Alert
            type="info"
            showIcon
            message="回流契约状态：事件总线 + 后端已落库"
            description="曝光 / 采纳 / 驳回 / 修改记录为结构化事件（原因码、traceId、时间），并异步写入后端 append-only 表（/api/v1/claims/{claimId}/ai/feedback，网关已白名单），真实计入服务端采纳率；端点不触碰案件状态、不写资金、不含金额。写库失败只提示、绝不谎称已落库。"
          />
        </Space>
      ) : null}

      <Modal
        title={`AI 建议 · ${disposition === 'ADOPT' ? '采纳' : disposition === 'REJECT' ? '驳回' : '修改后采纳'}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={submitDisposition}
        okButtonProps={{ disabled: needsReason && !reasonCode }}
        okText="记录裁决"
        cancelText="取消"
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text>
            {needsReason
              ? '驳回与修改必须选择受控原因码（与后端 ReasonType 四值对齐），自由文本不能代替。'
              : '采纳可不填原因码；如填写将一并记录。'}
          </Typography.Text>
          <Radio.Group
            value={reasonCode}
            onChange={(e) => setReasonCode(e.target.value as ReasonType)}
            style={{ width: '100%' }}
          >
            <Space direction="vertical">
              {REASON_TYPES.map((code) => (
                <Radio key={code} value={code}>{REASON_LABELS[code]}（{code}）</Radio>
              ))}
            </Space>
          </Radio.Group>
          <Input.TextArea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={200}
            showCount
            placeholder="人工备注（可选，仅展示用，不进结构化回流）"
            rows={2}
          />
          <Typography.Text type="secondary">
            记录仅写入浏览器本地事件总线，不写库、不改变结论与金额。
          </Typography.Text>
        </Space>
      </Modal>
    </Card>
  );
}
