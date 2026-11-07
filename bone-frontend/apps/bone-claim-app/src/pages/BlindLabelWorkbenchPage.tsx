import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import {
  agreementMeta,
  blindLabelApi,
  scanLabelResult,
  scanPayload,
  statusMeta,
  type BlindLabelTaskView,
  type StatusMeta,
} from '../services/blindLabel';

/**
 * 独立盲标工作台（hidden 路由 /blind-labels，从核赔工作台「盲标」深链进入）。
 *
 * 诚实纪律（与后端契约同源，不是 UI 取舍）：
 *   · 解盲前任何视图不含 AI 侧字段——后端解盲前本就没有 AI 数据，前端不做「AI 建议预览」；
 *   · 盲标结论一次性提交，提交后不可改写；
 *   · AI_ABSENT ≠ 配对失败：它表示 AI 根本没参与本案决策，样本不进模型评测；
 *   · status/agreement 未知码按原文降级展示，不猜测语义。
 */

const TONE_COLOR = { success: 'success', warning: 'warning', error: 'error', neutral: 'default' } as const;

function StatusTag({ meta }: { meta: StatusMeta }): ReactElement {
  return <Tag color={TONE_COLOR[meta.tone]}>{meta.label}</Tag>;
}

const ASSIGNER_STORAGE_KEY = 'blindLabel.assigneeId';

/** 美化 JSON 展示：解析失败原样显示，绝不编造格式化结果。 */
function prettyJson(raw: string | null | undefined): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function JsonBlock({ title, raw, empty }: { title: string; raw: string | null; empty: string }): ReactElement {
  return (
    <Card size="small" title={title}>
      {raw ? (
        <Typography.Paragraph>
          <pre style={{ margin: 0, maxHeight: 320, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{prettyJson(raw)}</pre>
        </Typography.Paragraph>
      ) : (
        <Typography.Text type="secondary">{empty}</Typography.Text>
      )}
    </Card>
  );
}

export function BlindLabelWorkbenchPage(): ReactElement {
  const [assigneeId, setAssigneeId] = useState<string>(() => localStorage.getItem(ASSIGNER_STORAGE_KEY) ?? '');
  const [tasks, setTasks] = useState<BlindLabelTaskView[]>([]);
  const [listError, setListError] = useState<string | null>(null);
  const [listLoading, setListLoading] = useState(false);

  const [dispatchClaimId, setDispatchClaimId] = useState<string>('');
  const [payloadText, setPayloadText] = useState('');
  const [dispatching, setDispatching] = useState(false);

  const [selected, setSelected] = useState<BlindLabelTaskView | null>(null);
  const [resultText, setResultText] = useState('');
  const [acting, setActing] = useState(false);

  const refreshList = useCallback(async (who: string) => {
    if (!who.trim()) {
      setListError('先填写标注人 ID');
      setTasks([]);
      return;
    }
    setListLoading(true);
    setListError(null);
    try {
      setTasks(await blindLabelApi.list(who.trim()));
    } catch (e) {
      setTasks([]);
      setListError(String((e as { message?: string })?.message ?? e ?? '未知错误'));
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => { void refreshList(assigneeId); }, [refreshList, assigneeId]);

  const refreshSelected = useCallback(async (id: string) => {
    try {
      setSelected(await blindLabelApi.get(id));
    } catch (e) {
      message.error(String((e as { message?: string })?.message ?? e ?? '查询失败'));
    }
  }, []);

  const persistAssignee = (v: string) => {
    setAssigneeId(v);
    if (v.trim()) localStorage.setItem(ASSIGNER_STORAGE_KEY, v.trim());
    else localStorage.removeItem(ASSIGNER_STORAGE_KEY);
  };

  const payloadScan = scanPayload(payloadText);

  const doDispatch = async () => {
    const claimId = dispatchClaimId.trim();
    if (!/^\d{1,19}$/.test(claimId) || !assigneeId.trim()) {
      message.warning('需要合法的案件 ID（纯数字）与标注人 ID');
      return;
    }
    if (!payloadScan.ok) {
      message.error(payloadScan.structureError ?? '载荷未通过预检');
      return;
    }
    setDispatching(true);
    try {
      const created = await blindLabelApi.dispatch(claimId, assigneeId.trim(), payloadText);
      message.success(`盲标任务已派发：${created.taskNo}`);
      setPayloadText('');
      setDispatchClaimId('');
      setSelected(created);
      await refreshList(assigneeId);
    } catch (e) {
      message.error(String((e as { message?: string })?.message ?? e ?? '派发失败'));
    } finally {
      setDispatching(false);
    }
  };

  const doSubmit = async () => {
    if (!selected) return;
    const err = scanLabelResult(resultText);
    if (err) {
      message.error(err);
      return;
    }
    setActing(true);
    try {
      const updated = await blindLabelApi.submit(selected.id, resultText);
      message.success('盲标结论已提交（一次性，不可改写）');
      setSelected(updated);
      await refreshList(assigneeId);
    } catch (e) {
      message.error(String((e as { message?: string })?.message ?? e ?? '提交失败'));
    } finally {
      setActing(false);
    }
  };

  const doUnblind = async () => {
    if (!selected) return;
    setActing(true);
    try {
      const updated = await blindLabelApi.unblind(selected.id);
      message.success('已解盲：AI 建议快照与盲标结论配对完成');
      setSelected(updated);
      await refreshList(assigneeId);
    } catch (e) {
      message.error(String((e as { message?: string })?.message ?? e ?? '解盲失败'));
    } finally {
      setActing(false);
    }
  };

  const status = selected ? statusMeta(selected.status) : null;
  const agreement = selected ? agreementMeta(selected.agreement) : null;

  return (
    <div>
      <PageHeader
        title="盲标工作台"
        description="独立盲标：标注人只看材料引用；结论一次性提交后解盲，AI 建议快照才落库配对。"
      />
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="解盲前视图不含 AI 侧任何字段"
        description="派发载荷过 AI 痕迹禁区扫描（aiSuggestion/confidence/ruleHits 等键命中即拒绝）；这是防反馈环的金标准闸门——AI_ABSENT 的样本只作人工判定参考，不进模型评测。"
      />

      <Card
        title="我的盲标任务"
        extra={(
          <Space>
            <Input
              style={{ width: 180 }}
              placeholder="标注人 ID"
              value={assigneeId}
              onChange={(e) => persistAssignee(e.target.value)}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void refreshList(assigneeId)}>刷新</Button>
          </Space>
        )}
        style={{ marginBottom: 16 }}
      >
        {listError ? <Alert type="warning" showIcon message={listError} style={{ marginBottom: 12 }} /> : null}
        <Table<BlindLabelTaskView>
          rowKey="id"
          size="small"
          loading={listLoading}
          dataSource={tasks}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条（最近任务上限 50）` }}
          onRow={(record) => ({ onClick: () => { setResultText(''); void refreshSelected(record.id); }, style: { cursor: 'pointer' } })}
          columns={[
            { title: '任务号', dataIndex: 'taskNo', width: 220 },
            { title: '案件 ID', dataIndex: 'claimId', width: 120 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 140,
              render: (v: string) => <StatusTag meta={statusMeta(v)} />,
            },
            { title: '提交时间', dataIndex: 'submittedAt', width: 200, render: (v: string | null) => v ?? '—' },
            { title: '解盲时间', dataIndex: 'unblindedAt', width: 200, render: (v: string | null) => v ?? '—' },
          ]}
        />
      </Card>

      <Card title="派发新任务" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Row gutter={12}>
            <Col span={6}>
              <Input
                placeholder="案件 ID（纯数字）"
                value={dispatchClaimId}
                onChange={(e) => setDispatchClaimId(e.target.value)}
              />
            </Col>
            <Col span={18}>
              <Typography.Text type="secondary">
                载荷由派发方组装（材料引用清单），标注人据此独立判断；禁区扫描后端是权威，前端仅提前提示。
              </Typography.Text>
            </Col>
          </Row>
          <Input.TextArea
            rows={5}
            placeholder='{"evidenceRefs":["E-1","E-2"],"caseNote":"门诊小额"}'
            value={payloadText}
            onChange={(e) => setPayloadText(e.target.value)}
          />
          {payloadText.trim() ? (
            payloadScan.ok
              ? <Alert type="success" message="载荷预检通过（材料引用，无 AI 痕迹键）" showIcon />
              : <Alert type="error" showIcon message="载荷预检未通过" description={payloadScan.structureError} />
          ) : null}
          <Button type="primary" loading={dispatching} disabled={!payloadScan.ok} onClick={() => void doDispatch()}>
            派发盲标任务
          </Button>
        </Space>
      </Card>

      <Card title="任务详情">
        {!selected ? (
          <Typography.Text type="secondary">在上方任务列表点击一行查看详情。</Typography.Text>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Descriptions size="small" column={3} bordered>
              <Descriptions.Item label="任务号">{selected.taskNo}</Descriptions.Item>
              <Descriptions.Item label="案件 ID">{selected.claimId}</Descriptions.Item>
              <Descriptions.Item label="标注人">{selected.assigneeId || '—'}</Descriptions.Item>
              <Descriptions.Item label="状态">{status ? <StatusTag meta={status} /> : '—'}</Descriptions.Item>
              <Descriptions.Item label="载荷哈希">{selected.payloadHash || '—'}</Descriptions.Item>
              <Descriptions.Item label="配对结论">{agreement ? <StatusTag meta={agreement} /> : '—'}</Descriptions.Item>
            </Descriptions>

            {selected.status === 'PENDING' ? (
              <>
                <Alert
                  type="warning"
                  showIcon
                  message="一次性提交：结论提交后不可改写，提交前请核对材料引用。"
                />
                <Input.TextArea
                  rows={5}
                  placeholder='{"liability":"全责","payable":true,"amount":1200}'
                  value={resultText}
                  onChange={(e) => setResultText(e.target.value)}
                />
                <Button type="primary" loading={acting} onClick={() => void doSubmit()}>提交盲标结论</Button>
              </>
            ) : null}

            {selected.status === 'LABELED' ? (
              <Alert
                type="info"
                showIcon
                message="盲标结论已提交，等待解盲。"
                description="解盲会从决策轨迹读取 AI 建议快照落库并发布解盲事件——此操作不可逆，且只应由解盲岗执行。"
                action={(
                  <Popconfirm
                    title="确认解盲？"
                    description="AI 建议快照将落库配对，操作不可逆。"
                    okText="解盲"
                    cancelText="取消"
                    onConfirm={() => void doUnblind()}
                  >
                    <Button danger loading={acting}>解盲配对</Button>
                  </Popconfirm>
                )}
              />
            ) : null}

            {selected.status === 'UNBLINDED' ? (
              <Row gutter={12}>
                <Col span={12}>
                  <JsonBlock
                    title="盲标结论（人工独立判定）"
                    raw={selected.labelResultJson}
                    empty="后端未返回盲标结论。"
                  />
                </Col>
                <Col span={12}>
                  <JsonBlock
                    title="AI 建议快照（解盲时才落库）"
                    raw={selected.aiSuggestionJson}
                    empty={selected.aiParticipated
                      ? 'AI 参与但快照为空——后端返回异常，不得编造内容。'
                      : 'AI_ABSENT：AI 未参与本案决策（或置信度非模型来源），无快照，不进模型评测。'}
                  />
                </Col>
              </Row>
            ) : null}
          </Space>
        )}
      </Card>
    </div>
  );
}
