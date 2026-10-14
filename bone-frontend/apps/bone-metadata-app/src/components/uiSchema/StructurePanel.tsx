/**
 * 结构配置面板：分区 / 控件 / 字段调色板 / 控件属性。
 *
 * 交互范式（对标 Salesforce Lightning App Builder、Retool）：
 * - 左侧画布式的分区卡片列表，控件以行呈现，可直接拖拽重排（分区内 + 跨分区）；
 * - 未使用的实体字段在底部「字段库」待选，点一下即落到当前分区（避免手填 code 打错字）；
 * - 点选控件就地展开属性卡，不弹窗（减少一次上下文切换）。
 */
import { useState } from 'react';
import {
  Badge, Button, Empty, Input, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, DragOutlined,
  EyeInvisibleOutlined, PlusOutlined, SettingOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema, Control, ControlKind } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { CONTROL_KINDS, KIND_GROUPS, KIND_MAP } from './constants';
import {
  addControl, addSection, moveControl, moveControlAcross, moveSection,
  removeControl, removeSection, unusedFields, updateControl, updateSection, viewOf,
} from './schemaOps';
import { dragSort2 } from './drag';
import type { ViewKey } from './schemaOps';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  /** 视图：form=录入/编辑表单，detail=详情视图（同结构，独立配置） */
  view: ViewKey;
  onViewChange: (v: 'form' | 'detail') => void;
  /** 高亮定位（来自自检点击） */
  focus?: { si?: number; ci?: number };
}

/** 单个控件的属性展开卡 */
function ControlInspector({
  control, fields, onPatch,
}: {
  control: Control;
  fields: MetaField[];
  onPatch: (p: Partial<Control>) => void;
}) {
  const field = fields.find((f) => f.code === control.fieldCode);
  return (
    <div style={{
      background: '#fafbfd', border: '1px solid #f0f0f0', borderRadius: 8,
      padding: 10, marginTop: 6, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8,
    }}>
      <div>
        <Text type="secondary" style={{ fontSize: 11 }}>显示名覆盖</Text>
        <Input
          size="small" placeholder={field?.displayName ?? '沿用元数据字段名'}
          value={control.labelOverride ?? ''}
          onChange={(e) => onPatch({ labelOverride: e.target.value || undefined })}
        />
      </div>
      <div>
        <Text type="secondary" style={{ fontSize: 11 }}>占位提示</Text>
        <Input
          size="small" placeholder="placeholder"
          value={control.placeholder ?? ''}
          onChange={(e) => onPatch({ placeholder: e.target.value || undefined })}
        />
      </div>
      <div style={{ gridColumn: '1 / -1' }}>
        <Text type="secondary" style={{ fontSize: 11 }}>栅格宽度（1-24，24=整行）</Text>
        <Select
          size="small" style={{ width: 120 }} allowClear placeholder="默认"
          value={(control.props?.span as number | undefined) ?? undefined}
          options={[24, 12, 8, 6].map((n) => ({ value: n, label: n === 24 ? '整行' : `1/${24 / n}` }))}
          onChange={(v) => onPatch({ props: { ...control.props, ...(v ? { span: v } : { span: undefined }) } })}
        />
      </div>
      <Space size={12} style={{ gridColumn: '1 / -1' }}>
        <Space size={4}>
          <Switch
            size="small" checked={control.visible === false}
            onChange={(v) => onPatch({ visible: v ? false : undefined })}
          />
          <Text style={{ fontSize: 12 }}>默认隐藏</Text>
        </Space>
        <Space size={4}>
          <Switch size="small" checked={!!control.readonly} onChange={(v) => onPatch({ readonly: v || undefined })} />
          <Text style={{ fontSize: 12 }}>只读</Text>
        </Space>
        {field?.required && <Tag color="red" style={{ fontSize: 11 }}>元数据必填</Tag>}
      </Space>
    </div>
  );
}

export default function StructurePanel({ schema, onChange, fields, view, onViewChange, focus }: Props): React.ReactElement {
  const [sel, setSel] = useState<{ si: number; ci: number } | null>(null);
  const [dragOver, setDragOver] = useState<string | null>(null);

  const sections = viewOf(schema, view).sections ?? [];
  const palette = unusedFields(schema, fields, view);

  const isFocused = (si: number, ci?: number) =>
    focus?.si === si && (ci === undefined || focus.ci === ci);

  return (
    <div>
      <Space style={{ marginBottom: 10 }} wrap>
        <Segmented
          size="small"
          value={view}
          onChange={(v) => onViewChange(v as 'form' | 'detail')}
          options={[{ value: 'form', label: '录入表单' }, { value: 'detail', label: '详情视图' }]}
        />
        <Text type="secondary" style={{ fontSize: 12 }}>
          {view === 'form' ? '新增/编辑时的布局' : '查看详情时的布局（与表单独立配置）'}
        </Text>
      </Space>

      {sections.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="还没有分区，先建一个承载控件"
          style={{ padding: '18px 0' }}
        />
      )}

      {sections.map((sec, si) => (
        <div
          key={sec.id ?? si}
          style={{
            border: `1px solid ${isFocused(si) ? '#1677ff' : '#f0f0f0'}`,
            borderRadius: 10, marginBottom: 10, background: '#fff',
            boxShadow: isFocused(si) ? '0 0 0 3px rgba(22,119,255,.12)' : '0 1px 2px rgba(16,24,40,.04)',
            transition: 'box-shadow .18s, border-color .18s',
          }}
        >
          {/* 分区头 */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 10px',
            borderBottom: '1px solid #f5f5f5', background: '#fafbfd', borderTopLeftRadius: 10, borderTopRightRadius: 10,
          }}>
            <Tooltip title="拖拽调整分区顺序">
              <span
                draggable
                onDragStart={(e) => { e.dataTransfer.setData('text/plain', `S${si}`); e.dataTransfer.effectAllowed = 'move'; }}
                onDragOver={(e) => { e.preventDefault(); setDragOver(`S${si}`); }}
                onDragLeave={() => setDragOver(null)}
                onDrop={(e) => {
                  e.preventDefault(); setDragOver(null);
                  const raw = e.dataTransfer.getData('text/plain');
                  if (raw.startsWith('S')) onChange(moveSection(schema, view, Number(raw.slice(1)), si));
                }}
                style={{ cursor: 'grab', color: '#bfbfbf', display: 'inline-flex' }}
              >
                <DragOutlined />
              </span>
            </Tooltip>
            <Input
              size="small" style={{ width: 110, fontWeight: 600 }} value={sec.id ?? ''}
              onChange={(e) => onChange(updateSection(schema, view, si, { id: e.target.value }))}
            />
            <Input
              size="small" style={{ flex: 1, maxWidth: 220, fontWeight: 600 }} placeholder="分区标题"
              value={sec.title ?? ''}
              onChange={(e) => onChange(updateSection(schema, view, si, { title: e.target.value }))}
            />
            <Tag style={{ fontSize: 11 }}>{sec.controls?.length ?? 0} 控件</Tag>
            <Space size={2}>
              <Tooltip title="上移">
                <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={si === 0}
                  onClick={() => onChange(moveSection(schema, view, si, si - 1))} />
              </Tooltip>
              <Tooltip title="下移">
                <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={si === sections.length - 1}
                  onClick={() => onChange(moveSection(schema, view, si, si + 1))} />
              </Tooltip>
              <Tooltip title="删除分区">
                <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => onChange(removeSection(schema, view, si))} />
              </Tooltip>
            </Space>
          </div>

          {/* 控件列表 */}
          <div
            style={{ padding: 8, background: dragOver === `C${si}` ? '#f0f7ff' : undefined, transition: 'background .15s' }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(`C${si}`); }}
            onDragLeave={() => setDragOver(null)}
            onDrop={(e) => {
              e.preventDefault(); setDragOver(null);
              const raw = e.dataTransfer.getData('text/plain');
              // 从字段库拖入：追加到末尾
              if (raw.startsWith('F:')) {
                const code = raw.slice(2);
                const f = fields.find((x) => x.code === code);
                onChange(addControl(schema, view, si, f));
              }
            }}
          >
            {(sec.controls ?? []).length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                空分区 —— 从下方「字段库」点选或拖入字段
              </Text>
            )}

            {(sec.controls ?? []).map((c, ci) => {
              const kind = KIND_MAP[c.controlKind ?? ''] ?? KIND_MAP.INPUT;
              const Icon = kind.icon;
              const selected = sel?.si === si && sel?.ci === ci;
              return (
                <div key={`${c.fieldCode}-${ci}`}>
                  <div
                    {...dragSort2(si, ci, (fs, fc, ts, tc) => {
                      onChange(fs === ts ? moveControl(schema, view, fs, fc, tc) : moveControlAcross(schema, view, fs, fc, ts, tc));
                    })}
                    onClick={() => setSel(selected ? null : { si, ci })}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 6, padding: '6px 8px',
                      border: `1px solid ${selected ? '#1677ff' : isFocused(si, ci) ? '#ffa940' : '#f0f0f0'}`,
                      borderRadius: 8, marginBottom: 5, background: selected ? '#f0f7ff' : '#fff',
                      cursor: 'pointer', transition: 'all .15s',
                    }}
                  >
                    <span style={{ color: '#bfbfbf', cursor: 'grab', display: 'inline-flex' }}><DragOutlined /></span>
                    <Icon />
                    <Text code style={{ fontSize: 12 }}>{c.fieldCode || '（未绑定）'}</Text>
                    <Select
                      size="small" style={{ width: 118 }} value={c.controlKind}
                      onChange={(v) => onChange(updateControl(schema, view, si, ci, { controlKind: v as ControlKind }))}
                      options={KIND_GROUPS.map((g) => ({
                        label: g,
                        options: CONTROL_KINDS.filter((k) => k.group === g)
                          .map((k) => ({ value: k.value, label: k.label })),
                      }))}
                      onClick={(e) => e.stopPropagation()}
                    />
                    {c.labelOverride && <Tag color="blue" style={{ fontSize: 11 }}>{c.labelOverride}</Tag>}
                    {c.visible === false && <Tooltip title="默认隐藏"><EyeInvisibleOutlined style={{ color: '#faad14' }} /></Tooltip>}
                    {c.readonly && <Tag style={{ fontSize: 11 }}>只读</Tag>}
                    <span style={{ flex: 1 }} />
                    <Tooltip title="控件属性">
                      <SettingOutlined
                        style={{ color: selected ? '#1677ff' : '#bfbfbf' }}
                        onClick={(e) => { e.stopPropagation(); setSel(selected ? null : { si, ci }); }}
                      />
                    </Tooltip>
                    <Tooltip title="移出分区">
                      <DeleteOutlined
                        style={{ color: '#ff7875' }}
                        onClick={(e) => { e.stopPropagation(); onChange(removeControl(schema, view, si, ci)); }}
                      />
                    </Tooltip>
                  </div>

                  {selected && (
                    <ControlInspector
                      control={c}
                      fields={fields}
                      onPatch={(p) => onChange(updateControl(schema, view, si, ci, p))}
                    />
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}

      <Button
        block size="small" icon={<PlusOutlined />} style={{ marginBottom: 12 }}
        onClick={() => onChange(addSection(schema, view))}
      >
        添加分区
      </Button>

      {/* 字段库 */}
      <div style={{ border: '1px dashed #d9d9d9', borderRadius: 10, padding: 10, background: '#fafbfd' }}>
        <Space style={{ marginBottom: 6 }}>
          <Text strong style={{ fontSize: 12 }}>字段库</Text>
          <Badge count={palette.length} style={{ backgroundColor: palette.length ? '#1677ff' : '#d9d9d9' }} />
          <Text type="secondary" style={{ fontSize: 11 }}>
            {palette.length ? '点选加入最后一个分区，或拖到目标分区' : '实体字段已全部使用'}
          </Text>
        </Space>
        {palette.length === 0 ? (
          <Text type="secondary" style={{ fontSize: 12 }}>无待用字段</Text>
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {palette.map((f) => (
              <Tooltip key={f.id} title={`${f.type}${f.required ? ' · 必填' : ''}`}>
                <Tag
                  draggable
                  onDragStart={(e) => { e.dataTransfer.setData('text/plain', `F:${f.code}`); e.dataTransfer.effectAllowed = 'copy'; }}
                  onClick={() => onChange(addControl(schema, view, Math.max(0, sections.length - 1), f))}
                  style={{ cursor: 'pointer', margin: 0, padding: '2px 8px' }}
                  color="blue"
                >
                  {f.displayName}
                  <Text type="secondary" style={{ fontSize: 10, marginLeft: 4 }}>{f.code}</Text>
                </Tag>
              </Tooltip>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
