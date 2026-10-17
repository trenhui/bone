import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, DatePicker, Form, Input, InputNumber, Select, Space, Switch, Table, Typography, message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { MetaField } from '../types';
import { isRenderSupported } from '../utils/columnProjection';
import { parseEnumValues } from '@bone/shared-types';
import {
  fieldVisible,
  linkageState,
  resolveVisibleActions,
  runSubmit,
  type ActionBarItem,
  type BoneUiSchema,
  type Control,
  type FieldUiState,
  type FormView,
  type SubmitRule,
  type LinkageRule,
  type UiContext,
} from '../utils/uiSchemaEngine';
import { runtimeRecordApi } from '../services/metadataApi';

const { Text } = Typography;

export type DisplayMode = 'create' | 'edit' | 'view';

/** 分区卡片（轻量，无画布）；kind 徽标让 dashboard/timeline/compare/report/tasks 分区在运行时肉眼可辨 */
function CardSection({ title, kind, children }: { title: string; kind?: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 16, border: '1px solid #f0f0f0', borderRadius: 8, padding: '12px 16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <Text strong>{title}</Text>
        {kind && kind !== 'form' && (
          <span style={{ fontSize: 11, color: '#6b7280', border: '1px solid #eef0f5', borderRadius: 6, padding: '0 6px' }}>{kind}</span>
        )}
      </div>
      {children}
    </div>
  );
}

/** 页头关键信息条（v1.2 §13.2：与分区互斥，引用字段渲染于分区之前，如 案件号/申请人/状态） */
function PageHeaderBar({ codes, data, fields }: { codes: string[]; data: Record<string, unknown>; fields: MetaField[] }) {
  if (!codes.length) return null;
  return (
    <div style={{
      display: 'flex', flexWrap: 'wrap', gap: '10px 26px', padding: '12px 16px', marginBottom: 16,
      background: 'linear-gradient(135deg, #eef3ff, #ffffff)', border: '1px solid #dfe9ff', borderRadius: 8,
    }}>
      {codes.map((code) => {
        const f = fields.find((x) => x.code === code);
        const v = data[code];
        const empty = v === undefined || v === null || v === '';
        return (
          <div key={code} style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
            <span style={{ fontSize: 11, color: '#6b7280' }}>{f?.displayName ?? code}</span>
            <span style={{ fontSize: 13, fontWeight: 700, color: empty ? '#c0c4cc' : '#131720' }}>{empty ? '—' : String(v)}</span>
          </div>
        );
      })}
    </div>
  );
}

/** 常驻合规条（协议 footer 四键白名单：免责声明 + AI 标识 + 模拟数据标注） */
function SchemaFooterBar({ footer }: { footer: NonNullable<BoneUiSchema['footer']> }) {
  return (
    <div style={{
      marginTop: 16, padding: '10px 14px', border: '1px solid #eef0f5', borderRadius: 10,
      background: '#fafbfd', fontSize: 11, color: '#6b7280', display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
    }}>
      {footer.aiBadge && <span style={{ fontSize: 10, color: '#2f6bff', background: '#eef3ff', borderRadius: 20, padding: '1px 8px', fontWeight: 600 }}>AI</span>}
      <span>{footer.disclaimer ?? '本界面由配置生成'}</span>
      {footer.mockDataMark && <span style={{ fontSize: 10, color: '#b08d3a', background: '#fdf3e0', border: '1px solid #ffe1a8', borderRadius: 4, padding: '1px 6px', fontWeight: 600 }}>演示数据</span>}
    </div>
  );
}

interface Props {
  schema: BoneUiSchema;
  fields: MetaField[];
  /** 初始记录（编辑/详情）；create 时按字段默认值初始化 */
  record?: Record<string, unknown>;
  recordId?: string;
  entityCode: string;
  displayMode?: DisplayMode;
  ctx?: UiContext;
  /** 保存回调：返回（经前端 submit 校验后的）数据；子表由组件内 replaceChildren 落库 */
  onSubmit?: (data: Record<string, unknown>) => Promise<void> | void;
  /** quiet=true 时跳过组件内成功 toast（调用方接管提示文案，如 RuntimeDataManagement 的创建/更新成功） */
  quiet?: boolean;
}

/** 字段基线态：从控件声明派生（如 tax_no 默认隐藏） */
function baseStatesOf(form: FormView | undefined): Record<string, Partial<FieldUiState>> {
  const out: Record<string, Partial<FieldUiState>> = {};
  form?.sections.forEach((s) => s.controls.forEach((c) => {
    if (c.visible === false || c.readonly) {
      out[c.fieldCode] = { ...(out[c.fieldCode] ?? {}), visible: c.visible !== false, readonly: c.readonly || out[c.fieldCode]?.readonly };
    }
  }));
  return out;
}

/** 字段静态必填基线（来自 meta_field.required） */
function baseRequiredOf(fields: MetaField[]): Record<string, boolean> {
  return Object.fromEntries(fields.filter((f) => f.required).map((f) => [f.code, true]));
}

export const BoneDynamicForm: React.FC<Props> = ({
  schema, fields, record, recordId, entityCode, displayMode = 'edit', ctx = {}, onSubmit, quiet = false,
}) => {
  const form = schema.views.form;
  const readOnly = displayMode === 'view';
  const [data, setData] = useState<Record<string, unknown>>({});
  const [childRows, setChildRows] = useState<Record<string, Record<string, unknown>[]>>({});
  const [referenceOptions, setReferenceOptions] = useState<Record<string, { value: string; label: string }[]>>({});
  const [submitMsg, setSubmitMsg] = useState<{ errors: string[]; warnings: string[] } | null>(null);
  const [saving, setSaving] = useState(false);

  const fieldMap = useMemo(() => Object.fromEntries(fields.map((f) => [f.code, f])), [fields]);
  // 规则/动作条包 useMemo：下游 linkageState/visibleActions 的 useMemo 依赖它们，
  // 不包则每次渲染新数组引用 ⇒ state 重算死循环风险（react-hooks/exhaustive-deps 要求）
  const linkageRules: LinkageRule[] = useMemo(() => schema.linkageRules ?? [], [schema.linkageRules]);
  const submitRules: SubmitRule[] = useMemo(() => schema.submitRules ?? [], [schema.submitRules]);
  const actionBar: ActionBarItem[] = useMemo(() => schema.actionBar ?? [], [schema.actionBar]);

  // 初始化数据
  useEffect(() => {
    const init: Record<string, unknown> = {};
    fields.forEach((f) => { init[f.code] = record?.[f.code] ?? (f.type === 'BOOLEAN' ? false : ''); });
    if (record) Object.assign(init, record);
    setData(init);
    // 预载 REFERENCE 候选
    form?.sections.forEach((s) => s.controls.forEach((c) => {
      if (c.controlKind === 'REFERENCE' && (c.props as { targetEntity?: string } | undefined)?.targetEntity) {
        const t = (c.props as { targetEntity: string }).targetEntity;
        runtimeRecordApi.lookupOptions(t, c.fieldCode).then((res) => {
          if (res.code === 200) {
            setReferenceOptions((prev) => ({ ...prev, [c.fieldCode]: res.data.map((o) => ({ value: String(o.value), label: o.label })) }));
          }
        });
      }
    }));
    // 预载 CHILD_TABLE 明细（编辑态）
    if (recordId) {
      form?.sections.forEach((s) => s.controls.forEach((c) => {
        if (c.controlKind === 'CHILD_TABLE') {
          const child = (c.props as { childEntity?: string } | undefined)?.childEntity;
          if (child) {
            runtimeRecordApi.listChildren(entityCode, recordId, child).then((res) => {
              if (res.code === 200) setChildRows((prev) => ({ ...prev, [c.fieldCode]: res.data.map((r, i) => ({ ...(r as Record<string, unknown>), __rowKey: `r${i}` })) }));
            });
          }
        }
      }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityCode, recordId]);

  const state = useMemo(
    () => linkageState(data, linkageRules, ctx, baseRequiredOf(fields), baseStatesOf(form)),
    [data, linkageRules, fields, form, ctx],
  );

  const setValue = (code: string, value: unknown) => setData((prev) => ({ ...prev, [code]: value }));

  const renderControl = (c: Control) => {
    const s: FieldUiState = state[c.fieldCode] ?? { visible: true, required: !!fieldMap[c.fieldCode]?.required, readonly: false, disabled: false, notice: '', noticeKey: '' };
    // 可见性走同源 fieldVisible：联动 then.tier 档位门控在运行时同样生效（预览同函数）
    if (!fieldVisible(s, ctx)) return null;
    const f = fieldMap[c.fieldCode];
    const ro = readOnly || s.readonly || !!c.readonly;
    const label = c.labelOverride ?? f?.displayName ?? c.fieldCode;
    const required = s.required;
    // 内联 notice 优先于 noticeKey（协议口径；key 无运行时词表时至少可见 key 本体，不静默丢弃）
    const notice = s.notice || s.noticeKey || '';
    const type = (c.controlKind ?? f?.type ?? 'STRING').toUpperCase();
    const control = (() => {
      if (type === 'CHILD_TABLE') {
        const child = (c.props as { childEntity?: string; columns?: string[] } | undefined)?.childEntity;
        const cols = (c.props as { columns?: string[] } | undefined)?.columns ?? [];
        const rows = childRows[c.fieldCode] ?? [];
        const columns: ColumnsType<Record<string, unknown>> = cols.map((k) => ({
          title: fieldMap[k]?.displayName ?? k, dataIndex: k, key: k,
          render: (v: unknown, r: Record<string, unknown>) => ro ? String(v ?? '') : (
            <Input size="small" defaultValue={v as string} onBlur={(e) => updateChild(c.fieldCode, r.__rowKey as string, k, e.target.value)} />
          ),
        }));
        if (!ro) columns.push({ title: '操作', key: '__op', render: (_v, r) => <Button size="small" danger type="link" onClick={() => removeChild(c.fieldCode, r.__rowKey as string)}>删除</Button> });
        return (
          <div>
            <Table rowKey="__rowKey" size="small" pagination={false} columns={columns} dataSource={rows} />
            {!ro && <Button size="small" style={{ marginTop: 8 }} onClick={() => addChild(c.fieldCode, cols)}>+ 添加明细行</Button>}
            {!child && <Text type="secondary">（未声明 childEntity）</Text>}
          </div>
        );
      }
      if (type === 'ATTACHMENT') {
        const files = Array.isArray(data[c.fieldCode]) ? (data[c.fieldCode] as { name?: string }[]) : [];
        return (
          <div>
            {files.length === 0 && <Text type="secondary">暂无附件</Text>}
            {files.map((a, i) => <div key={i}>📎 {a.name}{!ro && <Button size="small" type="link" danger onClick={() => { const n = [...files]; n.splice(i, 1); setValue(c.fieldCode, n); }}>删除</Button>}</div>)}
            {!ro && <Button size="small" style={{ marginTop: 4 }} onClick={() => { const name = window.prompt('附件名（真上传另立项）'); if (name) setValue(c.fieldCode, [...files, { name }]); }}>+ 添加附件</Button>}
          </div>
        );
      }
      if (type === 'REFERENCE') {
        const opts = referenceOptions[c.fieldCode] ?? [];
        return <Select allowClear disabled={ro} style={{ width: '100%' }} options={opts} value={data[c.fieldCode] ? String(data[c.fieldCode]) : undefined}
          onChange={(v) => setValue(c.fieldCode, v)} placeholder={opts.length ? '请选择' : '候选加载中…'} />;
      }
      if (type === 'ENUM') {
        const opts = parseEnumValues(f?.enumValues).map((o) => ({ value: o.code, label: `${o.label}（${o.code}）` }));
        return <Select disabled={ro} style={{ width: '100%' }} options={opts} value={data[c.fieldCode] as string} onChange={(v) => setValue(c.fieldCode, v)} placeholder={opts.length ? '请选择' : '该枚举未配置码表'} />;
      }
      if (type === 'DATE') return <DatePicker disabled={ro} style={{ width: '100%' }} value={data[c.fieldCode] ? dayjs(data[c.fieldCode] as string) : null} onChange={(d) => setValue(c.fieldCode, d ? d.format('YYYY-MM-DD') : '')} />;
      if (type === 'DATETIME') return <DatePicker showTime disabled={ro} style={{ width: '100%' }} value={data[c.fieldCode] ? dayjs(data[c.fieldCode] as string) : null} onChange={(d) => setValue(c.fieldCode, d ? d.format('YYYY-MM-DD HH:mm:ss') : '')} />;
      if (type === 'INTEGER' || type === 'LONG') return <InputNumber disabled={ro} style={{ width: '100%' }} value={data[c.fieldCode] as number} onChange={(v) => setValue(c.fieldCode, v)} />;
      if (type === 'DECIMAL' || type === 'NUMBER' || type === 'MONEY') return <InputNumber disabled={ro} step={0.01} style={{ width: '100%' }} value={data[c.fieldCode] as number} onChange={(v) => setValue(c.fieldCode, v)} />;
      if (type === 'TEXT') return <Input.TextArea disabled={ro} rows={3} value={data[c.fieldCode] as string} onChange={(e) => setValue(c.fieldCode, e.target.value)} placeholder={c.placeholder} />;
      return <Input disabled={ro} value={data[c.fieldCode] as string} onChange={(e) => setValue(c.fieldCode, e.target.value)} placeholder={c.placeholder} />;
    })();

    return (
      <Form.Item label={label} required={required} extra={f ? <Text type="secondary" style={{ fontSize: 12 }}>{f.code} · {f.type}{f.required ? ' · 必填' : ''}</Text> : undefined}>
        {type === 'BOOLEAN' ? <Switch disabled={ro} checked={!!data[c.fieldCode]} onChange={(v) => setValue(c.fieldCode, v)} /> : control}
        {notice && <Alert type="warning" showIcon style={{ marginTop: 4 }} message={notice} />}
      </Form.Item>
    );
  };

  const updateChild = (pk: string, rowKey: string, k: string, v: string) =>
    setChildRows((prev) => ({ ...prev, [pk]: (prev[pk] ?? []).map((r) => (r.__rowKey === rowKey ? { ...r, [k]: v } : r)) }));
  const addChild = (pk: string, cols: string[]) =>
    setChildRows((prev) => ({ ...prev, [pk]: [...(prev[pk] ?? []), { ...Object.fromEntries(cols.map((c) => [c, ''])), __rowKey: `r${Date.now()}${Math.random()}` }] }));
  const removeChild = (pk: string, rowKey: string) =>
    setChildRows((prev) => ({ ...prev, [pk]: (prev[pk] ?? []).filter((r) => r.__rowKey !== rowKey) }));

  const handleSubmit = async () => {
    const r = runSubmit(data, submitRules, linkageRules, ctx, baseRequiredOf(fields));
    setSubmitMsg(r);
    if (r.errors.length) return;
    if (r.warnings.length) {
      const ok = window.confirm(`有 ${r.warnings.length} 条警告，仍要保存？\n${r.warnings.join('\n')}`);
      if (!ok) return;
    }
    setSaving(true);
    try {
      // 子表整组替换（存在父记录时）
      if (recordId) {
        for (const c of form?.sections.flatMap((s) => s.controls) ?? []) {
          if (c.controlKind === 'CHILD_TABLE') {
            const child = (c.props as { childEntity?: string } | undefined)?.childEntity;
            if (child) await runtimeRecordApi.replaceChildren(entityCode, recordId, child, (childRows[c.fieldCode] ?? []) as never);
          }
        }
      }
      await onSubmit?.(data);
      if (!quiet) message.success('已保存');
    } catch {
      message.error('提交失败');
    } finally {
      setSaving(false);
    }
  };

  const visibleActions = resolveVisibleActions(data, actionBar, ctx);

  if (!form || !form.sections?.length) {
    return <Alert type="info" showIcon message="该 schema 未定义表单视图（views.form.sections）" />;
  }

  return (
    <div style={{ maxWidth: 720 }}>
      {/* 页头关键信息条（v1.2 §13.2；引用字段必须存在于某分区控件，悬空由 lint ERROR） */}
      <PageHeaderBar codes={form.pageHeader ?? []} data={data} fields={fields} />

      {form.sections.map((sec) => {
        // split-left-40/60：按当前可见控件顺序对半切分（与预览同口径：顺序即分栏）
        const layout = sec.layout ?? 'single';
        const split = layout === 'split-left-40' || layout === 'split-left-60';
        const visibleCtrls = sec.controls.filter((c) => fieldVisible(state[c.fieldCode], ctx));
        const body = (ctrl: Control) => {
          const f = fieldMap[ctrl.fieldCode];
          if (!f && !ctrl.virtual) {
            return <Alert type="warning" showIcon style={{ marginBottom: 8 }}
              message={`控件引用的字段 ${ctrl.fieldCode} 在结构里不存在（UI-REF-01）`} />;
          }
          if (f && !isRenderSupported(f) && ctrl.controlKind === undefined) {
            return <Alert type="warning" showIcon style={{ marginBottom: 8 }}
              message={`字段「${f.displayName}」的类型 ${f.type} 暂不支持自动生成控件`} />;
          }
          return <div>{renderControl(ctrl)}</div>;
        };
        const title = sec.title ?? sec.id;
        if (split && visibleCtrls.length >= 2) {
          const mid = Math.ceil(visibleCtrls.length / 2);
          const lPct = layout === 'split-left-40' ? '38%' : '58%';
          const rPct = layout === 'split-left-40' ? '58%' : '38%';
          return (
            <div key={sec.id} style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <Text strong>{title}</Text>
                {sec.kind && <span style={{ fontSize: 11, color: '#6b7280', border: '1px solid #eef0f5', borderRadius: 6, padding: '0 6px' }}>{sec.kind}</span>}
                <span style={{ fontSize: 11, color: '#9ca3af' }}>左右分栏 {layout === 'split-left-40' ? '40/60' : '60/40'}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: `${lPct} ${rPct}`, gap: 16, alignItems: 'start' }}>
                <div>{visibleCtrls.slice(0, mid).map((c, i) => <div key={`${c.fieldCode}-${i}`}>{body(c)}</div>)}</div>
                <div>{visibleCtrls.slice(mid).map((c, i) => <div key={`${c.fieldCode}-${i}`}>{body(c)}</div>)}</div>
              </div>
            </div>
          );
        }
        return (
          <CardSection key={sec.id} title={title} kind={sec.kind}>
            {visibleCtrls.map((c, i) => <div key={`${c.fieldCode}-${i}`}>{body(c)}</div>)}
          </CardSection>
        );
      })}

      {submitMsg && (submitMsg.errors.length > 0 || submitMsg.warnings.length > 0) && (
        <div style={{ margin: '12px 0' }}>
          {submitMsg.errors.map((e, i) => <Alert key={`e${i}`} type="error" showIcon style={{ marginBottom: 4 }} message={e} />)}
          {submitMsg.warnings.map((w, i) => <Alert key={`w${i}`} type="warning" showIcon style={{ marginBottom: 4 }} message={w} />)}
        </div>
      )}

      {!readOnly && (
        <Space style={{ marginTop: 12 }}>
          <Button type="primary" loading={saving} onClick={handleSubmit}>提交</Button>
          {visibleActions.map((a) => (
            <Button key={a.actionCode} danger={a.actionCode === 'reject'}>{a.actionCode}</Button>
          ))}
        </Space>
      )}

      {/* 常驻合规条（footer 配置了才渲染；AI 标识/免责声明/模拟数据标注） */}
      {schema.footer && <SchemaFooterBar footer={schema.footer} />}
    </div>
  );
};

export default BoneDynamicForm;
