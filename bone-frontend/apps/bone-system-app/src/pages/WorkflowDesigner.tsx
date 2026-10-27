import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Layout,
  Card,
  Button,
  Space,
  Input,
  Select,
  InputNumber,
  Tag,
  Drawer,
  Empty,
  Alert,
  Typography,
  Popconfirm,
  Tooltip,
  message,
  Switch,
  Divider,
  Badge,
} from 'antd';
import {
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
  EyeOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  BranchesOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import {
  compile,
  type FlowDoc,
  type Step,
  type ApproveStep,
  type CcStep,
  type ConditionStep,
  type Branch,
  type AssignmentRule,
  type AssignmentStrategy,
  type ApprovalMode,
  type LayoutPoint,
  type Violation,
  PARAM_KEY_BY_STRATEGY,
  groupViolationsByNode,
  hintOf,
  isPublishable,
} from '@bone/workflow-dsl';
import { workflowDesignerApi, docFromDefinitionJson, type WorkflowPublished } from '@/services/workflowApi';

const { Sider, Content } = Layout;
const { Text, Title } = Typography;

const NODE_W = 200;
const NODE_H = 64;
const ROW_GAP = 110;
const COL_GAP = 240;

let codeSeq = 0;
const genCode = (prefix: string) => `${prefix}_${(++codeSeq).toString(36)}_${Date.now().toString(36).slice(-4)}`;

/** 从文档树计算默认画布坐标（未被拖拽覆盖时使用）。 */
function computeDefaultLayout(doc: FlowDoc): Record<string, LayoutPoint> {
  const layout: Record<string, LayoutPoint> = { START: { x: 0, y: 0 } };
  let y = ROW_GAP;

  const walk = (steps: Step[], depth: number) => {
    for (const step of steps) {
      layout[step.code] = { x: depth * COL_GAP, y };
      y += ROW_GAP;
      if (step.kind === 'condition') {
        for (const branch of step.branches) walk(branch.body, depth + 1);
        walk(step.defaultBranch.body, depth + 1);
      }
    }
  };
  walk(doc.steps, 0);
  layout.END = { x: 0, y };
  return layout;
}

const blankDoc = (key: string, name: string): FlowDoc => ({ key, name, steps: [] });

const emptyApprove = (code: string): ApproveStep => ({
  kind: 'approve',
  code,
  label: '审批节点',
  mode: 'SINGLE',
  rules: [],
});
const emptyCc = (code: string): CcStep => ({ kind: 'cc', code, label: '抄送节点', rules: [] });
const emptyCondition = (code: string): ConditionStep => ({
  kind: 'condition',
  code,
  label: '条件分支',
  branches: [{ id: genCode('b'), label: '分支 1', when: null, body: [] }],
  defaultBranch: { id: genCode('b'), label: '默认', when: null, body: [] },
});

/** 不可变地更新文档树里某个 code 对应的 step。 */
function updateStep(doc: FlowDoc, code: string, updater: (s: Step) => Step): FlowDoc {
  const map = (steps: Step[]): Step[] =>
    steps.map((s) => {
      if (s.code === code) return updater(s);
      if (s.kind === 'condition') {
        return {
          ...s,
          branches: s.branches.map((b) => ({ ...b, body: map(b.body) })),
          defaultBranch: { ...s.defaultBranch, body: map(s.defaultBranch.body) },
        };
      }
      return s;
    });
  return { ...doc, steps: map(doc.steps) };
}

function removeStep(doc: FlowDoc, code: string): FlowDoc {
  const filter = (steps: Step[]): Step[] =>
    steps
      .filter((s) => s.code !== code)
      .map((s) =>
        s.kind === 'condition'
          ? {
              ...s,
              branches: s.branches.map((b) => ({ ...b, body: filter(b.body) })),
              defaultBranch: { ...s.defaultBranch, body: filter(s.defaultBranch.body) },
            }
          : s,
      );
  return { ...doc, steps: filter(doc.steps) };
}

/** 把 step 追加到主链或某条件节点的某分支 body。 */
function appendStep(doc: FlowDoc, step: Step, targetBranchId: string | null): FlowDoc {
  if (targetBranchId == null) return { ...doc, steps: [...doc.steps, step] };
  const map = (steps: Step[]): Step[] =>
    steps.map((s) => {
      if (s.kind !== 'condition') return s;
      const branches = s.branches.map((b) =>
        b.id === targetBranchId ? { ...b, body: [...b.body, step] } : b,
      );
      const defaultBranch =
        s.defaultBranch.id === targetBranchId
          ? { ...s.defaultBranch, body: [...s.defaultBranch.body, step] }
          : s.defaultBranch;
      return { ...s, branches, defaultBranch };
    });
  return { ...doc, steps: map(doc.steps) };
}

/** 试运行报告的前端投影（与后端 {@code SimulationReport} 镜像的界面所需字段）。 */
interface SimReport {
  path?: string[];
  conditionTraces?: unknown[];
  warnings?: string[];
  error?: string | null;
}

const WorkflowDesigner: React.FC = () => {
  const [defKey, setDefKey] = useState('');
  const [defName, setDefName] = useState('');
  const [doc, setDoc] = useState<FlowDoc | null>(null);
  const [layout, setLayout] = useState<Record<string, LayoutPoint>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [revision, setRevision] = useState<number | null>(null);
  const [draftExists, setDraftExists] = useState(false);
  const [lossy, setLossy] = useState(false);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [simReport, setSimReport] = useState<SimReport | null>(null);
  const [defList, setDefList] = useState<WorkflowPublished[]>([]);
  const [versions, setVersions] = useState<WorkflowPublished[]>([]);
  const [versionDrawer, setVersionDrawer] = useState(false);
  const [, setLoading] = useState(false);
  const [drag, setDrag] = useState<{ code: string; dx: number; dy: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const compiled = useMemo(() => {
    if (!doc) return null;
    try {
      return compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    } catch {
      return null;
    }
  }, [doc, layout]);

  const violationByNode = useMemo(() => groupViolationsByNode(violations), [violations]);

  const refreshList = useCallback(async () => {
    const res = await workflowDesignerApi.listLatest();
    if (res.code === 200) setDefList(res.data ?? []);
  }, []);

  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  const openKey = useCallback(
    async (key: string, name?: string) => {
      setDefKey(key);
      if (name) setDefName(name);
      setSelected(null);
      setViolations([]);
      setSimReport(null);
      setLoading(true);
      try {
        const draft = await workflowDesignerApi.findDraft(key);
        if (draft.code === 200 && draft.data) {
          setDraftExists(true);
          setRevision(draft.data.revision);
          const parsed = docFromDefinitionJson(draft.data.definitionJson);
          const def = JSON.parse(draft.data.definitionJson) as { ui?: { layout?: Record<string, LayoutPoint> } };
          if (parsed) {
            setDoc(parsed);
            setLayout(def.ui?.layout ?? computeDefaultLayout(parsed));
            setLossy(false);
          } else {
            setDoc(blankDoc(key, draft.data.defName));
            setLayout(computeDefaultLayout(blankDoc(key, draft.data.defName)));
            setLossy(true);
          }
          return;
        }
        // 无草稿：尝试取已发布最新版还原（flowDoc 在 ui 段）
        const vers = await workflowDesignerApi.listVersions(key);
        if (vers.code === 200 && vers.data && vers.data.length > 0) {
          const latest = vers.data[0];
          const parsed = docFromDefinitionJson(latest.definitionJson);
          const def = JSON.parse(latest.definitionJson) as { ui?: { layout?: Record<string, LayoutPoint> } };
          if (parsed) {
            setDoc(parsed);
            setLayout(def.ui?.layout ?? computeDefaultLayout(parsed));
            setLossy(false);
          } else {
            setDoc(blankDoc(key, latest.defName));
            setLayout(computeDefaultLayout(blankDoc(key, latest.defName)));
            setLossy(true);
          }
          setDraftExists(false);
          setRevision(null);
          return;
        }
        const fresh = blankDoc(key, name ?? key);
        setDoc(fresh);
        setLayout(computeDefaultLayout(fresh));
        setDraftExists(false);
        setRevision(null);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const onValidate = useCallback(async () => {
    if (!compiled) return;
    const res = await workflowDesignerApi.validate(JSON.stringify(compiled));
    if (res.code === 200) {
      setViolations(res.data?.violations ?? []);
      if (isPublishable(res.data?.violations)) message.success('校验通过，可发布');
      else message.warning(`存在 ${res.data?.violations.length} 处问题`);
    }
  }, [compiled]);

  const onSaveDraft = useCallback(async () => {
    if (!doc || !defKey) {
      message.error('请先指定流程 key');
      return;
    }
    if (lossy) {
      message.error('当前为旧数据有损还原，请补全节点后再保存');
      return;
    }
    const def = compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    const res = await workflowDesignerApi.saveDraft(defKey, JSON.stringify(def), revision);
    if (res.code === 200 && res.data) {
      setRevision(res.data.draft.revision);
      setDraftExists(true);
      setViolations(res.data.violations);
      message.success(`草稿已保存（revision=${res.data.draft.revision}）`);
    } else {
      message.error((res as { message?: string }).message ?? '保存失败');
    }
  }, [doc, defKey, lossy, layout, revision]);

  const onPublish = useCallback(async () => {
    if (!doc || !defKey) return;
    const def = compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    const vRes = await workflowDesignerApi.validate(JSON.stringify(def));
    if (vRes.code !== 200 || !isPublishable(vRes.data?.violations)) {
      setViolations(vRes.data?.violations ?? []);
      message.error('存在违规，拒绝发布（fail-closed）');
      return;
    }
    setViolations([]);
    const res = await workflowDesignerApi.publish(defKey, revision);
    if (res.code === 200) {
      message.success(`已发布 v${res.data?.version}`);
      setDraftExists(false);
      setRevision(null);
      void refreshList();
    } else {
      message.error((res as { message?: string }).message ?? '发布失败');
    }
  }, [doc, defKey, layout, revision, refreshList]);

  const onSimulate = useCallback(async () => {
    if (!compiled) return;
    const res = await workflowDesignerApi.simulate({
      definitionJson: JSON.stringify(compiled),
      sampleData: {},
      initiatorId: 'sim-user',
    });
    if (res.code === 200) setSimReport(res.data ?? null);
    else message.error((res as { message?: string }).message ?? '试运行失败');
  }, [compiled]);

  const onAdd = (kind: 'approve' | 'cc' | 'condition') => {
    if (!doc) return;
    const code = genCode(kind);
    const step: Step =
      kind === 'approve' ? emptyApprove(code) : kind === 'cc' ? emptyCc(code) : emptyCondition(code);
    const targetBranch = selected ? findBranchId(doc, selected) : null;
    setDoc(appendStep(doc, step, targetBranch));
    setSelected(code);
  };

  const onDelete = (code: string) => {
    if (!doc) return;
    setDoc(removeStep(doc, code));
    setSelected(null);
  };

  const onMouseDown = (e: React.MouseEvent, code: string) => {
    e.preventDefault();
    const start = layout[code] ?? { x: 0, y: 0 };
    setDrag({ code, dx: e.clientX - start.x, dy: e.clientY - start.y });
  };
  useEffect(() => {
    if (!drag) return;
    const move = (e: MouseEvent) => {
      setLayout((prev) => ({ ...prev, [drag.code]: { x: e.clientX - drag.dx, y: e.clientY - drag.dy } }));
    };
    const up = () => setDrag(null);
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
    return () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
  }, [drag]);

  const selectedStep = doc && selected ? findStep(doc, selected) : null;

  return (
    <Layout style={{ height: '100%' }}>
      <Sider width={300} theme="light" style={{ borderRight: '1px solid #f0f0f0', overflow: 'auto' }}>
        <div style={{ padding: 16 }}>
          <Title level={5}>工作流设计器</Title>
          <Input
            placeholder="流程 key（如 expense_designer）"
            value={defKey}
            onChange={(e) => setDefKey(e.target.value)}
            addonBefore="key"
            style={{ marginBottom: 8 }}
          />
          <Input
            placeholder="流程名称"
            value={defName}
            onChange={(e) => setDefName(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          <Button block onClick={() => defKey && openKey(defKey, defName)}>
            打开 / 新建
          </Button>
          <Divider>已有流程</Divider>
          <Space direction="vertical" style={{ width: '100%' }}>
            {defList.map((d) => (
              <Button key={d.defKey} block type={d.defKey === defKey ? 'primary' : 'default'} onClick={() => openKey(d.defKey, d.defName)}>
                {d.defName} <Tag color={d.enabled ? 'green' : 'default'}>v{d.version}</Tag>
              </Button>
            ))}
            {defList.length === 0 && <Empty description="暂无流程" />}
          </Space>
        </div>
      </Sider>
      <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ padding: 12, borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space wrap>
            <Button icon={<SaveOutlined />} onClick={onSaveDraft} disabled={!doc}>
              保存草稿
            </Button>
            <Button icon={<SendOutlined />} type="primary" onClick={onPublish} disabled={!doc}>
              发布
            </Button>
            <Button icon={<EyeOutlined />} onClick={onValidate} disabled={!doc}>
              校验
            </Button>
            <Button icon={<ExperimentOutlined />} onClick={onSimulate} disabled={!doc}>
              试运行
            </Button>
            <Popconfirm title="确认丢弃草稿？" onConfirm={() => defKey && workflowDesignerApi.discardDraft(defKey).then(() => { setDraftExists(false); setRevision(null); })}>
              <Button icon={<DeleteOutlined />} danger disabled={!draftExists}>
                丢弃草稿
              </Button>
            </Popconfirm>
            <Button icon={<AuditOutlined />} onClick={() => { if (defKey) workflowDesignerApi.listVersions(defKey).then((r) => { if (r.code === 200) setVersions(r.data ?? []); }); setVersionDrawer(true); }}>
              版本
            </Button>
            {draftExists && <Tag color="orange">草稿 rev {revision}</Tag>}
            {lossy && <Tag color="red">有损还原</Tag>}
          </Space>
          <Space>
            <Tooltip title="向主链或选中条件分支添加节点">
              <Button icon={<PlusOutlined />} onClick={() => onAdd('approve')}>
                审批
              </Button>
            </Tooltip>
            <Button icon={<PlusOutlined />} onClick={() => onAdd('cc')}>
              抄送
            </Button>
            <Button icon={<BranchesOutlined />} onClick={() => onAdd('condition')}>
              条件
            </Button>
          </Space>
        </div>

        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          {/* 画布 */}
          <div
            ref={containerRef}
            style={{ flex: 1, position: 'relative', overflow: 'auto', background: '#fafafa' }}
          >
            {!doc && <Empty style={{ marginTop: 80 }} description="指定流程 key 后开始设计" />}
            {doc && compiled && (
              <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
                {compiled.edges.map((e, i) => {
                  const a = layout[e.from] ?? computeDefaultLayout(doc)[e.from];
                  const b = layout[e.to] ?? computeDefaultLayout(doc)[e.to];
                  if (!a || !b) return null;
                  return (
                    <line
                      key={i}
                      x1={a.x + NODE_W / 2}
                      y1={a.y + NODE_H}
                      x2={b.x + NODE_W / 2}
                      y2={b.y}
                      stroke="#bbb"
                      strokeWidth={1.5}
                    />
                  );
                })}
              </svg>
            )}
            {doc && compiled && compiled.nodes.map((n) => {
              const pos = layout[n.code] ?? computeDefaultLayout(doc)[n.code] ?? { x: 0, y: 0 };
              const vs = violationByNode.get(n.code) ?? [];
              const color =
                n.type === 'START' ? '#52c41a' : n.type === 'END' ? '#999' : n.type === 'CONDITION' ? '#722ed1' : n.type === 'CC' ? '#13c2c2' : '#1677ff';
              return (
                <div
                  key={n.code}
                  onMouseDown={(e) => onMouseDown(e, n.code)}
                  onClick={() => setSelected(n.code)}
                  style={{
                    position: 'absolute',
                    left: pos.x,
                    top: pos.y,
                    width: NODE_W,
                    height: NODE_H,
                    cursor: 'grab',
                    border: `2px solid ${selected === n.code ? '#fa8c16' : color}`,
                    borderRadius: 8,
                    background: '#fff',
                    boxShadow: selected === n.code ? '0 0 0 3px rgba(250,140,22,.2)' : '0 1px 4px rgba(0,0,0,.1)',
                    padding: '6px 10px',
                    userSelect: 'none',
                  }}
                >
                  <div style={{ fontSize: 12, color }}>{n.type}</div>
                  <div style={{ fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{n.label}</div>
                  {vs.length > 0 && <Badge count={vs.length} size="small" style={{ position: 'absolute', top: -8, right: -8 }} />}
                </div>
              );
            })}
          </div>

          {/* 属性面板 */}
          <div style={{ width: 320, borderLeft: '1px solid #f0f0f0', padding: 16, overflow: 'auto' }}>
            {!selectedStep && <Empty description="选中节点以编辑属性" />}
            {selectedStep && (
              <StepPropertyPanel
                step={selectedStep}
                onChange={(next) => setDoc((d) => (d ? updateStep(d, selectedStep.code, () => next) : d))}
                onDelete={() => onDelete(selectedStep.code)}
                violations={violationByNode.get(selectedStep.code) ?? []}
              />
            )}
            {violations.length > 0 && (
              <>
                <Divider>校验问题</Divider>
                <Space direction="vertical" style={{ width: '100%' }}>
                  {violations.map((v, i) => (
                    <Alert
                      key={i}
                      type="error"
                      showIcon
                      message={`${v.code}${v.locator ? ` @${v.locator}` : ''}`}
                      description={hintOf(v).suggestion ?? v.message}
                      onClick={() => v.locator && setSelected(v.locator)}
                      style={{ cursor: v.locator ? 'pointer' : 'default' }}
                    />
                  ))}
                </Space>
              </>
            )}
            {simReport && (
              <>
                <Divider>试运行</Divider>
                <SimReportView report={simReport} />
              </>
            )}
          </div>
        </div>
      </Content>

      <Drawer title={`版本管理 · ${defKey}`} open={versionDrawer} onClose={() => setVersionDrawer(false)} width={420}>
        <Space direction="vertical" style={{ width: '100%' }}>
          {versions.map((v) => (
            <Card key={v.version} size="small">
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>
                  v{v.version} <Tag color={v.enabled ? 'green' : 'default'}>{v.enabled ? '已上线' : '已下线'}</Tag>
                </span>
                <Space>
                  <Button size="small" onClick={() => { const p = docFromDefinitionJson(v.definitionJson); if (p) { setDoc(p); setLayout(computeDefaultLayout(p)); setSelected(null); } setVersionDrawer(false); }}>载入</Button>
                  <Switch
                    checked={v.enabled}
                    size="small"
                    onChange={(on) => defKey && workflowDesignerApi.setEnabled(defKey, v.version, on).then(() => workflowDesignerApi.listVersions(defKey).then((r) => r.code === 200 && setVersions(r.data ?? [])))}
                  />
                </Space>
              </Space>
            </Card>
          ))}
          {versions.length === 0 && <Empty description="暂无版本" />}
        </Space>
      </Drawer>
    </Layout>
  );
};

/** 在文档树里定位 code 所在的 step。 */
function findStep(doc: FlowDoc, code: string): Step | null {
  const walk = (steps: Step[]): Step | null => {
    for (const s of steps) {
      if (s.code === code) return s;
      if (s.kind === 'condition') {
        for (const b of s.branches) {
          const r = walk(b.body);
          if (r) return r;
        }
        const d = walk(s.defaultBranch.body);
        if (d) return d;
      }
    }
    return null;
  };
  return walk(doc.steps);
}

/** 若选中节点在某个条件分支体内，返回该分支 id（用于向其追加子节点）。 */
function findBranchId(doc: FlowDoc, code: string): string | null {
  const walk = (steps: Step[]): string | null => {
    for (const s of steps) {
      if (s.kind !== 'condition') continue;
      for (const b of s.branches) {
        if (b.body.some((x) => x.code === code)) return b.id;
        const r = walk(b.body);
        if (r) return r;
      }
      if (s.defaultBranch.body.some((x) => x.code === code)) return s.defaultBranch.id;
      const d = walk(s.defaultBranch.body);
      if (d) return d;
    }
    return null;
  };
  return walk(doc.steps);
}

const STEP_COLOR: Record<string, string> = {
  approve: '#1677ff',
  cc: '#13c2c2',
  condition: '#722ed1',
};

const StepPropertyPanel: React.FC<{
  step: Step;
  onChange: (next: Step) => void;
  onDelete: () => void;
  violations: Violation[];
}> = ({ step, onChange, onDelete, violations }) => {
  return (
    <div>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Tag color={STEP_COLOR[step.kind]}>{step.kind}</Tag>
        <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete}>
          删除
        </Button>
      </Space>
      <Divider style={{ margin: '12px 0' }} />
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">节点编码</Text>
        <div>{step.code}</div>
      </div>
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">名称</Text>
        <Input value={step.label} onChange={(e) => onChange({ ...step, label: e.target.value })} />
      </div>

      {step.kind === 'approve' && (
        <>
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary">完成方式</Text>
            <Select
              style={{ width: '100%' }}
              value={step.mode}
              onChange={(v: ApprovalMode) => onChange({ ...step, mode: v })}
              options={[
                { value: 'SINGLE', label: '单签' },
                { value: 'ALL', label: '会签（全部）' },
                { value: 'ANY', label: '或签（任一）' },
                { value: 'RATIO', label: '比例会签' },
              ]}
            />
          </div>
          {step.mode === 'RATIO' && (
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">至少通过人数</Text>
              <InputNumber min={1} style={{ width: '100%' }} value={step.quorum ?? 1} onChange={(v) => onChange({ ...step, quorum: v ?? 1 })} />
            </div>
          )}
          <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />
        </>
      )}

      {step.kind === 'cc' && <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />}

      {step.kind === 'condition' && (
        <BranchEditor branches={step.branches} defaultBranch={step.defaultBranch} onChange={(branches, defaultBranch) => onChange({ ...step, branches, defaultBranch })} />
      )}

      {violations.length > 0 && (
        <Alert
          style={{ marginTop: 8 }}
          type="error"
          showIcon
          message={violations.map((v) => `${v.code}`).join('、')}
          description={violations.map((v) => hintOf(v).suggestion ?? v.message).join('；')}
        />
      )}
    </div>
  );
};

const RuleEditor: React.FC<{ rules: AssignmentRule[]; onChange: (rules: AssignmentRule[]) => void }> = ({ rules, onChange }) => {
  const update = (i: number, rule: AssignmentRule) => onChange(rules.map((r, idx) => (idx === i ? rule : r)));
  return (
    <div>
      <Text type="secondary">审批人 / 抄送人指派</Text>
      {rules.map((rule, i) => (
        <Card key={i} size="small" style={{ margin: '8px 0' }}>
          <Select
            style={{ width: '100%', marginBottom: 6 }}
            value={rule.strategy}
            onChange={(v: AssignmentStrategy) => update(i, { strategy: v, params: {} })}
            options={[
              { value: 'LINE_MANAGER', label: '直属主管' },
              { value: 'DEPT_HEAD', label: '部门负责人' },
              { value: 'LEVEL_UP', label: '向上 N 级' },
              { value: 'ROLE', label: '角色' },
              { value: 'USER', label: '指定人' },
              { value: 'FIELD', label: '表单字段' },
              { value: 'INITIATOR_SELECT', label: '发起人自选' },
            ]}
          />
          {PARAM_KEY_BY_STRATEGY[rule.strategy] && (
            <Input
              placeholder={PARAM_KEY_BY_STRATEGY[rule.strategy] as string}
              value={(rule.params[PARAM_KEY_BY_STRATEGY[rule.strategy] as string] as string) ?? ''}
              onChange={(e) => update(i, { ...rule, params: { ...rule.params, [PARAM_KEY_BY_STRATEGY[rule.strategy] as string]: e.target.value } })}
            />
          )}
          <Button size="small" danger style={{ marginTop: 6 }} onClick={() => onChange(rules.filter((_, idx) => idx !== i))}>
            移除
          </Button>
        </Card>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange([...rules, { strategy: 'LINE_MANAGER', params: {} }])}>
        添加指派
      </Button>
    </div>
  );
};

const OPERATORS = [
  { value: 'eq', label: '等于' },
  { value: 'neq', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'in', label: '属于' },
  { value: 'between', label: '介于' },
];

const BranchEditor: React.FC<{
  branches: Branch[];
  defaultBranch: Branch;
  onChange: (branches: Branch[], defaultBranch: Branch) => void;
}> = ({ branches, defaultBranch, onChange }) => {
  const renderBranch = (b: Branch, isDefault: boolean) => (
    <Card key={b.id} size="small" title={isDefault ? `默认分支：${b.label}` : `分支：${b.label}`} style={{ margin: '8px 0' }}>
      <Input
        placeholder="分支名称"
        value={b.label}
        onChange={(e) => patch(b.id, { ...b, label: e.target.value }, isDefault)}
        style={{ marginBottom: 6 }}
      />
      {!isDefault && (
        <div style={{ display: 'flex', gap: 4, marginBottom: 6 }}>
          <Input placeholder="字段 fieldCode" value={(b.when as { fieldCode?: string } | null)?.fieldCode ?? ''} onChange={(e) => patch(b.id, { ...b, when: { fieldCode: e.target.value, operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq', value: ((b.when as { value?: unknown } | null)?.value as never) ?? '' } }, isDefault)} />
          <Select
            style={{ width: 90 }}
            value={((b.when as { operator?: string } | null)?.operator as string) ?? 'eq'}
            onChange={(op) => patch(b.id, { ...b, when: { fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '', operator: op as never, value: ((b.when as { value?: unknown } | null)?.value as never) ?? '' } }, isDefault)}
            options={OPERATORS}
          />
          <Input placeholder="值" value={((b.when as { value?: unknown } | null)?.value as string) ?? ''} onChange={(e) => patch(b.id, { ...b, when: { fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '', operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq', value: e.target.value as never } }, isDefault)} />
        </div>
      )}
      <Text type="secondary">分支内步骤：{b.body.length} 个</Text>
    </Card>
  );
  const patch = (id: string, next: Branch, isDefault: boolean) => {
    if (isDefault) onChange(branches, next);
    else onChange(branches.map((x) => (x.id === id ? next : x)), defaultBranch);
  };
  return (
    <div>
      {branches.map((b) => renderBranch(b, false))}
      {renderBranch(defaultBranch, true)}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange([...branches, { id: genCode('b'), label: `分支 ${branches.length + 1}`, when: null, body: [] }], defaultBranch)}>
        添加分支
      </Button>
    </div>
  );
};

const SimReportView: React.FC<{ report: { path?: string[]; conditionTraces?: unknown[]; warnings?: string[]; error?: string | null } }> = ({ report }) => {
  if (report.error) return <Alert type="error" message="试运行失败" description={report.error} />;
  return (
    <div>
      <div><Text type="secondary">路径：</Text> {report.path?.join(' → ')}</div>
      {report.warnings?.map((w, i) => (
        <Alert key={i} type="warning" showIcon message={w} style={{ margin: '4px 0' }} />
      ))}
      {report.conditionTraces && report.conditionTraces.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Text type="secondary">分支判定：</Text>
          {report.conditionTraces.map((t, i) => (
            <div key={i}>{(t as { label?: string }).label}：{(t as { branches?: Array<{ label: string; matched: boolean | null }> }).branches?.map((b) => `${b.label}=${b.matched}`).join('，')}</div>
          ))}
        </div>
      )}
    </div>
  );
};

export default WorkflowDesigner;
