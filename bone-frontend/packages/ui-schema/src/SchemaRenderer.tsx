/**
 * 协议驱动界面运行时渲染器（design / runtime 共享单源）。
 *
 * 读 BoneUiSchema + 数据，渲染 form/detail/list 视图，复用 `@bone/ui-schema` 引擎内核的
 * linkageState / fieldVisible / resolveVisibleActions（与设计器预览同源行为）。
 * 不内联业务标签：列/分区标题一律由 schema 的 labelOverride 或 fieldCode 规整得到；
 * 合规文案走 schema.footer（disclaimer + disclaimerKey），不内联散落。
 *
 * 与设计器预览（SchemaPreview）的差异：本组件只渲染「运行时显示」，不带场景模拟/角色栏/
 * 主题 css——纯展示。action 通道接线（写/动）由业务层经 runtime action 通道承担，不在此处。
 */
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Spin,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { useEffect, useMemo, type ReactElement } from 'react';
import {
  fieldVisible,
  linkageState,
  normalizeUiSchema,
  resolveVisibleActions,
  type BoneUiSchema,
  type Control,
  type ControlKind,
  type FieldUiState,
  type Section,
  type UiContext,
} from './engine';

const { Text, Paragraph } = Typography;

function prettify(key: string): string {
  return key
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim();
}

function labelOf(c: Control): string {
  return c.labelOverride ?? c.labelKey ?? prettify(c.fieldCode);
}

type Row = Record<string, unknown>;

/**
 * 单元格只读格式化。金额/枚举由 controlKind 驱动（不再按字段名嗅探业务命名），
 * 其余默认：长值可复制、空值占位「-」。
 */
function formatCell(v: unknown, controlKind?: ControlKind): ReactElement {
  if (v == null || v === '') return <Text type="secondary">-</Text>;
  if (controlKind === 'MONEY' && typeof v === 'number') {
    return <Text>{`¥${v.toFixed(2)}`}</Text>;
  }
  if (controlKind === 'ENUM') return <Tag color="blue">{String(v)}</Tag>;
  const text = String(v);
  if (text.length > 24) return <Text copyable ellipsis>{text}</Text>;
  return <Text>{text}</Text>;
}

/** 单控件渲染（只读展示形态）。 */
function FieldControl({ control, value }: { control: Control; value: unknown }): ReactElement {
  const kind = control.controlKind ?? 'INPUT';
  const readonly = control.readonly;

  if (kind === 'CHILD_TABLE') {
    const rawCols = Array.isArray(control.props?.columns)
      ? (control.props!.columns as Array<string | { fieldCode: string; controlKind?: ControlKind }>)
      : [];
    const cols = rawCols.map((c) =>
      typeof c === 'string' ? { fieldCode: c, controlKind: undefined as ControlKind | undefined } : c,
    );
    const rows = Array.isArray(value) ? (value as Row[]) : [];
    return (
      <Table<Row>
        rowKey={(_, i) => String(i)}
        size="small"
        pagination={false}
        scroll={{ x: 1100 }}
        dataSource={rows}
        columns={cols.map((col) => ({
          title: prettify(col.fieldCode),
          dataIndex: col.fieldCode,
          key: col.fieldCode,
          render: (v: unknown) => formatCell(v, col.controlKind),
        }))}
      />
    );
  }

  if (kind === 'TIMELINE') {
    const arr = Array.isArray(value) ? (value as unknown[]) : [];
    if (!arr.length) return <Text type="secondary">暂无轨迹</Text>;
    return (
      <Timeline
        items={arr.map((x, i) => ({
          children: (
            <Text type="secondary">
              {typeof x === 'object' ? <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{JSON.stringify(x, null, 2)}</pre> : String(x)}
            </Text>
          ),
          key: i,
        }))}
      />
    );
  }

  if (kind === 'STAT' || kind === 'COMPUTED') {
    return <Statistic value={value == null ? '—' : String(value)} valueStyle={{ fontSize: 16 }} />;
  }

  if (kind === 'MONEY') {
    return (
      <Statistic
        prefix="¥"
        precision={2}
        value={typeof value === 'number' ? value : Number(value) || 0}
        valueStyle={{ fontSize: 16 }}
      />
    );
  }

  if (kind === 'ALERT') {
    return <Alert type="warning" showIcon message={String(value ?? control.props?.warningText ?? '')} />;
  }

  if (kind === 'TEXTAREA') {
    return (
      <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }} ellipsis={{ rows: 6, expandable: true, symbol: '展开' }}>
        {value == null ? '' : String(value)}
      </Paragraph>
    );
  }

  if (kind === 'SWITCH') {
    return <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>;
  }

  // INPUT / ENUM / REFERENCE / NUMBER / DATE / 默认：只读文本（长值可复制）
  if (readonly || kind !== 'INPUT') {
    if (value == null || value === '') return <Text type="secondary">-</Text>;
    return <Text copyable={String(value).length > 24} ellipsis>{String(value)}</Text>;
  }
  return <Text>{value == null ? '' : String(value)}</Text>;
}

function SectionView({
  sec,
  data,
  states,
  ctx,
}: {
  sec: Section;
  data: Row;
  states: Record<string, FieldUiState>;
  ctx: UiContext;
}): ReactElement | null {
  const ctrls = (sec.controls ?? []).filter((c) => fieldVisible(states[c.fieldCode], ctx));
  if (!ctrls.length) return null;
  const title = sec.title ?? sec.titleKey ?? sec.id;

  if (sec.kind === 'dashboard') {
    return (
      <Card size="small" title={title} style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
          {ctrls.map((c) => (
            <FieldControl key={c.fieldCode} control={c} value={data[c.fieldCode]} />
          ))}
        </div>
      </Card>
    );
  }

  if (sec.kind === 'timeline') {
    return (
      <Card size="small" title={title} style={{ marginBottom: 16 }}>
        <Timeline
          items={ctrls.map((c, i) => ({
            children: <FieldControl key={c.fieldCode} control={c} value={data[c.fieldCode]} />,
            key: i,
          }))}
        />
      </Card>
    );
  }

  return (
    <Card size="small" title={title} style={{ marginBottom: 16 }}>
      <Descriptions column={1} bordered size="small">
        {ctrls.map((c) => (
          <Descriptions.Item key={c.fieldCode} label={labelOf(c)}>
            <FieldControl control={c} value={data[c.fieldCode]} />
          </Descriptions.Item>
        ))}
      </Descriptions>
    </Card>
  );
}

export interface SchemaRendererProps {
  schema: BoneUiSchema;
  /** 单记录对象（form/detail）或记录数组（list）。 */
  data: Row | Row[];
  viewMode?: 'form' | 'detail' | 'list';
  ctx?: UiContext;
  loading?: boolean;
  /** actionBar 命中后回调（无则不渲染动作条）。 */
  onAction?: (actionCode: string) => void;
  actionsDisabled?: boolean;
  /** 归一化/校验告警回调（加载期非法 schema 上抛，便于宿主 fail-fast / 上报）。 */
  onError?: (errors: string[]) => void;
}

export function SchemaRenderer({
  schema,
  data,
  viewMode = 'form',
  ctx = {},
  loading,
  onAction,
  actionsDisabled,
  onError,
}: SchemaRendererProps): ReactElement {
  if (viewMode === 'list') {
    const rows = Array.isArray(data) ? data : [];
    const cols = schema.views.list?.columns?.filter((c) => c.visible !== false) ?? [];
    return (
      <Table<Row>
        loading={loading}
        rowKey={(_, i) => String(i)}
        size="small"
        dataSource={rows}
        columns={cols.map((c) => ({
          title: prettify(c.fieldCode),
          dataIndex: c.fieldCode,
          key: c.fieldCode,
          width: c.width,
          fixed: c.pinned,
          render: (v: unknown) => formatCell(v, c.controlKind),
        }))}
      />
    );
  }

  // 边界归一化：views.*.sections 的分区 code 引用 → 内联对象（单一规范形态，fail-fast 暴露非法 ref）
  const normalized = useMemo(() => normalizeUiSchema(schema), [schema]);
  const normSchema = normalized.schema;
  // 告警移出 render 体（render 应纯函数）：开发期暴露畸形 schema，并上抛宿主处理
  useEffect(() => {
    if (normalized.errors.length) {
      console.warn('[SchemaRenderer] schema 归一化告警:', normalized.errors);
      onError?.(normalized.errors);
    }
  }, [normalized, onError]);

  const view = viewMode === 'detail' ? normSchema.views.detail : normSchema.views.form;
  const sections = view?.sections ?? [];
  const baseStates = linkageState({}, normSchema.linkageRules ?? [], ctx);
  const states = linkageState(
    Array.isArray(data) ? (data[0] ?? {}) : data,
    normSchema.linkageRules ?? [],
    ctx,
    {},
    baseStates as Record<string, Partial<FieldUiState>>,
  );
  const record = Array.isArray(data) ? (data[0] ?? {}) : data;

  const visibleActions = resolveVisibleActions(record, normSchema.actionBar ?? [], ctx);

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (!loading && sections.length === 0 && visibleActions.length === 0) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Empty description="暂无数据" />
      </div>
    );
  }

  return (
    <div>
      {visibleActions.length > 0 && onAction && (
        <div style={{ marginBottom: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {visibleActions.map((a, i) => (
            <Button
              key={a.actionCode}
              type={i === 0 ? 'primary' : 'default'}
              disabled={actionsDisabled}
              onClick={() => !actionsDisabled && onAction(a.actionCode)}
            >
              {a.label ?? a.labelKey ?? a.actionCode}
            </Button>
          ))}
        </div>
      )}
      {sections.map((sec, i) => (
        <SectionView key={sec.id ?? i} sec={sec} data={record} states={states} ctx={ctx} />
      ))}
      {normSchema.footer && (
        <div style={{ marginTop: 8, color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
          {normSchema.footer.aiBadge && <Tag color="purple" style={{ marginRight: 6 }}>AI</Tag>}
          <span>{normSchema.footer.disclaimer ?? ''}</span>
        </div>
      )}
    </div>
  );
}
