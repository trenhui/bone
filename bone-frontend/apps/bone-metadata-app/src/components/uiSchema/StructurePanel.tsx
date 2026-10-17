/**
 * 结构配置面板：分区 / 控件 / 字段调色板 / 控件属性 / 视图设置（页头·合规条·显示模式）。
 *
 * 交互范式（对标 Salesforce Lightning App Builder、Retool）：
 * - 左侧画布式的分区卡片列表，控件以行呈现，可直接拖拽重排（分区内 + 跨分区）；
 * - 未使用的实体字段在底部「字段库」待选，点一下即落到当前分区（避免手填 code 打错字）；
 * - 点选控件就地展开属性卡，不弹窗（减少一次上下文切换）；
 * - 分区支持 kind/layout（v1.1：dashboard/timeline/compare/report/tasks + 左右分栏），
 *   采购审查「左 40% 风险面板 + 右 60% 原文」即 split-left-40。
 */
import { useEffect, useState } from 'react';
import {
  Badge, Button, Empty, Input, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, DragOutlined,
  EyeInvisibleOutlined, PlusOutlined, SettingOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema, Control, ControlKind } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { CONTROL_KINDS, KIND_GROUPS, KIND_MAP, SECTION_KINDS, SECTION_LAYOUTS, DISPLAY_MODES } from './constants';
import {
  addControl, addSection, moveControl, moveControlAcross, moveSection,
  removeControl, removeSection, setPageHeader, unusedFields, updateControl, updateFooter, updateSection, usedFieldCodes, viewOf,
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

/**
 * 视图设置卡：页头字段条 + 显示模式 + 常驻合规条。
 *
 * - pageHeader（v1.2 §13.2）：独立于 sections 渲染在顶部（案件号/申请人/状态），
 *   引用必须存在于某分区控件（lint/后端 validate 双查悬空）；
 * - footer（v1.1 §10.2-8）：投研/理赔合规条（免责 + AI 标识 + 模拟数据标注）；
 * - displayModes：create/edit/view 三态（协议 §3.1 子集）。
 */
function ViewSettings({ schema, onChange, view, fields }: {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  view: ViewKey;
  fields: MetaField[];
}): React.ReactElement {
  const cur = viewOf(schema, view);
  const pageHeader = cur.pageHeader ?? [];
  const usedCodes = usedFieldCodes(schema, view);
  const headerOptions = fields.filter((f) => usedCodes.has(f.code)).map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）` }));
  const modes = schema.displayModes ?? ['create', 'edit', 'view'];
  const footer = schema.footer ?? {};
  const dangling = pageHeader.filter((c) => !usedCodes.has(c));

  return (
    <div style={{
      border: '1px solid #e6eaf0', borderRadius: 10, marginBottom: 12, background: '#fff',
      boxShadow: '0 1px 2px rgba(16,24,40,.04)',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
        borderBottom: '1px solid #f5f5f5', background: '#fafbfd', borderTopLeftRadius: 10, borderTopRightRadius: 10,
      }}>
        <SettingOutlined style={{ color: '#1677ff' }} />
        <Text strong style={{ fontSize: 13 }}>视图设置</Text>
        <Text type="secondary" style={{ fontSize: 11 }}>页头 · 显示模式 · 合规条</Text>
      </div>
      <div style={{ padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>页头关键信息条（pageHeader）</Text>
          <Select
            size="small" mode="multiple" allowClear style={{ width: '100%', marginTop: 4 }}
            placeholder="选择渲染在表单顶部的关键字段（须同时存在于某个分区）"
            value={pageHeader}
            onChange={(v) => onChange(setPageHeader(schema, view, v as string[]))}
            options={headerOptions}
          />
          {dangling.length > 0 ? (
            <Text type="danger" style={{ fontSize: 11 }}>
              悬空引用：{dangling.join('、')} 未出现在任何分区控件（发布校验 ERROR）
            </Text>
          ) : (
            <Text type="secondary" style={{ fontSize: 11 }}>
              渲染在分区之上的关键信息条（如案件号 / 申请人 / 状态），与分区互斥。
            </Text>
          )}
        </div>

        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>显示模式（displayModes）</Text>
          <div style={{ marginTop: 4 }}>
            <Select
              size="small" mode="multiple" style={{ width: 260 }}
              value={modes}
              onChange={(v) => onChange({ ...schema, displayModes: v as string[] })}
              options={DISPLAY_MODES.map((m) => ({ value: m.value, label: m.label }))}
            />
            <Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>
              按 create/edit/view 渲染同一 schema（config/preview 是建模态，不进运行时）
            </Text>
          </div>
        </div>

        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>常驻合规条（footer）</Text>
          <div style={{ marginTop: 4, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <Input
              size="small" placeholder="免责声明 / 合规文案（留空不渲染）"
              value={footer.disclaimer ?? ''}
              onChange={(e) => onChange(updateFooter(schema, { disclaimer: e.target.value || undefined }))}
            />
            <Space size={16}>
              <Space size={4}>
                <Switch size="small" checked={!!footer.aiBadge}
                  onChange={(v) => onChange(updateFooter(schema, { aiBadge: v || undefined }))} />
                <Text style={{ fontSize: 12 }}>AI 生成标识</Text>
              </Space>
              <Space size={4}>
                <Switch size="small" checked={!!footer.mockDataMark}
                  onChange={(v) => onChange(updateFooter(schema, { mockDataMark: v || undefined }))} />
                <Text style={{ fontSize: 12 }}>模拟数据标注</Text>
              </Space>
            </Space>
          </div>
        </div>
      </div>
    </div>
  );
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

  /**
   * 拖拽强制收尾：dragend/drop 冒泡到 window 时无条件清高亮。
   * 为什么不能只靠行内 onDragEnd：它只清「自己那个键」——从 A 行拖到 B 行
   * 松手（或按 Esc 取消）时，dragend 在 A 上触发，B 的高亮会残留
   * （实测探针复现：dragover B 后 dragend A，B 边框仍蓝）。
   * 行内 functional 守卫负责「慢速滑走时迟滞的 dragleave 不误清他人高亮」，
   * 这里负责「任何理由的拖拽结束都全清」，两层互补。
   */
  useEffect(() => {
    const clearAll = () => setDragOver(null);
    window.addEventListener('dragend', clearAll);
    window.addEventListener('drop', clearAll);
    return () => {
      window.removeEventListener('dragend', clearAll);
      window.removeEventListener('drop', clearAll);
    };
  }, []);

  const sections = viewOf(schema, view).sections ?? [];

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

      {/* ---------- 视图设置：页头 / 合规条 / 显示模式（协议 v1.2 §13.2 + v1.1 §10.2-8） ---------- */}
      <ViewSettings schema={schema} onChange={onChange} view={view} fields={fields} />

      {sections.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="还没有分区，先建一个承载控件"
          style={{ padding: '18px 0' }}
        />
      )}

      {sections.map((sec, si) => {
        // 分区级落点高亮：标题悬停（S=整区重排落点）或分区体悬停（C=字段库拖入落点）
        const secOver = dragOver === `S${si}` || dragOver === `C${si}`;
        return (
        <div
          key={sec.id ?? si}
          data-tour="section"
          style={{
            border: `1px solid ${isFocused(si) ? '#1677ff' : secOver ? '#69c0ff' : '#f0f0f0'}`,
            borderRadius: 10, marginBottom: 10, background: '#fff',
            boxShadow: isFocused(si) ? '0 0 0 3px rgba(22,119,255,.12)' : secOver ? '0 0 0 3px rgba(105,192,255,.18)' : '0 1px 2px rgba(16,24,40,.04)',
            transition: 'box-shadow .18s, border-color .18s',
          }}
        >
          {/* 分区头 */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 10px',
            borderBottom: '1px solid #f5f5f5', background: dragOver === `S${si}` ? '#f0f7ff' : '#fafbfd', borderTopLeftRadius: 10, borderTopRightRadius: 10,
          }}>
            <Tooltip title="拖拽调整分区顺序">
              <span
                draggable
                onDragStart={(e) => { e.dataTransfer.setData('text/plain', `S${si}`); e.dataTransfer.effectAllowed = 'move'; }}
                onDragOver={(e) => { e.preventDefault(); setDragOver(`S${si}`); }}
                onDragLeave={() => setDragOver((cur) => (cur === `S${si}` ? null : cur))}
                onDrop={(e) => {
                  e.preventDefault(); setDragOver(null);
                  const raw = e.dataTransfer.getData('text/plain');
                  if (raw.startsWith('S')) onChange(moveSection(schema, view, Number(raw.slice(1)), si));
                }}
                style={{ cursor: 'grab', color: dragOver === `S${si}` ? '#1677ff' : '#bfbfbf', display: 'inline-flex' }}
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
            <Tooltip title="分区类型（v1.1：表单区/统计卡区/时间轴/对照区/报告区/待办区）">
              <Select
                size="small" style={{ width: 104 }} value={sec.kind ?? 'form'}
                onChange={(v) => onChange(updateSection(schema, view, si, { kind: v }))}
                options={SECTION_KINDS.map((k) => ({ value: k.value, label: k.label }))}
                onClick={(e) => e.stopPropagation()}
              />
            </Tooltip>
            <Tooltip title="分区布局（split 用于审查分栏：左 40% 风险面板 + 右 60% 原文）">
              <Select
                size="small" style={{ width: 96 }} value={sec.layout ?? 'single'}
                onChange={(v) => onChange(updateSection(schema, view, si, { layout: v }))}
                options={SECTION_LAYOUTS.map((l) => ({ value: l.value, label: l.label }))}
                onClick={(e) => e.stopPropagation()}
              />
            </Tooltip>
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
            onDragLeave={() => setDragOver((cur) => (cur === `C${si}` ? null : cur))}
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
              // 行级落点高亮（拖拽悬停本行=重排落点）；functional 守卫防迟滞 leave 误清
              const rowOver = dragOver === `R${si}:${ci}`;
              return (
                <div key={`${c.fieldCode}-${ci}`}>
                  <div
                    // 控件行锚点：分区头的「删除分区」按钮与控件行的「移出分区」图标同为
                    // .anticon-delete，探针/引导无法用样式类区分二者（无脑 .first() 会删掉整个分区）。
                    // 给出稳定锚点，行级操作才能被精确定位。
                    data-tour="control-row"
                    {...dragSort2(si, ci, (fs, fc, ts, tc) => {
                      onChange(fs === ts ? moveControl(schema, view, fs, fc, tc) : moveControlAcross(schema, view, fs, fc, ts, tc));
                    }, (over) => setDragOver((cur) => (over ? `R${si}:${ci}` : cur === `R${si}:${ci}` ? null : cur)))}
                    onClick={() => setSel(selected ? null : { si, ci })}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 6, padding: '6px 8px',
                      border: `1px solid ${selected ? '#1677ff' : rowOver ? '#1677ff' : isFocused(si, ci) ? '#ffa940' : '#f0f0f0'}`,
                      borderRadius: 8, marginBottom: 5,
                      background: selected ? '#f0f7ff' : rowOver ? '#e6f4ff' : '#fff',
                      boxShadow: rowOver ? '0 0 0 2px rgba(22,119,255,.15)' : undefined,
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
                    {rowOver && <Text type="secondary" style={{ fontSize: 10 }}>松开落到此处</Text>}
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
      );
      })}

      <Button
        block size="small" icon={<PlusOutlined />} style={{ marginBottom: 12 }}
        onClick={() => onChange(addSection(schema, view))}
      >
        添加分区
      </Button>
    </div>
  );
}

/**
 * 字段库（控件面板）：被左栏 FAB 复用。
 * 点选即把字段落到当前视图的最后一个分区；支持 HTML5 拖拽（dataTransfer=F:code，拖到目标分区即追加）。
 */
export function FieldPalette({
  schema, fields, onChange, view,
}: {
  schema: BoneUiSchema;
  fields: MetaField[];
  onChange: (next: BoneUiSchema) => void;
  view: ViewKey;
}): React.ReactElement {
  const palette = unusedFields(schema, fields, view);
  const sections = viewOf(schema, view).sections ?? [];
  return (
    <div style={{ maxWidth: 320 }}>
      <Space style={{ marginBottom: 6 }} wrap>
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
  );
}
