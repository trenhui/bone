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
import { memo, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Empty, Input, InputNumber, Segmented, Select, Space, Switch, Tooltip, Typography, message,
} from 'antd';
import {
  CheckCircleOutlined, DeleteOutlined, PlusOutlined,
} from '@ant-design/icons';
import { parseEnumValues } from '@bone/shared-types';
import {
  controlBaseStates, enforceableFieldCodes, evalCond, fieldVisible, linkageState, resolveVariant, resolveVisibleActions, runSubmit, validateControlValues,
  type BoneUiSchema, type Control, type FieldUiState, type Section, type UiContext, type VariantMatch,
} from '@bone/ui-schema';
import type { MetaField } from '../../types';
import { gridItemStyle, isFullWidthControl } from '../../utils/uiGrid';
import { dictOf, formOf } from './schemaOps';
import { ROLE_OPTIONS } from './constants';
import { SCENARIOS, applyScenario, scenarioApplies } from './scenarios';
import './uiSchemaTheme.css';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  fields: MetaField[];
  entityCode: string;
  /** 左侧结构面板正在编辑的视图（form/detail），预览默认跟随，保持「左改右见」一致 */
  editView?: 'form' | 'detail';
  /**
   * 已发布快照（线上真源，后端 published_schema_json）。传入后预览头部出现
   * 「草稿 / 线上」对照开关——对标 Figma 版本对比 / 飞书已启用态：
   * 建模者不出设计器即可看到「终端用户此刻看到的画面」，再决定是否发布。
   */
  publishedSchema?: BoneUiSchema | null;
  /** 已存草稿与线上快照存在差异（设计器 publishState==='pending'）；线上视图下给出提示 */
  draftDiffers?: boolean;
  /** 点击规则追踪 chip 跳到对应规则（designer 用 schemaRef 反查索引，保持引用稳定） */
  onLocateRule?: (ruleKey: string) => void;
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

/**
 * N5：类型化样例数据（行业 builder 标配的「样例数据预览」）。
 * 仅对简单输入类控件生成贴近真实的样例值，用于感知真实长度与排版拥挤度；
 * 展示型（STAT/CHART/TIMELINE/COMPUTED/QUOTE/ALERT）与复合（CHILD_TABLE/ATTACHMENT）返回 undefined，沿用场景/空值。
 */
function sampleValueFor(c: Control, fields: MetaField[]): unknown {
  const f = fields.find((x) => x.code === c.fieldCode);
  const k = (c.controlKind ?? 'INPUT').toUpperCase();
  switch (k) {
  case 'SWITCH': return true;
  case 'NUMBER': case 'MONEY': return 12345.67;
  case 'SLIDER': return 50;
  case 'DATE': return '2026-10-19';
  case 'DATETIME': return '2026-10-19 10:15:00';
  case 'TEXTAREA': return '这是一段用于预览排版拥挤度的示例长文本，用于感知控件在真实数据下的展示效果与换行表现。';
  case 'ENUM': case 'REFERENCE': return f?.enumValues ? (parseEnumValues(f.enumValues)[0]?.code ?? 'SAMPLE') : 'SAMPLE';
  case 'INPUT': default: return `示例_${c.fieldCode}`;
  }
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
  const [quick, setQuick] = useState<string[]>([]);
  const [checked, setChecked] = useState<number[]>([]);

  if (!list) return <div className="uis-empty">该 schema 未定义列表视图（views.list）</div>;

  const columns = list.columns?.filter((c) => c.visible !== false) ?? [];
  const tabs = list.tabs ?? [];
  const stats = list.stats ?? [];
  const searchFields = list.searchFields ?? [];
  const quickFilters = list.quickFilters ?? [];
  const batchActions = list.batchActions ?? [];
  const viewKind = list.viewKind ?? 'table';
  const degraded = list.degradedBanner;

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

  const allChecked = checked.length === demoRows.length && demoRows.length > 0;

  return (
    <div>
      {/* 视图形态徽标（table=首版完整形态；kanban/calendar=P1，运行时降级） */}
      {viewKind !== 'table' && (
        <div style={{ marginBottom: 10 }}>
          <span className="uis-vk-badge">视图形态：{viewKind}（P1 · 运行时按表格降级）</span>
        </div>
      )}

      {/* 降级横幅（协议对象 when+text；预览直接展示，注明触发条件） */}
      {degraded?.text && (
        <div className="uis-degraded">
          <span>⚠️</span>
          <span>
            <Text strong>{degraded.text}</Text>
            <Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>触发条件：{degraded.when}（后端降级开关，UI 不判断）</Text>
          </span>
        </div>
      )}

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

      {/* 快捷筛选（协议 quickFilters：code 数组，运行时翻译为具名筛选入口） */}
      {quickFilters.length > 0 && (
        <div className="uis-quick-filters">
          <span className="uis-qf-lbl">快捷筛选</span>
          {quickFilters.map((code) => {
            const on = quick.includes(code);
            return (
              <span key={code} className={`uis-qf ${on ? 'active' : ''}`}
                onClick={() => setQuick(on ? quick.filter((c) => c !== code) : [...quick, code])}>
                {code}
              </span>
            );
          })}
        </div>
      )}

      {list.dataScope && (
        <div style={{ marginBottom: 12 }}>
          <span className="uis-scope-chip">
            🛡 数据范围：{list.dataScope.policyCode}（后端 WHERE 强制，前端不拼）
          </span>
        </div>
      )}

      {/* 批量动作工具条（勾选演示行后激活；预览不真调动作） */}
      {batchActions.length > 0 && (
        <div className="uis-batch-bar">
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={allChecked}
              onChange={(e) => setChecked(e.target.checked ? demoRows.map((_, i) => i) : [])} />
            <span className="cnt">{checked.length ? `已选 ${checked.length} 项` : '全选'}</span>
          </label>
          <span style={{ flex: 1 }} />
          {batchActions.map((a) => (
            <button key={a} className="uis-batch-btn"
              onClick={() => message.info(`将对 ${checked.length || '所选'} 项执行：${a}`)}>
              {a}
            </button>
          ))}
        </div>
      )}

      {columns.length === 0 ? (
        <div className="uis-empty">列表未配置可见列</div>
      ) : (
        <table className="uis-grid">
          <thead>
            <tr>
              {batchActions.length > 0 && <th className="sel">✓</th>}
              {columns.map((c) => (
                <th key={c.fieldCode} className={c.pinned ? `pin-${c.pinned}` : undefined}>
                  {fields.find((f) => f.code === c.fieldCode)?.displayName ?? c.fieldCode}
                  {c.pinned && <span title={`固定${c.pinned === 'right' ? '右侧' : '左侧'}`} style={{ marginLeft: 4, fontSize: 10 }}>📌</span>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {demoRows.map((r, i) => (
              <tr key={i} className={checked.includes(i) ? 'row-checked' : undefined}>
                {batchActions.length > 0 && (
                  <td className="sel">
                    <input type="checkbox" checked={checked.includes(i)}
                      onChange={(e) => setChecked(e.target.checked ? [...checked, i] : checked.filter((k) => k !== i))} />
                  </td>
                )}
                {columns.map((c) => (
                  <td key={c.fieldCode} className={c.pinned ? `pin-${c.pinned}` : undefined}>{fmt(c.fieldCode, r[c.fieldCode])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

/* ---------------- 字段行（单栏网格 / 分栏共用） ---------------- */
function FieldRow({ control, states, fields, schema, data, onSet, inSplit = false }: {
  control: Control;
  states: Record<string, FieldUiState>;
  fields: MetaField[];
  schema: BoneUiSchema;
  data: Record<string, unknown>;
  onSet: (code: string, v: unknown) => void;
  /** 分栏模式下为单列堆叠，full 宽类无意义（不传） */
  inSplit?: boolean;
}) {
  const st = states[control.fieldCode] ?? { visible: true, required: false, readonly: false, disabled: false, notice: '' };
  const f = fields.find((x) => x.code === control.fieldCode);
  // 栅格宽度走共享真源 utils/uiGrid（span=24 或表格/附件/时间线 → 整行；缺省半行）：
  // 预览与运行时（BoneDynamicForm）同一计算，双渲染器不漂移
  const full = !inSplit && isFullWidthControl(control);
  return (
    <div className={full ? 'uis-full' : undefined} style={inSplit ? undefined : gridItemStyle(control)}>
      <div className="uis-fld">
        <label>
          {control.labelOverride || f?.displayName || control.fieldCode}
          {(st.required || control.required) && <span className="req">*</span>}
          {st.readonly && <span className="ro">只读</span>}
          {control.virtual && <span className="ro">虚拟</span>}
        </label>
        <FieldControl
          control={control} st={st} fields={fields} schema={schema}
          value={data[control.fieldCode]} onChange={(v) => onSet(control.fieldCode, v)}
        />
        {st.notice && <div className="uis-notice">{st.notice}</div>}
      </div>
    </div>
  );
}

/* ---------------- 分区（Section 级 memo） ---------------- */

/**
 * 单个分区的预览渲染，memo 到分区粒度。
 * 大 schema（50 分区/200 控件）下，预览内某字段的编辑/联动重算只应波及
 * 相关分区，而不是整张表单重渲染。props 全部按引用/原值传递：
 * sec 引用随 schema 不可变更新保持稳定，states/data 仅在联动重算时换新。
 */
const PreviewSection = memo(function PreviewSection({
  sec, states, fields, schema, data, onSet, ctx,
}: {
  sec: Section;
  states: Record<string, FieldUiState>;
  fields: MetaField[];
  schema: BoneUiSchema;
  data: Record<string, unknown>;
  onSet: (code: string, v: unknown) => void;
  ctx: UiContext;
}) {
  // 可见性走同源 fieldVisible：联动 then.tier 档位门控在此生效（不声明=不限制）
  const ctrls = (sec.controls ?? []).filter((c) => fieldVisible(states[c.fieldCode], ctx));
  if (!ctrls.length) return null;
  const layout = sec.layout ?? 'single';
  // split-left-40/60：按当前可见控件顺序对半切分（顺序即分栏，与拖拽排序同源）。
  // 控件数 <2 时无分栏意义，退化为单栏。
  const split = layout === 'split-left-40' || layout === 'split-left-60';
  if (split && ctrls.length >= 2) {
    const mid = Math.ceil(ctrls.length / 2);
    const pct = layout === 'split-left-40' ? '40%' : '60%';
    return (
      <div>
        {/* 分栏分区同样要渲染标题 + kind 徽标（探针实测：漏渲染会让
            「split + dashboard」分区的身份不可见——采购审查左风险面板
            右原文时，用户分不清哪栏是什么） */}
        <div className="uis-sec-title" style={{ marginBottom: 10 }}>
          {sec.title ?? sec.id}
          {sec.kind && <span className={`kind k-${sec.kind}`}>{sec.kind}</span>}
          <span className="kind">分栏 {pct}/{layout === 'split-left-40' ? '60%' : '40%'}</span>
        </div>
        <div className={`uis-split ${layout === 'split-left-40' ? 's40' : 's60'}`}>
          <div className="uis-split-col">
            <span className="uis-split-tag">◀ 左栏（{pct}）</span>
            {ctrls.slice(0, mid).map((c, ci) => (
              <FieldRow key={`${c.fieldCode}-${ci}`} control={c} inSplit
                states={states} fields={fields} schema={schema} data={data} onSet={onSet} />
            ))}
          </div>
          <div className="uis-split-col">
            <span className="uis-split-tag">右栏 ▶</span>
            {ctrls.slice(mid).map((c, ci) => (
              <FieldRow key={`${c.fieldCode}-${ci}`} control={c} inSplit
                states={states} fields={fields} schema={schema} data={data} onSet={onSet} />
            ))}
          </div>
        </div>
      </div>
    );
  }
  return (
    <div className={`uis-seccard ${sec.kind === 'dashboard' ? 'uis-dash' : ''}`}>
      <div className="uis-sec-title">
        {sec.title ?? sec.id}
        {sec.kind && <span className={`kind k-${sec.kind}`}>{sec.kind}</span>}
      </div>
      <div className="uis-grid2">
        {ctrls.map((c, ci) => (
          <FieldRow key={`${c.fieldCode}-${ci}`} control={c}
            states={states} fields={fields} schema={schema} data={data} onSet={onSet} />
        ))}
      </div>
    </div>
  );
});

/* ---------------- 主组件 ---------------- */
export default function SchemaPreview({
  schema, fields, entityCode, editView = 'form', publishedSchema = null, draftDiffers = false, onLocateRule,
}: Props): React.ReactElement {
  const [scenarioKey, setScenarioKey] = useState('blank');
  const [scenarioPicked, setScenarioPicked] = useState(false);
  const [previewMode, setPreviewMode] = useState<'form' | 'detail' | 'list'>(editView);
  /**
   * 预览源：draft=正在编辑的草稿（默认，左改右见）；
   * published=线上发布快照（终端用户真实画面，只读对照）。
   * 无 publishedSchema 时开关不渲染，恒为草稿。
   */
  const [source, setSource] = useState<'draft' | 'published'>('draft');
  const activeSchema = source === 'published' && publishedSchema ? publishedSchema : schema;
  const [data, setData] = useState<Record<string, unknown>>({});
  const [result, setResult] = useState<{ errors: string[]; warnings: string[] } | null>(null);
  /** N5：样例数据预览开关（填充类型化样例值，感知真实长度/排版拥挤度） */
  const [sampleOn, setSampleOn] = useState(false);

  // 身份模拟：实时裁决变体
  const [roles, setRoles] = useState<string[]>([]);
  const [tier, setTier] = useState<string>('');
  const [channel, setChannel] = useState<string>('web');
  const [device, setDevice] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');

  const ctx: UiContext = useMemo(
    () => ({ roles: roles.length ? roles : undefined, tier: tier || undefined, channel: channel || undefined, device }),
    [roles, tier, channel, device],
  );

  const fieldsKey = fields.map((f) => f.code).join('|');

  // 场景按实体领域过滤：报销单不出现「门诊快赔」这类跨领域演示场景。
  // 依赖取 fieldsKey（fields.code 集合的稳定投影）：fields 引用每渲染可能新对象，
  // 而内容变化必反映在 fieldsKey —— 以 key 为依赖既可重算又不抖动。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const visibleScenarios = useMemo(() => SCENARIOS.filter((s) => scenarioApplies(s, fields)), [fieldsKey]);
  // 当前选中场景被过滤掉时（如切换实体），优先回落到领域场景，其次空白
  const effScenarioKey = visibleScenarios.some((s) => s.key === scenarioKey)
    ? scenarioKey
    : (visibleScenarios.find((s) => s.key !== 'blank') ?? visibleScenarios[0])?.key ?? 'blank';

  // 首次进入：根据实体领域自动选最贴合的演示场景（不再硬编码「门诊快赔」），避免跨领域误导；
  // 用户一旦显式选择（scenarioPicked=true）即固定，不再被自动覆盖。
  useEffect(() => {
    if (scenarioPicked) return;
    const best = visibleScenarios.find((s) => s.key !== 'blank');
    if (best) setScenarioKey(best.key);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleScenarios]);

  // 预览视图默认跟随左侧正在编辑的视图（form/detail）；用户手动点「列表」后不再被拽回
  useEffect(() => {
    if (previewMode !== 'list') setPreviewMode(editView);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editView]);

  // 预览渲染的视图分区（表单 / 详情；列表视图由 ListViewPreview 单独渲染）
  const previewSections = previewMode === 'list'
    ? []
    : (previewMode === 'detail' ? (activeSchema.views.detail?.sections ?? []) : formOf(activeSchema).sections ?? []);
  // 页头关键信息条（v1.2 §13.2）：与分区互斥，随预览视图切换取各自配置
  const pageHeaderCodes = previewMode === 'list'
    ? []
    : (previewMode === 'detail'
      ? (activeSchema.views.detail?.pageHeader ?? [])
      : (formOf(activeSchema).pageHeader ?? []));
  const listCodes = (activeSchema.views.list?.columns ?? []).map((c) => c.fieldCode);
  const allCodes = previewMode === 'list'
    ? listCodes
    : previewSections.flatMap((sec) => (sec.controls ?? []).map((c) => c.fieldCode));
  const shapeKey = allCodes.join('|');

  useEffect(() => {
    const sc = SCENARIOS.find((s) => s.key === effScenarioKey) ?? SCENARIOS[0];
    const base = applyScenario(sc, allCodes, (code) => defaultOf(code, fields));
    if (sampleOn) {
      const sample: Record<string, unknown> = {};
      previewSections.flatMap((s) => s.controls ?? []).forEach((c) => {
        const v = sampleValueFor(c, fields);
        if (v !== undefined) sample[c.fieldCode] = v;
      });
      setData({ ...base, ...sample });
    } else {
      setData(base);
    }
    setResult(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [effScenarioKey, shapeKey, fieldsKey, sampleOn]);

  // 变体裁决（与运行时同源）
  const resolved = useMemo(() => resolveVariant(activeSchema, ctx), [activeSchema, ctx]);
  const activeVariant = useMemo(() => {
    const hit = (activeSchema.variants ?? [])
      .filter((v) => fieldMatch(v.match, ctx))
      .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100))[0];
    return hit ? (hit.label ?? hit.key) : 'base（默认）';
  }, [activeSchema.variants, ctx]);

  const baseStates = useMemo(() => controlBaseStates(formOf(resolved).sections ?? []), [resolved]);

  const baseRequired = useMemo(() => {
    const out: Record<string, boolean> = {};
    fields.forEach((f) => { if (f.required) out[f.code] = true; });
    return out;
  }, [fields]);

  const states = useMemo(() => {
    // structuredClone 取代 JSON 深拷贝：原型链/undefined 保真，且避免大表单（200 控件）下
    // 每次联动重算都走一次序列化（热路径；对标不可变更新里的结构性克隆用原生 API）。
    const snapshot = structuredClone(data);
    return linkageState(snapshot, resolved.linkageRules ?? [], ctx, baseRequired, baseStates);
  }, [data, resolved.linkageRules, baseRequired, baseStates, ctx]);

  const visibleActions = useMemo(
    () => resolveVisibleActions(data, resolved.actionBar ?? [], ctx),
    [data, resolved.actionBar, ctx],
  );

  const setField = (code: string, v: unknown) => { setData((d) => ({ ...d, [code]: v })); setResult(null); };

  const doSubmit = () => {
    const snapshot = structuredClone(data);
    const formControls = (formOf(resolved).sections ?? []).flatMap((s) => s.controls ?? []);
    // 必填强制域收窄与运行时（BoneDynamicForm）严格同源：附件/明细行无物理列，
    // 前端若比后端严会把「本应放行」的提交拦在预览里，预览即失真。
    const enforceable = enforceableFieldCodes(formControls, fields.map((f) => f.code));
    const r = runSubmit(snapshot, resolved.submitRules ?? [], resolved.linkageRules ?? [], ctx, baseRequired, enforceable);
    // N4：控件级校验（必填覆盖 / 最值 / 长度 / 正则）与提交规则叠加，预览=运行时同源
    const c = validateControlValues(snapshot, formControls, baseRequired, enforceable);
    setResult({ errors: [...r.errors, ...c.errors], warnings: [...r.warnings, ...c.warnings] });
  };

  const firedLinkage = (resolved.linkageRules ?? []).filter((r) => r.enabled !== false && evalCond(r.when, data, ctx));
  const activeRules = (resolved.linkageRules ?? []).filter((r) => r.enabled !== false).length;

  return (
    <div className="uis-root">
      <div className="uis-pv-head">
        <span className="uis-pv-entity">{entityCode}</span>
        <span className="uis-pv-badge">{previewMode === 'list' ? '列表视图' : previewMode === 'detail' ? '详情视图' : '表单视图'}</span>
        <Segmented
          size="small" value={previewMode} onChange={(v) => setPreviewMode(v as 'form' | 'detail' | 'list')}
          options={[{ value: 'form', label: '表单' }, { value: 'detail', label: '详情' }, { value: 'list', label: '列表' }]}
        />
        {/* 草稿 / 线上对照开关（仅存在已发布快照时出现；对标 Figma 版本对比） */}
        {publishedSchema && (
          <Segmented
            size="small" value={source}
            onChange={(v) => setSource(v as 'draft' | 'published')}
            options={[{ value: 'draft', label: '草稿' }, { value: 'published', label: '线上' }]}
          />
        )}
        <Tooltip
          title={
            source === 'published' && publishedSchema
              ? '线上发布快照（后端 published_schema_json）——终端用户真实画面，只读'
              : '正在编辑的草稿；保存并发布后才对终端用户生效'
          }
        >
          <span
            className="uis-pv-badge uis-pv-source"
            data-source={source === 'published' && publishedSchema ? 'published' : 'draft'}
          >
            {source === 'published' && publishedSchema ? '线上发布版 · 只读' : '草稿'}
          </span>
        </Tooltip>
        <Segmented
          size="small" value={effScenarioKey} onChange={(v) => { setScenarioKey(String(v)); setScenarioPicked(true); }}
          options={visibleScenarios.map((s) => ({ value: s.key, label: s.label }))}
        />
        <Tooltip title="填充类型化样例数据，感知真实长度与排版拥挤度（行业 builder 标配）；关闭则按场景/空值预览">
          <Space size={4} style={{ marginLeft: 4 }}>
            <Switch size="small" aria-label="样例数据预览" checked={sampleOn} onChange={setSampleOn} />
            <Text style={{ fontSize: 12 }}>样例数据</Text>
          </Space>
        </Tooltip>
        <Tooltip title="预览跑运行时同一套引擎，但不落库">
          <span className="uis-pv-badge">沙盒 · 不落库</span>
        </Tooltip>
      </div>

      <span className="uis-pv-desc">{SCENARIOS.find((s) => s.key === effScenarioKey)?.desc}</span>

      {/* 线上对照 + 草稿有未发布改动 ⇒ 显式警示，避免误把草稿效果当成线上现状 */}
      {source === 'published' && publishedSchema && draftDiffers && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 10 }}
          message="正在查看线上发布版；已存草稿有未发布改动（见工具栏「变更预告」）"
        />
      )}

      {/* 身份模拟栏 */}
      <div className="uis-idbar">
        <span className="uis-lbl">身份模拟</span>
        <Select
          mode="multiple" size="small" allowClear placeholder="角色"
          style={{ minWidth: 160 }} value={roles}
          onChange={(v) => setRoles(v)}
          options={ROLE_OPTIONS}
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
            const key = r.code ?? r.name ?? '';
            return (
              <span
                key={key}
                role="button"
                tabIndex={0}
                aria-label={`定位规则 ${key}`}
                onClick={() => onLocateRule?.(key)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onLocateRule?.(key); } }}
                className={`uis-chip ${on ? '' : 'warn'}`}
                style={{ cursor: onLocateRule ? 'pointer' : 'default' }}
              >{on ? '●' : '○'} {key || '规则'}</span>
            );
          })}
          {(resolved.linkageRules ?? []).length === 0 && <span className="uis-chip warn">无联动规则</span>}
        </div>
      </details>

      <div className={`uis-stage ${device}`}>
        {previewMode === 'list' ? (
          <div className="uis-list-card">
            <ListViewPreview schema={resolved} fields={fields} scenarioData={data} />
          </div>
        ) : (
          <>
            {/* 页头关键信息条（v1.2 §13.2：引用字段渲染于分区之外，如 案件号/申请人/状态） */}
            {pageHeaderCodes.length > 0 && (
              <div className="uis-page-header">
                {pageHeaderCodes.map((code) => {
                  const f = fields.find((x) => x.code === code);
                  const v = data[code];
                  const empty = v === undefined || v === null || v === '';
                  return (
                    <div key={code} className="uis-ph-item">
                      <span className="k">{f?.displayName ?? code}</span>
                      <span className={`v ${empty ? 'empty' : ''}`}>{empty ? '—' : String(v)}</span>
                    </div>
                  );
                })}
              </div>
            )}

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

            {previewSections.length === 0 && <div className="uis-empty">{previewMode === 'detail' ? '详情视图还没有配置任何分区' : '还没有配置任何分区'}</div>}

            {previewSections.map((sec, si) => (
              <PreviewSection
                key={sec.id ?? si}
                sec={sec}
                states={states} fields={fields} schema={resolved} data={data} onSet={setField} ctx={ctx}
              />
            ))}

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
                {resolved.footer.mockDataMark && <span className="mock-mark">演示数据</span>}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
