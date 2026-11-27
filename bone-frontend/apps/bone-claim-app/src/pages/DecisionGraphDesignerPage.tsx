import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntApp,
  Card,
  Checkbox,
  Descriptions,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined, RollbackOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { PERMISSIONS } from '../constants/permissions';
import { ERROR_CODES } from '../constants/errorCodes';
import { claimConfigApi, type DecisionFlowConfigView } from '../services/claimConfigApi';
import {
  DECISION_GRAPH_NODES,
  SKIPPABLE_NODES,
  validateSkipNodes,
  type DecisionGraphNode,
} from '../services/decisionGraph';

/**
 * 决策图编排（运营配置面）。
 *
 * <p><b>这是 hidden 路由（/decision-graph），不占 IAM 菜单</b>：入口从保单管理「决策图编排」按钮下钻，
 * 直接输 URL 也可达。单导航探针（claim-single-nav-probe）对 hidden 路由豁免「功能不可达」断言，
 * 因此不需要新增菜单迁移脚本。
 *
 * <p><b>边界</b>：页面只编辑后端既有维度 `skipNodes`，且 UI 只暴露白名单节点 AiAssess；节点顺序
 * 以只读列表呈现，没有重排/注入/删除控件。服务端 `ClaimFlowConfigApplicationService.save()` 是唯一门禁，
 * 本页的 `validateSkipNodes` 仅用于即时反馈，失败时原样展示服务端错误码与消息，且不清空现网配置。
 *
 * <p><b>回滚语义（如实说明）</b>：「恢复默认」写入 `skipNodes=[]`，引擎回到 canonical 图；
 * 配置版本递增并留痕。当前未接 feature_toggle 百分比灰度，页面不宣称已接灰度。
 */
export function DecisionGraphDesignerPage(): JSX.Element {
  const { message } = AntApp.useApp();
  const [view, setView] = useState<DecisionFlowConfigView | null>(null);
  const [draft, setDraft] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [localErrors, setLocalErrors] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await claimConfigApi.getFlowConfig();
      const next: DecisionFlowConfigView = res.data ?? {
        flowKey: 'decision-graph',
        configVersion: 0,
        skipNodes: [],
        version: null,
      };
      setView(next);
      setDraft([...(next.skipNodes ?? [])]);
      setLocalErrors([]);
    } catch (err) {
      // 加载失败只提示，不把「没读到」伪装成「现网是空配置」——view 保持 null，保存入口禁用。
      message.error(resolveErrorMessage(err, '决策图配置加载失败'));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const dirty = useMemo(() => {
    if (!view) return false;
    const a = [...draft].sort();
    const b = [...(view.skipNodes ?? [])].sort();
    return a.length !== b.length || a.some((n, i) => n !== b[i]);
  }, [draft, view]);

  /** 保存 / 恢复默认共用一条提交链路；回滚只是「提交空集」，不是另一套实现。 */
  const submit = async (skipNodes: string[], successText: string) => {
    const errors = validateSkipNodes(skipNodes);
    setLocalErrors(errors);
    if (errors.length > 0) {
      message.error('本地校验未通过，配置未提交');
      return;
    }
    setSaving(true);
    try {
      const res = await claimConfigApi.updateFlowConfig(skipNodes);
      if (res.data) {
        setView(res.data);
        setDraft([...(res.data.skipNodes ?? [])]);
      }
      message.success(successText);
    } catch (err) {
      const code = resolveErrorCode(err);
      const detail = resolveErrorMessage(err, '决策图配置保存失败');
      message.error(code ? `${code}：${detail}` : detail);
      // 关键：失败时保留 draft 与 view（现网配置）原样，绝不清空。
      if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
        message.warning('配置已被他人修改，请刷新后再保存');
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '顺序',
        key: 'seq',
        width: 64,
        render: (_value: unknown, _record: DecisionGraphNode, index: number) => index + 1,
      },
      {
        title: '节点',
        key: 'name',
        render: (_value: unknown, record: DecisionGraphNode) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{record.label}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {record.name}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: '类型',
        key: 'nodeType',
        width: 100,
        render: (_value: unknown, record: DecisionGraphNode) => <Tag>{record.nodeType}</Tag>,
      },
      {
        title: '可配置性',
        key: 'skippable',
        render: (_value: unknown, record: DecisionGraphNode) =>
          record.skippable ? (
            <Tag color="blue">白名单：可跳过</Tag>
          ) : (
            <Tag color="default">必需节点，不可跳过</Tag>
          ),
      },
      {
        title: '当前配置',
        key: 'effective',
        render: (_value: unknown, record: DecisionGraphNode) =>
          draft.includes(record.name) ? (
            <Tag color="orange">将跳过（转人工）</Tag>
          ) : (
            <Tag color="green">执行</Tag>
          ),
      },
    ],
    [draft],
  );

  return (
    <div>
      <PageHeader
        title="决策图编排"
        description="仅可配置「允许跳过的节点」；节点顺序由执行引擎唯一真源决定，本页只读展示，不可重排或增删节点。"
      />

      <Spin spinning={loading}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="边界说明：节点顺序只读，仅 AiAssess 可跳过"
            description="顺序 EvidenceLoad → RuleCheck → AiAssess → Adjust → Arbitrate 写死在执行引擎 DecisionGraph.canonical()；Adjust（金额）与 Arbitrate（结论）绝不外放。跳过 AI 评估后结论一律转人工 MANUAL_REQUIRED，不会自动通过。"
          />

          <Card title="节点顺序（只读）" size="small">
            <Table<DecisionGraphNode>
              rowKey="name"
              size="small"
              pagination={false}
              dataSource={[...DECISION_GRAPH_NODES]}
              columns={columns}
            />
          </Card>

          <Card title="编辑允许跳过的节点" size="small">
            <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
              勾选表示「跳过该节点」。当前白名单只有 AiAssess（唯一 LLM 节点），其他节点既不在 UI
              中出现，也无法通过本页提交。
            </Typography.Paragraph>
            <Checkbox.Group
              value={draft.filter((n) => SKIPPABLE_NODES.includes(n))}
              onChange={(checked) => {
                const next = (checked as string[]).filter((n) => SKIPPABLE_NODES.includes(n));
                setDraft(next);
                setLocalErrors(validateSkipNodes(next));
              }}
              options={DECISION_GRAPH_NODES.filter((n) => n.skippable).map((n) => ({
                value: n.name,
                label: `${n.label}（${n.name}）— 跳过后转人工，不自动放行`,
              }))}
            />

            {localErrors.length > 0 ? (
              <Alert
                type="error"
                showIcon
                style={{ marginTop: 12 }}
                message="本地校验未通过（未提交服务端）"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {localErrors.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                }
              />
            ) : null}

            <Space style={{ marginTop: 16 }} wrap>
              <AuthButton
                code={PERMISSIONS.POLICY_WRITE}
                type="primary"
                loading={saving}
                disabled={!view || !dirty}
                onClick={() => void submit(draft, '决策图配置已保存')}
              >
                保存配置
              </AuthButton>
              <AuthButton
                code={PERMISSIONS.POLICY_READ}
                icon={<ReloadOutlined />}
                disabled={loading}
                onClick={() => void load()}
              >
                刷新现网配置
              </AuthButton>
            </Space>
          </Card>

          <Card title="秒级回滚" size="small">
            <Alert
              type="warning"
              showIcon
              message="回滚语义（如实说明）"
              description={
                <Space direction="vertical" size={4}>
                  <span>
                    「恢复默认」= 写入 skipNodes=[]，引擎按 canonical 图逐节点执行；配置版本递增并发布
                    ClaimFlowConfigChangedEvent 留痕（可按 flowKey 在审计日志检索）。
                  </span>
                  <span>
                    这是「恢复到 canonical 配置版本」，<strong>不是</strong>回退到某个历史配置快照；
                    当前<strong>未接入</strong> feature_toggle 百分比灰度，本页不支持也不宣称已接通按比例灰度。
                    回滚不触碰执行引擎、资金闸与赔付计算。
                  </span>
                </Space>
              }
            />
            <Popconfirm
              title="恢复默认配置？"
              description="将 skipNodes 清空，执行引擎按 canonical 图执行；版本递增并留痕。"
              okText="确认恢复"
              cancelText="取消"
              onConfirm={() => void submit([], '已恢复 canonical 默认配置')}
            >
              <AuthButton
                code={PERMISSIONS.POLICY_WRITE}
                icon={<RollbackOutlined />}
                loading={saving}
                disabled={!view}
                style={{ marginTop: 12 }}
              >
                恢复默认（skipNodes=[]）
              </AuthButton>
            </Popconfirm>
          </Card>

          <Card title="版本与留痕" size="small">
            <Descriptions size="small" bordered column={1}>
              <Descriptions.Item label="flowKey">{view?.flowKey ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="配置版本 configVersion">
                {view?.configVersion ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="乐观锁版本号 version">
                {view?.version ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="当前生效 skipNodes">
                {view?.skipNodes?.length ? view.skipNodes.join(', ') : '[]（canonical 图，无跳过）'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              每次保存或恢复默认都会递增 config_version 并发布变更事件（新建=CREATED / 覆写=OVERWRITTEN）；
              并发修改返回 SC_CLAIM_CONCURRENT_CONFLICT，刷新后再保存。
            </Typography.Paragraph>
          </Card>
        </Space>
      </Spin>
    </div>
  );
}
