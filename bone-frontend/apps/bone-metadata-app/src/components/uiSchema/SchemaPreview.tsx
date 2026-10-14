/**
 * 实时预览画布（高端范式）。
 *
 * 纪律：预览跑的是**运行时同一套引擎**（linkageState / runSubmit / resolveVisibleActions /
 * resolveVariant），不是另写渲染器。否则预览通过 ≠ 运行时通过，预览就成了误导性装饰。
 *
 * 相比 v1 的增量：
 * - 顶级设计令牌（uiSchemaTheme.css）：渐变/柔和阴影/分区标题强调条/统计卡/时间线/设备框/规则追踪。
 * - 身份模拟栏：角色/档位/渠道/设备 → 实时裁决变体（员工端 vs 核赔工作台），直接服务「智能理赔」场景联调。
 * - 设备框：桌面/平板/手机三档，呼应协议 devices 扩展性与移动端就绪。
 * - 规则追踪：哪些联动命中、哪些校验拦截，配置对错一目了然。
 * - 列表视图预览：columns / tabs / stats / search / dataScope 整屏还原。
 * - 预览不落库：动作按钮只提示「将触发 XX」，不真调 runtime。
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Empty, Input, InputNumber, Segmented, Select, Space, Switch, Tooltip, Typography, message,
} from 'antd';
import {
  CheckCircleOutlined, DeleteOutlined, PlusOutlined,
} from '@ant-design/icons';
import { parseEnumValues } from '@bone/shared-types';
import {
  evalCond, linkageState, resolveVariant, resolveVisibleActions, runSubmit,
  type BoneUiSchema, type Control, type FieldUiState, type UiContext, type VariantMatch,
} from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { dictOf, formOf } from './schemaOps';
import { SCENARIOS, applyScenario } from './scenarios';
import './uiSchemaTheme.css';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  fields: MetaField[];
  entityCode: string;
}

function defaultOf(code: string, fields: MetaField[]): unknown {
  const f = fields.find((x) => x.code === code);
  const t = (f?.type ?? '').toUpperCase();
  if (t === 'INT' || t === 'BIGINT' || t === 'DECIMAL') return undefined;
  if (t === 'BOOLEAN') return false;
  return '';
}

function optionsOf(code: string, fields: MetaField[], schema: BoneUiSchema) {
  const f = fields.find((x) => x.code === code);
  const raw = parseEnumValues(f?.enumValues);
  const ov = dictOf(schema, code);
  return raw
    .filter((o) => !(ov?.hidden ?? []).includes(o.code))
    .map((o) => ({ value: o.code, label: ov?.labelOverride?.[o.code] || o.label }));
}

function fieldMatch(m?: VariantMatch, ctx: UiContext = {}): boolean {
  if (!m) return true;
  if (m.roles && m.roles.length && !(m.roles.some((r) => (ctx.roles ?? []).includes(r)))) return false;
  if (m.tiers && m.tiers.length && (!ctx.tier || !m.tiers.includes(ctx.tier))) return false;
  if (m.channels && m.channels.length && (!ctx.channel || !m.channels.includes(ctx.channel))) return false;
  if (m.devices && m.devices.length && (!ctx.device || !m.devices.includes(ctx.device))) return false;
  return true;
}

/* ---------------- 字段控件 ---------------- */
function FieldControl({
  control, st, fields, schema, value, onChange,
}: {
  control: Control;
  st: FieldUiState;
  fields: MetaField[];
  schema: BoneUiSchema;
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const code = control.fieldCode;
  const kind = control.controlKind ?? 'INPUT';
  const dis = st.readonly || st.disabled;

  if (kind === 'CHILD_TABLE') {
    const rows = Array.isArray(value) ? (value as Record<string, unknown>[]) : [];
    const cols = rows.length ? Object.keys(rows[0]) : [];
    return (
      <div style={{ width: '100%' }}>
        {rows.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无明细行" style={{ padding: '10px 0', margin: 0 }} />
        ) : (
          <div className="uis-ctable">
            <table>
              <thead>
                <tr>{cols.map((c) => <th key={c}>{c}</th>)}<th style={{ width: 40 }} /></tr>
              </thead>
              <tbody>
                {rows.map((r, i) => (
                  <tr key={i}>
                    {cols.map((c) => (
                      <td key={c}>
                        <InputNumber
                          size="small" style={{ width: '100%' }} disabled={dis}
                          value={typeof r[c] === 'number' ? (r[c] as number) : undefined}
                          onChange={(nv) => {
                            const next = rows.map((rr, k) => (k === i ? { ...rr, [c]: nv ?? 0 } : rr));
                            onChange(next);
                          }}
                        />
                      </td>
                    ))}
                    <td>
                      <Button size="small" type="text" danger icon={<DeleteOutlined />} disabled={dis}
                        onClick={() => onChange(rows.filter((_, k) => k !== i))} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Button size="small" type="dashed" icon={<PlusOutlined />} style={{ marginTop: 6 }} disabled={dis}
          onClick={() => onChange([...rows, Object.fromEntries(cols.map((c) => [c, 0]))])}>
          添加明细行
        </Button>
        <div className="uis-sum-line">合计 {rows.reduce((a, r) => a + (Number(r.amount) || 0), 0)}</div>
      </div>
    );
  }

  if (kind === 'ALERT') {
    return <div className="uis-alert-box">{String(control.props?.warningText ?? value ?? '风险提示')}</div>;
  }

  if (kind === 'STAT' || kind === 'COMPUTED') {
    return (
      <div className="uis-stat" style={{ maxWidth: 220 }}>
        <div className="v">{String(value ?? '—')}</div>
        <div className="k">{kind === 'STAT' ? '统计值' : '计算值（后端为准）'}</div>
      </div>
    );
  }

  if (kind === 'TIMELINE') {
    const arr = Array.isArray(value) ? (value as unknown[]) : [];
    return (
      <div className="uis-timeline">
        {arr.length === 0
          ? <Text type="secondary" style={{ fontSize: 12 }}>暂无轨迹</Text>
          : arr.map((x, i) => (
            <div key={i} className="uis-tl-item">
              <div className="uis-tl-t">{typeof x === 'object' ? JSON.stringify(x) : String(x)}</div>
            </div>
          ))}
      </div>
    );
  }

  if (kind === 'ATTACHMENT') {
    const arr = Array.isArray(value) ? (value as unknown[]) : [];
    return (
      <div className="uis-att-list">
        {arr.length === 0
          ? <Text type="secondary" style={{ fontSize: 12 }}>未上传</Text>
          : arr.map((x, i) => (
            <div key={i} className="uis-att-item">
              <span className="nm">📎 {String(x)}</span>
              {!dis && <Button size="small" type="link" danger onClick={() => onChange(arr.filter((_, k) => k !== i))}>删除</Button>}
            </div>
          ))}
        {!dis && <Button size="small" icon={<PlusOutlined />}
          onClick={() => onChange([...arr, `附件${arr.length + 1}.pdf`])}>模拟上传</Button>}
      </div>
    );
  }

  if (kind === 'ENUM' || kind === 'REFERENCE') {
    const opts = optionsOf(code, fields, schema);
    return (
      <Select
        size="small" style={{ width: '100%', maxWidth: 320 }} disabled={dis} allowClear
        placeholder={control.placeholder ?? '请选择'}
        value={value === '' || value === undefined ? undefined : (value as string)}
        onChange={onChange}
        options={opts.length ? opts : [{ value: String(value ?? ''), label: String(value ?? '（无选项）') }]}
      />
    );
  }

  if (kind === 'SWITCH') return <Switch size="small" disabled={dis} checked={!!value} onChange={onChange} />;

  if (kind === 'NUMBER' || kind === 'MONEY') {
    return (
      <InputNumber
        size="small" style={{ width: '100%', maxWidth: 320 }} disabled={dis}
        placeholder={control.placeholder} value={value === '' ? undefined : (value as number)}
        onChange={onChange}
      />
    );
  }

  if (kind === 'DATE' || kind === 'DATETIME') {
    return (
      <Input
        size="small" type={kind === 'DATE' ? 'date' : 'datetime-local'} disabled={dis}
        style={{ width: '100%', maxWidth: 320 }}
        value={value === undefined || value === null ? '' : String(value)}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }

  if (kind === 'TEXTAREA') {
    return (
      <Input.TextArea size="small" rows={2} disabled={dis} placeholder={control.placeholder}
        value={value === undefined || value === null ? '' : String(value)}
        onChange={(e) => onChange(e.target.value)} />
    );
  }

  return (
    <Input
      size="small" disabled={dis} placeholder={control.placeholder}
      style={{ width: '100%', maxWidth: 320 }}
      value={value === undefined || value === null ? '' : String(value)}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

/* ---------------- 列表视图预览 ---------------- */
function ListViewPreview({ schema, fields, scenarioData }: {
  schema: BoneUiSchema; fields: MetaField[]; scenarioData: Record<string, unknown>;
}) {
  const list = schema.views.list;
  const [tab, setTab] = useState(0);

  if (!list) return <div className="uis-empty">该 schema 未定义列表视图（views.list）</div>;

  const columns = list.columns?.filter((c) => c.visible !== false) ?? [];
  const tabs = list.tabs ?? [];
  const stats = list.stats ?? [];
  const searchFields = list.searchFields ?? [];

  // 合成演示行（设计预览，非真实数据）：以场景数据为基，派生 3 种状态
  const demoRows = [
    scenarioData,
    { ...scenarioData, status: 'SUBMITTED' },
    { ...scenarioData, status: 'APPROVED' },
  ];

  const fmt = (c: string, v: unknown): string => {
    if (v === undefined || v === null || v === '') return '—';
    const col = columns.find((x) => x.fieldCode === c);
    if (col?.format?.currencyField) return `¥${Number(v).toFixed(Number(col.format?.precision ?? 2))}`;
    return String(v);
  };

  return (
    <div>
      {stats.length > 0 && (
        <div className="uis-stat-grid" style={{ marginBottom: 16 }}>
          {stats.map((s, i) => (
            <div key={i} className="uis-stat">
              <div className="v">—</div>
              <div className="k">{s.title ?? s.titleKey ?? `指标${i + 1}`}（聚合口径待接入）</div>
            </div>
          ))}
        </div>
      )}

      {tabs.length > 0 && (
        <div className="uis-tabs-bar">
          {tabs.map((t, i) => (
            <span key={i} className={`uis-tab ${i === tab ? 'active' : ''}`} onClick={() => setTab(i)}>
              {t.title ?? t.titleKey ?? String(t.value)}
            </span>
          ))}
        </div>
      )}

      {searchFields.length > 0 && (
        <div className="uis-search-bar">
          {searchFields.map((f) => (
            <div key={f} className="uis-search-box">
              <span style={{ color: 'var(--sub)', fontSize: 12 }}>🔍</span>
              <input placeholder={fields.find((x) => x.code === f)?.displayName ?? f} />
            </div>
          ))}
        </div>
      )}

      {list.dataScope && (
        <div style={{ marginBottom: 12 }}>
          <span className="uis-scope-chip">
            🛡 数据范围：{list.dataScope.policyCode}（后端 WHERE 强制，前端不拼）
          </span>
        </div>
      )}

      {columns.length === 0 ? (
        <div className="uis-empty">列表未配置可见列</div>
      ) : (
        <table className="uis-grid">
          <thead>
            <tr>{columns.map((c) => <th key={c.fieldCode}>{fields.find((f) => f.code === c.fieldCode)?.displayName ?? c.fieldCode}</th>)}</tr>
          </thead>
          <tbody>
            {demoRows.map((r, i) => (
              <tr key={i}>
                {columns.map((c) => <td key={c.fieldCode}>{fmt(c.fieldCode, r[c.fieldCode])}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

/* ---------------- 主组件 ---------------- */
export default function SchemaPreview({ schema, fields, entityCode }: Props): React.ReactElement {
  const [scenarioKey, setScenarioKey] = useState(SCENARIOS[1].key);
  const [data, setData] = useState<Record<string, unknown>>({});
  const [result, setResult] = useState<{ errors: string[]; warnings: string[] } | null>(null);
  const [view, setView] = useState<'form' | 'list'>('form');

  // 身份模拟：实时裁决变体
  const [roles, setRoles] = useState<string[]>([]);
  const [tier, setTier] = useState<string>('');
  const [channel, setChannel] = useState<string>('web');
  const [device, setDevice] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');

  const ctx: UiContext = useMemo(
    () => ({ roles: roles.length ? roles : undefined, tier: tier || undefined, channel: channel || undefined, device }),
    [roles, tier, channel, device],
  );

  const sections = useMemo(() => formOf(schema).sections ?? [], [schema]);
  const allCodes = useMemo(() => sections.flatMap((sec) => (sec.controls ?? []).map((c) => c.fieldCode)), [sections]);
  const shapeKey = allCodes.join('|');
  const fieldsKey = fields.map((f) => f.code).join('|');

  useEffect(() => {
    const sc = SCENARIOS.find((s) => s.key === scenarioKey) ?? SCENARIOS[0];
    setData(applyScenario(sc, allCodes, (code) => defaultOf(code, fields)));
    setResult(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioKey, shapeKey, fieldsKey]);

  // 变体裁决（与运行时同源）
  const resolved = useMemo(() => resolveVariant(schema, ctx), [schema, ctx]);
  const activeVariant = useMemo(() => {
    const hit = (schema.variants ?? [])
      .filter((v) => fieldMatch(v.match, ctx))
      .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100))[0];
    return hit ? (hit.label ?? hit.key) : 'base（默认）';
  }, [schema.variants, ctx]);

  const baseStates = useMemo(() => {
    const out: Record<string, Partial<FieldUiState>> = {};
    (formOf(resolved).sections ?? []).forEach((s) => (s.controls ?? []).forEach((c) => {
      if (c.visible === false || c.readonly) out[c.fieldCode] = { visible: c.visible !== false, readonly: !!c.readonly };
    }));
    return out;
  }, [resolved]);

  const baseRequired = useMemo(() => {
    const out: Record<string, boolean> = {};
    fields.forEach((f) => { if (f.required) out[f.code] = true; });
    return out;
  }, [fields]);

  const states = useMemo(() => {
    const snapshot = JSON.parse(JSON.stringify(data)) as Record<string, unknown>;
    return linkageState(snapshot, resolved.linkageRules ?? [], ctx, baseRequired, baseStates);
  }, [data, resolved.linkageRules, baseRequired, baseStates, ctx]);

  const visibleActions = useMemo(
    () => resolveVisibleActions(data, resolved.actionBar ?? [], ctx),
    [data, resolved.actionBar, ctx],
  );

  const setField = (code: string, v: unknown) => { setData((d) => ({ ...d, [code]: v })); setResult(null); };

  const doSubmit = () => {
    setResult(runSubmit(
      JSON.parse(JSON.stringify(data)) as Record<string, unknown>,
      resolved.submitRules ?? [], resolved.linkageRules ?? [], ctx, baseRequired,
    ));
  };

  const firedLinkage = (resolved.linkageRules ?? []).filter((r) => r.enabled !== false && evalCond(r.when, data, ctx));
  const activeRules = (resolved.linkageRules ?? []).filter((r) => r.enabled !== false).length;

  return (
    <div className="uis-root">
      <div className="uis-pv-head">
        <span className="uis-pv-entity">{entityCode}</span>
        <span className="uis-pv-badge">{view === 'form' ? '表单视图' : '列表视图'}</span>
        <Segmented
          size="small" value={view} onChange={(v) => setView(v as 'form' | 'list')}
          options={[{ value: 'form', label: '表单' }, { value: 'list', label: '列表' }]}
        />
        <Segmented
          size="small" value={scenarioKey} onChange={(v) => setScenarioKey(String(v))}
          options={SCENARIOS.map((s) => ({ value: s.key, label: s.label }))}
        />
        <Tooltip title="预览跑运行时同一套引擎，但不落库">
          <span className="uis-pv-badge">沙盒 · 不落库</span>
        </Tooltip>
      </div>

      <span className="uis-pv-desc">{SCENARIOS.find((s) => s.key === scenarioKey)?.desc}</span>

      {/* 身份模拟栏 */}
      <div className="uis-idbar">
        <span className="uis-lbl">身份模拟</span>
        <Select
          mode="multiple" size="small" allowClear placeholder="角色"
          style={{ minWidth: 160 }} value={roles}
          onChange={(v) => setRoles(v)}
          options={[
            { value: 'employee', label: '员工 employee' },
            { value: 'reviewer', label: '核赔 reviewer' },
            { value: 'admin', label: '管理员 admin' },
          ]}
        />
        <Select
          size="small" allowClear placeholder="渠道" style={{ width: 110 }} value={channel || undefined}
          onChange={(v) => setChannel(v ?? '')}
          options={[{ value: 'web', label: 'Web' }, { value: 'mobile', label: 'Mobile' }, { value: 'wechat', label: '微信' }]}
        />
        <Input size="small" placeholder="档位 tier" style={{ width: 100 }} value={tier} onChange={(e) => setTier(e.target.value)} />
        <Segmented
          size="small" value={device} onChange={(v) => setDevice(v as 'desktop' | 'tablet' | 'mobile')}
          options={[{ value: 'desktop', label: '桌面' }, { value: 'tablet', label: '平板' }, { value: 'mobile', label: '手机' }]}
        />
        <span className="uis-ctx-badge base">变体：{activeVariant}</span>
      </div>

      {/* 规则追踪 */}
      <details className="uis-trace" open>
        <summary>⚡ 规则追踪（{firedLinkage.length}/{activeRules} 联动命中）</summary>
        <div className="chips">
          {(resolved.linkageRules ?? []).map((r) => {
            const on = firedLinkage.includes(r);
            return <span key={r.code ?? r.name} className={`uis-chip ${on ? '' : 'warn'}`}>{on ? '●' : '○'} {r.code ?? r.name ?? '规则'}</span>;
          })}
          {(resolved.linkageRules ?? []).length === 0 && <span className="uis-chip warn">无联动规则</span>}
        </div>
      </details>

      <div className={`uis-stage ${device}`}>
        {view === 'list' ? (
          <div className="uis-list-card">
            <ListViewPreview schema={resolved} fields={fields} scenarioData={data} />
          </div>
        ) : (
          <>
            {/* 动作条 */}
            {visibleActions.length > 0 && (
              <div className="uis-actionbar" style={{ borderTop: 'none', paddingTop: 0, marginTop: 0, marginBottom: 16 }}>
                <span style={{ color: 'var(--sub)', fontSize: 11, alignSelf: 'center' }}>动作条（{visibleActions.length}/{resolved.actionBar?.length ?? 0} 可见）</span>
                {visibleActions.map((a, i) => {
                  const primary = i === 0;
                  const danger = a.actionCode === 'reject' || a.actionCode === 'appeal';
                  return (
                    <button
                      key={a.actionCode}
                      className={`uis-btn ${primary ? 'primary' : ''} ${danger ? 'danger' : ''}`}
                      onClick={() => message.info(`将触发动作：${a.actionCode}`)}
                    >
                      {a.actionCode}
                    </button>
                  );
                })}
              </div>
            )}

            {sections.length === 0 && <div className="uis-empty">还没有配置任何分区</div>}

            {(formOf(resolved).sections ?? []).map((sec, si) => {
              const ctrls = (sec.controls ?? []).filter((c) => states[c.fieldCode]?.visible !== false);
              if (!ctrls.length) return null;
              return (
                <div key={sec.id ?? si} className={`uis-seccard ${sec.kind === 'dashboard' ? 'uis-dash' : ''}`}>
                  <div className="uis-sec-title">
                    {sec.title ?? sec.id}
                    {sec.kind && <span className="kind">{sec.kind}</span>}
                  </div>
                  <div className="uis-grid2">
                    {ctrls.map((c, ci) => {
                      const st = states[c.fieldCode] ?? { visible: true, required: false, readonly: false, disabled: false, notice: '' };
                      const span = (c.props?.span as number | undefined) ?? 12;
                      const f = fields.find((x) => x.code === c.fieldCode);
                      const full = span >= 24 || c.controlKind === 'CHILD_TABLE' || c.controlKind === 'ATTACHMENT' || c.controlKind === 'TIMELINE';
                      return (
                        <div key={`${c.fieldCode}-${ci}`} className={full ? 'uis-full' : ''}>
                          <div className="uis-fld">
                            <label>
                              {c.labelOverride || f?.displayName || c.fieldCode}
                              {st.required && <span className="req">*</span>}
                              {st.readonly && <span className="ro">只读</span>}
                              {c.virtual && <span className="ro">虚拟</span>}
                            </label>
                            <FieldControl
                              control={c} st={st} fields={fields} schema={resolved}
                              value={data[c.fieldCode]} onChange={(v) => setField(c.fieldCode, v)}
                            />
                            {st.notice && <div className="uis-notice">{st.notice}</div>}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}

            {/* 提交区 */}
            <div className="uis-seccard">
              <Space wrap>
                <button className="uis-btn primary" onClick={doSubmit}>模拟提交校验</button>
                <span style={{ color: 'var(--sub)', fontSize: 11 }}>
                  生效联动 {activeRules}/{resolved.linkageRules?.length ?? 0} · 校验 {resolved.submitRules?.length ?? 0}
                </span>
              </Space>
              {result && (
                <div style={{ marginTop: 10 }}>
                  {result.errors.length === 0 && result.warnings.length === 0 ? (
                    <Alert type="success" showIcon icon={<CheckCircleOutlined />} message="校验通过，可以提交" />
                  ) : (
                    <>
                      {result.errors.map((m, i) => (
                        <Alert key={`e${i}`} type="error" showIcon message={m} style={{ marginBottom: 6 }} />
                      ))}
                      {result.warnings.map((m, i) => (
                        <Alert key={`w${i}`} type="warning" showIcon message={m} style={{ marginBottom: 6 }} />
                      ))}
                    </>
                  )}
                </div>
              )}
            </div>

            {resolved.footer && (
              <div className="uis-footer">
                {resolved.footer.aiBadge && <span className="ai-badge">AI</span>}
                <span>{resolved.footer.disclaimer ?? '本界面由配置生成'}</span>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
