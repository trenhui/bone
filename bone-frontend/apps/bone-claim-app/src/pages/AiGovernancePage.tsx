import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Descriptions, Input, InputNumber, List, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined, ApiOutlined, SaveOutlined } from '@ant-design/icons';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import {
  aiGovernanceApi,
  calibrationEvidenceMeta,
  capabilityStateMeta,
  CONFIG_LIMIT_DIRECTION,
  drillMeta,
  gatesMeta,
  governanceWindow,
  isLoosened,
  metricText,
  participationMeta,
  probeMeta,
  rolloutVerdictMeta,
  stageMeta,
  verdictMeta,
  type AiCalibrationReport,
  type AiConfigGovernance,
  type AiRuntimeHealth,
  type GovernanceTone,
  type GuardCheckResp,
  type ProviderProbeReport,
  type ReadinessSnapshot,
  type RolloutDrillReport,
  type RolloutSnapshot,
  type StatusMeta,
} from '../services/aiGovernance';

type Load<T> = { state: 'loading' | 'ok' | 'error'; data?: T; error?: string };

interface GovernanceState {
  readiness: Load<ReadinessSnapshot>;
  runtime: Load<AiRuntimeHealth>;
  calibration: Load<AiCalibrationReport>;
  rollout: Load<RolloutSnapshot>;
  drill: Load<RolloutDrillReport>;
  config: Load<AiConfigGovernance>;
}

const INITIAL: GovernanceState = {
  readiness: { state: 'loading' },
  runtime: { state: 'loading' },
  calibration: { state: 'loading' },
  rollout: { state: 'loading' },
  drill: { state: 'loading' },
  config: { state: 'loading' },
};

const TONE_COLOR: Record<GovernanceTone, string> = {
  success: 'success',
  warning: 'warning',
  error: 'error',
  neutral: 'default',
};

function StatusTag({ meta }: { meta: StatusMeta }): ReactElement {
  return <Tag color={TONE_COLOR[meta.tone]}>{meta.label}</Tag>;
}

function Section({
  title,
  load,
  children,
}: {
  title: string;
  load: Load<unknown>;
  children: (data: never) => ReactElement;
}): ReactElement {
  if (load.state === 'loading') return <Card title={title}><Spin /></Card>;
  if (load.state === 'error' || !load.data) {
    return (
      <Card title={title}>
        <Alert type="error" showIcon message="数据不可用" description={load.error ?? '端点未返回数据'} />
      </Card>
    );
  }
  return <Card title={title}>{children(load.data as never)}</Card>;
}

function MetaDetail({ meta }: { meta: StatusMeta }): ReactElement {
  return (
    <Space size={8} wrap>
      <StatusTag meta={meta} />
      <Typography.Text type="secondary">{meta.detail}</Typography.Text>
    </Space>
  );
}

/**
 * 配置字段元数据：金额类「更低=更严格」，置信度类「更高=更严格」，方向相反不能共用一套规则。
 * tighten 决定前端 min/max 与提示；后端才是有权威的判据（逐字段相对当前生效值比较）。
 */
const LIMIT_FIELDS: ReadonlyArray<{
  key: keyof AiConfigGovernance['limits'];
  label: string;
  step: number;
  precision: number;
}> = [
  { key: 'autoAmountLimit', label: '自动通过金额上限（元）', step: 1, precision: 2 },
  { key: 'dualReviewAmount', label: '双人复核金额门（元）', step: 1, precision: 2 },
  { key: 'aiManualThreshold', label: 'AI 置信度强制人工线', step: 0.01, precision: 4 },
  { key: 'aiReviewThreshold', label: 'AI 置信度复核档上界', step: 0.01, precision: 4 },
  { key: 'ocrManualThreshold', label: 'OCR 置信度熔断线', step: 0.01, precision: 4 },
];

/** 按错误码给可行动文案；兜底不静默，避免「点了没反应」。 */
function configErrorText(code: string | undefined): string {
  if (code === 'SC_CLAIM_CONFIG_RATCHET_VIOLATION') {
    return '配置放宽被拒：只能提交与当前生效值相等或更严格的配置';
  }
  if (code === 'SC_CLAIM_CONFIG_WRITE_DISABLED') {
    return '配置写入已关闭或未配置，当前为只读';
  }
  if (code === 'SC_CLAIM_CONFIG_INVALID') {
    return '配置请求非法（未知键、类型错误或越界值）';
  }
  return '配置保存失败，请稍后重试或联系管理员';
}

/**
 * 分诊阈值配置面板。写入能力完全由服务端 writeEnabled 驱动：关闭时只展示当前值与只读说明，
 * 不渲染任何可提交控件，消除「页面可编辑但提交失败」的错误暗示（方案 A 双闸门）。
 */
function ConfigPanel({
  load,
  onSaved,
}: {
  load: Load<AiConfigGovernance>;
  onSaved: () => void;
}): ReactElement {
  const [draft, setDraft] = useState<AiConfigGovernance['limits'] | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDraft(load.state === 'ok' && load.data ? { ...load.data.limits } : null);
  }, [load]);

  if (load.state === 'loading') {
    return <Card title="分诊阈值配置（仅收紧）"><Spin /></Card>;
  }
  if (load.state === 'error' || !load.data || !draft) {
    return (
      <Card title="分诊阈值配置（仅收紧）">
        <Alert type="error" showIcon message="配置不可用"
          description={load.error ?? '端点未返回配置'} />
      </Card>
    );
  }

  const current = load.data.limits;
  const writeEnabled = load.data.writeEnabled;
  const changed = (Object.keys(current) as Array<keyof typeof current>)
    .filter((key) => Number(draft[key]) !== Number(current[key]));

  const submit = async () => {
    if (changed.length === 0) {
      message.info('没有需要提交的变更');
      return;
    }
    const loosened = changed.find((key) =>
      isLoosened(key, Number(current[key]), Number(draft[key])));
    if (loosened) {
      message.error('仅收紧校验未通过：该字段的修改是放宽，请调回更严格的方向');
      return;
    }
    const payload: Partial<AiConfigGovernance['limits']> = {};
    for (const key of changed) {
      payload[key] = Number(draft[key]);
    }
    setSaving(true);
    try {
      await aiGovernanceApi.updateTriageLimits(payload);
      message.success('配置已通过「仅收紧」校验并保存');
      onSaved();
    } catch (err) {
      message.error(configErrorText(resolveErrorCode(err)));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title="分诊阈值配置（仅收紧）"
      extra={
        <Space size={8} wrap>
          <Tag color={writeEnabled ? 'success' : 'default'}>
            {writeEnabled ? '写入已开启' : '只读'}
          </Tag>
          <Tag color="warning">金额只能调低 · 置信度只能调高</Tag>
        </Space>
      }
    >
      {!writeEnabled && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="当前为只读"
          description="写 API 开关 AI_CONFIG_WRITE 未开启或未配置，页面不展示可提交控件；如需调整请先由管理员开启写开关。"
        />
      )}
      <Table<(typeof LIMIT_FIELDS)[number]>
        rowKey="key"
        size="small"
        pagination={false}
        dataSource={LIMIT_FIELDS}
        columns={[
          { title: '配置项', dataIndex: 'label', key: 'label' },
          {
            title: '当前生效值', key: 'current',
            render: (_value, row) => <Typography.Text>{metricText(current[row.key])}</Typography.Text>,
          },
          {
            title: '新值', key: 'draft',
            render: (_value, row) => writeEnabled ? (
              <InputNumber
                min={CONFIG_LIMIT_DIRECTION[row.key] === 'higher' ? current[row.key] : 0}
                max={CONFIG_LIMIT_DIRECTION[row.key] === 'lower' ? current[row.key] : 1}
                step={row.step}
                precision={row.precision}
                value={draft[row.key]}
                onChange={(v) => setDraft((prev) => (
                  prev ? { ...prev, [row.key]: v === null ? current[row.key] : Number(v) } : prev
                ))}
              />
            ) : <Typography.Text type="secondary">—（只读）</Typography.Text>,
          },
          {
            title: '收紧方向', key: 'tighten',
            render: (_value, row) => (
              <Tag color="blue">
                {CONFIG_LIMIT_DIRECTION[row.key] === 'lower' ? '只能调低' : '只能调高'}
              </Tag>
            ),
          },
        ]}
      />
      {writeEnabled && (
        <Space style={{ marginTop: 12 }} wrap>
          <Button type="primary" icon={<SaveOutlined />} loading={saving}
            disabled={changed.length === 0} onClick={() => void submit()}>
            保存（仅收紧）
          </Button>
          <Button disabled={changed.length === 0}
            onClick={() => setDraft({ ...current })}>
            撤销修改
          </Button>
          <Typography.Text type="secondary">
            {changed.length === 0
              ? '无未保存变更'
              : `待提交 ${changed.length} 项；放宽将被后端拒绝且不会改库`}
          </Typography.Text>
        </Space>
      )}
    </Card>
  );
}

/**
 * 逐案守门预演：wouldBlock / blockers / scopeNote 全部取后端原文，前端不自行推断，
 * 也不把 scopeNote 改写成「部署门禁通过即可放款」。真实自动通过入口已后端 fail-closed。
 */
function GuardCheckPanel(): ReactElement {
  const [claimId, setClaimId] = useState('');
  const [result, setResult] = useState<GuardCheckResp | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const run = async () => {
    const id = claimId.trim();
    if (!id) return;
    setRunning(true);
    setError(null);
    setResult(null);
    try {
      const resp = await aiGovernanceApi.guardCheck(id);
      if (resp?.code !== 200) {
        setError(resp?.message || '后端拒绝了守门预演请求');
        return;
      }
      setResult(resp?.data ?? null);
    } catch (err) {
      setError(resolveErrorMessage(err, '守门预演失败'));
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card title="逐案守门预演">
      <Space wrap>
        <Input
          aria-label="预演案件 ID"
          placeholder="输入 claimId"
          value={claimId}
          onChange={(e) => setClaimId(e.target.value)}
          onPressEnter={() => void run()}
          style={{ width: 260 }}
        />
        <Button type="primary" loading={running} disabled={!claimId.trim()} onClick={() => void run()}>
          预演守门
        </Button>
      </Space>
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
        此处为预演，真实自动通过入口已 fail-closed 拦截（blocker 非空转人工）；本面板不改变任何案件状态。
      </Typography.Text>
      {error && (
        <Alert type="error" showIcon style={{ marginTop: 12 }} message="预演失败" description={error} />
      )}
      {result && (
        <div style={{ marginTop: 12 }}>
          <Space wrap>
            <Typography.Text strong>判定：</Typography.Text>
            <Tag color={result.wouldBlock ? 'red' : 'green'}>
              {result.wouldBlock ? '拦截' : '通过'}
            </Tag>
          </Space>
          <div style={{ marginTop: 8 }}>
            <Typography.Text strong>阻断项（{result.blockers?.length ?? 0}）：</Typography.Text>
            {result.blockers?.length ? (
              <List
                size="small"
                dataSource={result.blockers}
                renderItem={(blocker) => (
                  <List.Item style={{ padding: '2px 0' }}>{blocker}</List.Item>
                )}
              />
            ) : (
              <Typography.Text type="secondary" style={{ display: 'block' }}>无阻断项</Typography.Text>
            )}
          </div>
          <Alert
            type="info"
            showIcon
            style={{ marginTop: 12 }}
            message="范围说明（后端原文，原样上屏）"
            description={result.scopeNote}
          />
        </div>
      )}
    </Card>
  );
}

export function AiGovernancePage(): ReactElement {
  const navigate = useNavigate();
  const [state, setState] = useState<GovernanceState>(INITIAL);
  // 探活独立于默认加载：端点每次真实消耗一次 provider 用量，只能由用户点击触发。
  const [probe, setProbe] = useState<{
    running: boolean;
    report: ProviderProbeReport | null;
    error: string | null;
    clicked: boolean;
  }>({ running: false, report: null, error: null, clicked: false });

  const runProbe = useCallback(async () => {
    setProbe({ running: true, report: null, error: null, clicked: true });
    try {
      const report = await aiGovernanceApi.providerProbe();
      setProbe({ running: false, report, error: null, clicked: true });
    } catch (e) {
      setProbe({
        running: false, report: null, clicked: true,
        error: String((e as { message?: string })?.message ?? e ?? '未知错误'),
      });
    }
  }, []);

  const load = useCallback(async () => {
    setState(INITIAL);
    const { from, to } = governanceWindow();
    const [readiness, runtime, calibration, rollout, drill, config] = await Promise.allSettled([
      aiGovernanceApi.readiness(),
      aiGovernanceApi.runtimeHealth(from, to),
      aiGovernanceApi.calibration(),
      aiGovernanceApi.rollout(from, to),
      aiGovernanceApi.drill(),
      aiGovernanceApi.config(),
    ]);
    const settle = <T,>(r: PromiseSettledResult<T>): Load<T> =>
      r.status === 'fulfilled'
        ? { state: 'ok', data: r.value }
        : { state: 'error', error: String(r.reason?.message ?? r.reason ?? '未知错误') };
    setState({
      readiness: settle(readiness),
      runtime: settle(runtime),
      calibration: settle(calibration),
      rollout: settle(rollout),
      drill: settle(drill),
      config: settle(config),
    });
  }, []);

  useEffect(() => { void load(); }, [load]);

  const gates = state.readiness.state === 'ok' && state.readiness.data
    ? gatesMeta(state.readiness.data.deploymentGatesPassed)
    : gatesMeta(null);

  return (
    <div style={{ padding: 16 }}>
      <PageHeader
        title="AI 治理"
        description="运行期证据与配置治理：能力就绪、模型参与、盲标校准、放量档位、回滚演练、仅收紧阈值配置（受写开关控制）与连通性探活"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button>
            <Button icon={<ApiOutlined />} loading={probe.running} onClick={() => void runProbe()}>
              provider 连通性探活
            </Button>
            <Button onClick={() => navigate('/review')}>返回核赔工作台</Button>
          </Space>
        }
      />

      <Alert
        type={gates.tone === 'error' ? 'error' : gates.tone === 'success' ? 'success' : 'warning'}
        showIcon
        message={<Space size={8} wrap><Typography.Text strong>部署门禁</Typography.Text><StatusTag meta={gates} /></Space>}
        description={gates.detail}
        style={{ marginBottom: 16 }}
      />

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Section title="能力就绪" load={state.readiness}>
          {(raw: never) => {
            const d = raw as unknown as ReadinessSnapshot;
            return (
              <>
                <Descriptions column={2} size="small" bordered style={{ marginBottom: 12 }}>
                  <Descriptions.Item label="风险等级">{metricText(d.riskTier)}</Descriptions.Item>
                  <Descriptions.Item label="自动通过开关">
                    {d.autoApproveEnabled === true ? '开启' : d.autoApproveEnabled === false ? '关闭' : '未测过'}
                  </Descriptions.Item>
                  <Descriptions.Item label="门禁阻断项">{d.blockers?.length ?? 0}</Descriptions.Item>
                  <Descriptions.Item label="范围说明">{metricText(d.scopeNote)}</Descriptions.Item>
                </Descriptions>
                <Table
                  rowKey="capabilityId"
                  size="small"
                  pagination={false}
                  dataSource={d.capabilities ?? []}
                  columns={[
                    { title: '能力', dataIndex: 'capabilityId', key: 'capabilityId' },
                    {
                      title: '状态', dataIndex: 'state', key: 'state',
                      render: (v: string) => <StatusTag meta={capabilityStateMeta(v)} />,
                    },
                    { title: '原因', dataIndex: 'reason', key: 'reason' },
                    { title: '整改项', dataIndex: 'remediation', key: 'remediation' },
                  ]}
                />
                {(d.blockers ?? []).length > 0 && (
                  <Alert type="warning" showIcon style={{ marginTop: 12 }} message="未解除门禁项"
                    description={(d.blockers ?? []).join('；')} />
                )}
              </>
            );
          }}
        </Section>

        <GuardCheckPanel />

        <Section title="运行期模型参与" load={state.runtime}>
          {(raw: never) => {
            const d = raw as unknown as AiRuntimeHealth;
            return (
              <>
                <MetaDetail meta={participationMeta(d.participationRate)} />
                <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                  统计窗口：{metricText(d.windowFrom)} ~ {metricText(d.windowTo)}
                </Typography.Text>
                <Descriptions column={3} size="small" bordered style={{ marginTop: 12 }}>
                  <Descriptions.Item label="扫描节点数">{metricText(d.nodesScanned)}</Descriptions.Item>
                  <Descriptions.Item label="真实模型参与">{metricText(d.aiParticipated)}</Descriptions.Item>
                  <Descriptions.Item label="确定性替身">{metricText(d.aiStubbed)}</Descriptions.Item>
                  <Descriptions.Item label="模型未证实">{metricText(d.aiModelUnproven)}</Descriptions.Item>
                  <Descriptions.Item label="降级">{metricText(d.aiDegraded)}</Descriptions.Item>
                  <Descriptions.Item label="无法判定">{metricText(d.aiUndetermined)}</Descriptions.Item>
                  <Descriptions.Item label="输出不可解析">{metricText(d.unparsableOutputs)}</Descriptions.Item>
                  <Descriptions.Item label="归因覆盖率">{metricText(d.attributionCoverage)}</Descriptions.Item>
                  <Descriptions.Item label="证据截断">{d.truncated === true ? '是' : d.truncated === false ? '否' : '未测过'}</Descriptions.Item>
                </Descriptions>
              </>
            );
          }}
        </Section>

        <Section title="独立盲标校准" load={state.calibration}>
          {(raw: never) => {
            const d = raw as unknown as AiCalibrationReport;
            const c = d.calibration;
            return (
              <>
                <MetaDetail meta={verdictMeta(c?.verdict)} />
                <Descriptions column={3} size="small" bordered style={{ marginTop: 12 }}>
                  <Descriptions.Item label="样本数">{metricText(c?.sampleCount ?? d.sampleCount)}</Descriptions.Item>
                  <Descriptions.Item label="不可解析样本">{metricText(d.unparseableSamples)}</Descriptions.Item>
                  <Descriptions.Item label="分桶数">{metricText(c?.bucketCount)}</Descriptions.Item>
                  <Descriptions.Item label="ECE">{metricText(c?.expectedCalibrationError)}</Descriptions.Item>
                  <Descriptions.Item label="Brier">{metricText(c?.brierScore)}</Descriptions.Item>
                  <Descriptions.Item label="样本下限">{metricText(d.effectiveMinSamples)}</Descriptions.Item>
                  <Descriptions.Item label="当前阈值">{metricText(c?.currentThreshold)}</Descriptions.Item>
                  <Descriptions.Item label="建议阈值">{metricText(c?.recommendedThreshold)}</Descriptions.Item>
                  <Descriptions.Item label="建议阈值覆盖数">{metricText(c?.coveredByRecommended)}</Descriptions.Item>
                </Descriptions>
                {c?.basis ? <Typography.Text type="secondary">{c.basis}</Typography.Text> : null}
                {(() => {
                  const evidence = calibrationEvidenceMeta(d);
                  if (!evidence.renderable) {
                    return (
                      <div style={{ marginTop: 12 }}>
                        <MetaDetail meta={evidence} />
                      </div>
                    );
                  }
                  return (
                    <Space direction="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
                      <Typography.Text strong>校准分桶明细</Typography.Text>
                      <Table
                        rowKey={(_, i) => `bucket-${i}`}
                        size="small"
                        pagination={false}
                        dataSource={evidence.buckets}
                        columns={[
                          { title: '区间起', dataIndex: 'bucketFrom', key: 'bucketFrom',
                            render: (v: number | string | null) => metricText(v) },
                          { title: '区间止', dataIndex: 'bucketTo', key: 'bucketTo',
                            render: (v: number | string | null) => metricText(v) },
                          { title: '样本数', dataIndex: 'sampleCount', key: 'sampleCount',
                            render: (v: number | null) => metricText(v) },
                          { title: '平均置信度', dataIndex: 'meanConfidence', key: 'meanConfidence',
                            render: (v: number | string | null) => metricText(v) },
                          { title: '准确率', dataIndex: 'accuracy', key: 'accuracy',
                            render: (v: number | string | null) => metricText(v) },
                          { title: '校准差', dataIndex: 'gap', key: 'gap',
                            render: (v: number | string | null) => metricText(v) },
                        ]}
                      />
                      <Typography.Text strong>校准切片明细</Typography.Text>
                      <Table
                        rowKey="sliceId"
                        size="small"
                        pagination={false}
                        dataSource={evidence.slices}
                        columns={[
                          { title: '切片', dataIndex: 'sliceId', key: 'sliceId' },
                          { title: '样本数', dataIndex: 'sampleCount', key: 'sampleCount',
                            render: (v: number | null) => metricText(v) },
                          { title: 'Brier', dataIndex: 'brierScore', key: 'brierScore',
                            render: (v: number | string | null) => metricText(v) },
                          { title: '准确率', dataIndex: 'accuracy', key: 'accuracy',
                            render: (v: number | string | null) => metricText(v) },
                        ]}
                      />
                    </Space>
                  );
                })()}
              </>
            );
          }}
        </Section>

        <Section title="放量档位" load={state.rollout}>
          {(raw: never) => {
            const d = raw as unknown as RolloutSnapshot;
            return (
              <>
                <Space size={12} wrap>
                  <StatusTag meta={stageMeta(d.stage)} />
                  <StatusTag meta={rolloutVerdictMeta(d.verdict)} />
                </Space>
                <Descriptions column={3} size="small" bordered style={{ marginTop: 12 }}>
                  <Descriptions.Item label="放量比例">{metricText(d.rolloutPct)}</Descriptions.Item>
                  <Descriptions.Item label="抽检数">{metricText(d.inspectedCount)}</Descriptions.Item>
                  <Descriptions.Item label="差异数">{metricText(d.diffCount)}</Descriptions.Item>
                  <Descriptions.Item label="错误率">{metricText(d.errorRate)}</Descriptions.Item>
                  <Descriptions.Item label="错误率目标">{metricText(d.errorRateTarget)}</Descriptions.Item>
                  <Descriptions.Item label="样本充分">{d.sampleSufficient === true ? '是' : d.sampleSufficient === false ? '否' : '未测过'}</Descriptions.Item>
                  <Descriptions.Item label="下一档位">{metricText(d.nextStage)}</Descriptions.Item>
                  <Descriptions.Item label="可晋级">{d.promotionReady === true ? '是' : d.promotionReady === false ? '否' : '未测过'}</Descriptions.Item>
                  <Descriptions.Item label="生效中">{d.active === true ? '是' : d.active === false ? '否' : '未测过'}</Descriptions.Item>
                </Descriptions>
                {(d.promotionBlockers ?? []).length > 0 && (
                  <Alert type="warning" showIcon style={{ marginTop: 12 }} message="晋级阻断项"
                    description={(d.promotionBlockers ?? []).join('；')} />
                )}
              </>
            );
          }}
        </Section>

        <Section title="回滚演练" load={state.drill}>
          {(raw: never) => {
            const d = raw as unknown as RolloutDrillReport;
            return (
              <>
                <MetaDetail meta={drillMeta(d.autoSettlementAfterRollback)} />
                <Descriptions column={3} size="small" bordered style={{ marginTop: 12 }}>
                  <Descriptions.Item label="当前档位">{metricText(d.currentStage)}</Descriptions.Item>
                  <Descriptions.Item label="回滚目标">{metricText(d.rollbackTargetStage)}</Descriptions.Item>
                  <Descriptions.Item label="已到底层">{d.atFloor === true ? '是' : d.atFloor === false ? '否' : '未测过'}</Descriptions.Item>
                  <Descriptions.Item label="完整性">{d.integrityIntact === true ? '完好' : d.integrityIntact === false ? '破坏' : '未测过'}</Descriptions.Item>
                  <Descriptions.Item label="写操作次数">{metricText(d.mutations)}</Descriptions.Item>
                  <Descriptions.Item label="禁止项">{metricText((d.forbiddenMutations ?? []).length)}</Descriptions.Item>
                </Descriptions>
              </>
            );
          }}
        </Section>

        <ConfigPanel load={state.config} onSaved={() => void load()} />

        <Card title="provider 连通性探活">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <MetaDetail meta={probeMeta(probe.report, probe.error)} />
            {probe.report ? (
              <>
                <Descriptions column={3} size="small" bordered>
                  <Descriptions.Item label="配置态">
                    {probe.report.configured ? '已配置' : '未配置'}
                  </Descriptions.Item>
                  <Descriptions.Item label="本次可达">
                    {probe.report.reachable ? '是' : '否'}
                  </Descriptions.Item>
                  <Descriptions.Item label="耗时(ms)">{metricText(probe.report.latencyMs)}</Descriptions.Item>
                  <Descriptions.Item label="模型标识">{metricText(probe.report.model)}</Descriptions.Item>
                  <Descriptions.Item label="模型来源">{metricText(probe.report.provenance)}</Descriptions.Item>
                  <Descriptions.Item label="失败原因码">{metricText(probe.report.reason)}</Descriptions.Item>
                </Descriptions>
                {probe.report.action ? (
                  <Alert type="info" showIcon message="处置动作" description={probe.report.action} />
                ) : null}
                {probe.report.note ? (
                  <Typography.Text type="secondary">{probe.report.note}</Typography.Text>
                ) : null}
              </>
            ) : null}
          </Space>
        </Card>
      </Space>
    </div>
  );
}
