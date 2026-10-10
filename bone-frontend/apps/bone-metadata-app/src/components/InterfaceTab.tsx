import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Empty, Form, Input, InputNumber, Segmented, Select, Space, Switch,
  Table, Tag, Tooltip, Typography, DatePicker, message,
} from 'antd';
import {
  AimOutlined, ArrowDownOutlined, ArrowUpOutlined, EyeOutlined,
  PlusOutlined, ReloadOutlined, SettingOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MetaField } from '../types';
import { FIELD_TYPE_MAP, parseEnumValues, RUNTIME_READONLY_FIELDS } from '../types';
import {
  clearOverlay, defaultOverlay, isEnumField, isRenderSupported, loadOverlay,
  projectColumns, reorderOverlay, saveOverlay, tableSizeOf, type UiOverlay,
} from '../utils/columnProjection';

const { Text } = Typography;

/**
 * 界面 Tab（W2 · UIF-005 MVP / UID-10/11/12）。
 *
 * 关键：预览列与运行时数据列都由同一个 `projectColumns(fields, overlay)` 投影得到，
 * 因此「界面预览列 ≡ 发布后数据列」在结构上成立（PRD v0.32 §0.6.3）。
 * MVP 覆盖存 localStorage（PRD §12.2 D2）；v1.1 服务端化时只需替换底层读写。
 *
 * 明确不做：拖拽画布 / 组件市场 / 多页面路由设计器（PRD §3.2 Out of Scope）。
 */
interface Props {
  entityCode: string;
  fields: MetaField[];
  /** 空态 CTA「去结构添加第一个字段」：由 EntityDetail 注入，避免反向依赖路由 */
  onAddField?: () => void;
  /** UID-11：定位某列对应的字段 —— 跳到「结构」Tab 并高亮该字段 */
  onLocateField?: (code: string) => void;
}

type ViewMode = 'list' | 'form';

const InterfaceTab: React.FC<Props> = ({ entityCode, fields, onAddField, onLocateField }) => {
  // 仅用户可见的数据列参与投影（排除系统只读列，与数据 Tab 口径一致）
  const baseFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

  const [mode, setMode] = useState<'config' | 'preview'>('config');
  const [view, setView] = useState<ViewMode>('list');
  const [overlay, setOverlay] = useState<UiOverlay | null>(null);

  const defaultOv = useMemo(() => defaultOverlay(baseFields), [baseFields]);

  useEffect(() => {
    setOverlay(loadOverlay(entityCode) ?? defaultOverlay(baseFields));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityCode]);

  const projected = useMemo(() => projectColumns(baseFields, overlay), [baseFields, overlay]);
  const projectedCodes = useMemo(() => projected.map((f) => f.code), [projected]);

  const dirty = useMemo(() => {
    if (!overlay) return false;
    return JSON.stringify(overlay.overrides) !== JSON.stringify(defaultOv.overrides)
      || (overlay.density ?? 'comfortable') !== 'comfortable';
  }, [overlay, defaultOv]);

  const unsupported = useMemo(() => baseFields.filter((f) => !isRenderSupported(f)), [baseFields]);

  const persist = (next: UiOverlay) => {
    setOverlay(next);
    saveOverlay(entityCode, next);
  };

  const toggleVisible = (code: string, visible: boolean) => {
    if (!overlay) return;
    persist({ ...overlay, overrides: { ...overlay.overrides, [code]: { ...overlay.overrides[code], visible } } });
  };

  const rename = (code: string, displayName: string) => {
    if (!overlay) return;
    persist({ ...overlay, overrides: { ...overlay.overrides, [code]: { ...overlay.overrides[code], displayName } } });
  };

  const move = (code: string, direction: 'up' | 'down') => {
    if (!overlay) return;
    persist(reorderOverlay(overlay, projectedCodes, code, direction));
  };

  const restoreDefault = () => {
    clearOverlay(entityCode);
    setOverlay(defaultOverlay(baseFields));
    message.success('已恢复默认显示');
  };

  /** 未支持渲染的类型：显式降级提示（PRD §5.5 禁静默渲染空） */
  const renderUnsupported = (field: MetaField) => (
    <Alert
      type="warning"
            showIcon
      message={`字段「${field.displayName || field.code}」的类型 ${field.type} 暂不支持自动生成界面控件`}
      description="该字段仍会随结构发布到运行时；如需界面支持请使用受支持类型（STRING/TEXT/INTEGER/LONG/DECIMAL/BOOLEAN/DATE/DATETIME/ENUM）。"
      style={{ marginBottom: 8 }}
    />
  );

  /** 按元数据类型渲染预览控件（与数据 Tab 表单同源；此处仅为预览，禁用输入） */
  const renderPreviewControl = (field: MetaField) => {
    const type = (field.type || 'STRING').toUpperCase();
    if (type === 'BOOLEAN') return <Switch disabled />;
    if (type === 'INTEGER' || type === 'LONG') return <InputNumber disabled style={{ width: '100%' }} />;
    if (type === 'DECIMAL') return <InputNumber disabled step={0.01} style={{ width: '100%' }} />;
    if (type === 'DATE') return <DatePicker disabled style={{ width: '100%' }} />;
    if (type === 'DATETIME') return <DatePicker disabled showTime style={{ width: '100%' }} />;
    if (isEnumField(field)) {
      const opts = parseEnumValues(field.enumValues).map((o) => ({ value: o.code, label: `${o.label}（${o.code}）` }));
      return <Select disabled options={opts} placeholder={opts.length ? '请选择' : '该枚举未配置码表'} style={{ width: '100%' }} />;
    }
    if (type === 'TEXT') return <Input.TextArea disabled rows={3} />;
    return <Input disabled />;
  };

  const configColumns: ColumnsType<MetaField> = [
    { title: '字段', dataIndex: 'displayName', width: 150, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 130, ellipsis: true },
    {
      title: '类型', dataIndex: 'type', width: 90,
      render: (t: string) => <Tag color={FIELD_TYPE_MAP[t]?.color ?? 'default'}>{t}</Tag>,
    },
    {
      title: '显示',
      key: 'visible',
      width: 70,
      render: (_, f) => (
        <Switch
          size="small"
          checked={overlay?.overrides[f.code]?.visible !== false}
          onChange={(v) => toggleVisible(f.code, v)}
        />
      ),
    },
    {
      title: '覆盖显示名',
      key: 'displayNameOverride',
      render: (_, f) => (
        <Input
          size="small"
          defaultValue={overlay?.overrides[f.code]?.displayName ?? f.displayName}
          placeholder={f.displayName}
          onChange={(e) => rename(f.code, e.target.value)}
        />
      ),
    },
    {
      title: '列序',
      key: 'order',
      width: 90,
      render: (_, f) => {
        const idx = projectedCodes.indexOf(f.code);
        return (
          <Space size={2}>
            <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={idx <= 0} onClick={() => move(f.code, 'up')} />
            <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={idx < 0 || idx >= projectedCodes.length - 1} onClick={() => move(f.code, 'down')} />
          </Space>
        );
      },
    },
    {
      // UID-11：从「界面」的某一列反查到「结构」里的字段定义（双向联动的一半）
      title: '定位',
      key: 'locate',
      width: 70,
      render: (_: unknown, f: MetaField) =>
        onLocateField ? (
          <Tooltip title="跳到「结构」并高亮该字段">
            <Button size="small" type="link" icon={<AimOutlined />} onClick={() => onLocateField(f.code)} />
          </Tooltip>
        ) : null,
    },
  ];

  const listPreviewColumns: ColumnsType<MetaField> = [
    { title: '显示名', dataIndex: 'displayName', width: 160, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'type', width: 90, render: (t: string) => <Tag color={FIELD_TYPE_MAP[t]?.color ?? 'default'}>{t}</Tag> },
  ];

  // 空态：无字段 ⇒ 单 CTA 引导去结构添加第一个字段（融合方案 §3.4.3）
  if (baseFields.length === 0) {
    return (
      <Empty
        description="还没有字段，界面会随结构自动生成"
        style={{ padding: '32px 0' }}
      >
        {onAddField && (
          <Button type="primary" icon={<PlusOutlined />} onClick={onAddField}>
            去结构添加第一个字段
          </Button>
        )}
      </Empty>
    );
  }

  return (
    <div>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Segmented
            value={mode}
            onChange={(v) => setMode(v as 'config' | 'preview')}
            options={[
              { value: 'config', label: <Space size={4}><SettingOutlined /><span>配置</span></Space> },
              { value: 'preview', label: <Space size={4}><EyeOutlined /><span>预览</span></Space> },
            ]}
          />
          <Segmented
            value={view}
            onChange={(v) => setView(v as ViewMode)}
            options={[{ value: 'list', label: '列表' }, { value: 'form', label: '表单' }]}
          />
          <Tag color="blue">自动生成 · 可微调</Tag>
          {mode === 'preview' && projected.length === 0 && <Tag color="orange">当前没有可见列</Tag>}
        </Space>
        <Space wrap>
          <Text type="secondary" style={{ fontSize: 12 }}>密度</Text>
          <Segmented
            size="small"
            value={overlay?.density ?? 'comfortable'}
            onChange={(v) => persist({ ...overlay!, density: v as 'comfortable' | 'compact' })}
            options={[{ value: 'comfortable', label: '标准' }, { value: 'compact', label: '紧凑' }]}
          />
          <Button icon={<ReloadOutlined />} size="small" onClick={restoreDefault} disabled={!dirty}>
            恢复默认显示
          </Button>
          {dirty ? <Tag color="orange">界面配置已调整</Tag> : <Tag color="green">与默认一致</Tag>}
        </Space>
      </Space>

      {unsupported.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`有 ${unsupported.length} 个字段的类型暂不支持自动生成控件`}
          description={<div>{unsupported.map((f) => <div key={f.id}>· {f.displayName}（{f.code}）：{f.type}</div>)}</div>}
        />
      )}

      {mode === 'config' ? (
        <>
          <div style={{ marginBottom: 8 }}><Text strong>列配置</Text><Text type="secondary">（显隐 / 显示名 / 列序{onLocateField ? '；点击任意行可跳到「结构」高亮该字段' : ''}）</Text></div>
          <Table
            rowKey="code"
            size="small"
            pagination={false}
            columns={configColumns}
            dataSource={baseFields}
            scroll={{ x: 'max-content' }}
            /* UID-11：整行可点，等价于右侧「定位」按钮（大目标更好点） */
            onRow={(f) => ({
              onClick: () => onLocateField?.(f.code),
              style: { cursor: onLocateField ? 'pointer' : 'default' },
            })}
          />
          <div style={{ margin: '12px 0 8px' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              以上调整即时生效于「预览」以及发布后的运行时数据表（两者共用同一套列投影）。
              MVP 覆盖保存在浏览器本地（PRD §12.2 D2），团队级共享将在 v1.1 服务端化。
            </Text>
          </div>
        </>
      ) : view === 'list' ? (
        <Table
          rowKey="code"
          size={tableSizeOf(overlay?.density)}
          pagination={false}
          columns={listPreviewColumns}
          dataSource={projected}
          locale={{ emptyText: '当前没有可见列，请在「配置」中开启显示' }}
          scroll={{ x: 'max-content' }}
          onRow={(f) => ({
            onClick: () => onLocateField?.(f.code),
            style: { cursor: onLocateField ? 'pointer' : 'default' },
          })}
        />
      ) : (
        <div style={{ maxWidth: 560 }}>
          <Form layout="vertical" disabled>
            {projected.length === 0 ? (
              <Empty description="当前没有可见字段，请在「配置」中开启显示" />
            ) : (
              projected.map((f) => (
                <div key={f.id}>
                  {isRenderSupported(f) ? (
                    <Form.Item label={f.displayName} extra={<Text type="secondary" style={{ fontSize: 12 }}>{f.code} · {f.type}{f.required ? ' · 必填' : ''}</Text>}>
                      {renderPreviewControl(f)}
                    </Form.Item>
                  ) : (
                    renderUnsupported(f)
                  )}
                </div>
              ))
            )}
          </Form>
        </div>
      )}
    </div>
  );
};

export default InterfaceTab;
