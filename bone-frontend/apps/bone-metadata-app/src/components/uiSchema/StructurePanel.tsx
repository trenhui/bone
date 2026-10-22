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
import { useEffect, useMemo, useState } from 'react';
import {
  Badge, Button, Checkbox, Empty, Input, InputNumber, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, CopyOutlined, DeleteOutlined, DownOutlined, DragOutlined,
  EyeInvisibleOutlined, PlusOutlined, QuestionCircleOutlined, SearchOutlined, SettingOutlined, UpOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema, Control, ControlKind } from '@bone/ui-schema';
import type { MetaField } from '../../types';
import { CONTROL_KIND_OPTIONS, DISPLAY_MODE_OPTIONS, KIND_MAP, MASK_OPTIONS, ROLE_OPTIONS, SECTION_KIND_OPTIONS, SECTION_LAYOUT_OPTIONS, TIER_OPTIONS } from './constants';
import {
  addControl, addSection, batchDuplicateControls, batchHideControls, batchRemoveControls,
  duplicateControl, moveControl, moveControlAcross, moveSection,
  removeControl, removeSection, setPageHeader, unusedFields, updateControl, updateFooter, updateSection, usedFieldCodes, viewOf,
} from './schemaOps';
import { dragSort2 } from './drag';
import type { ViewKey } from './schemaOps';
import { useScrollToFocus } from './useScrollToFocus';

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
              options={DISPLAY_MODE_OPTIONS}
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
                <Switch size="small" aria-label="AI 生成标识" checked={!!footer.aiBadge}
                  onChange={(v) => onChange(updateFooter(schema, { aiBadge: v || undefined }))} />
                <Text style={{ fontSize: 12 }}>AI 生成标识</Text>
              </Space>
              <Space size={4}>
                <Switch size="small" aria-label="模拟数据标注" checked={!!footer.mockDataMark}
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
  const vKind = (control.controlKind ?? 'INPUT').toUpperCase();
  const isNumeric = vKind === 'NUMBER' || vKind === 'MONEY' || vKind === 'SLIDER';
  const isText = vKind === 'INPUT' || vKind === 'TEXTAREA';
  /** 写入控件级 props 的辅助：剔除 undefined 键，避免脏数据残留在 schema */
  const patchProp = (k: string, v: unknown) => {
    const next = { ...(control.props ?? {}) };
    // 空数组同样视作"未配置"删键：空 roles/tier 语义上=不限制，留着会让建模者
    // 以为配过（且 lint 会对空数组报 warning），删掉才是真·未配置。
    if (v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0)) delete next[k];
    else next[k] = v;
    onPatch({ props: next });
  };
  return (
    <div
    // 检视器根节点锚点（与控件行 data-tour="control-row" 同口径）：
    // 该面板此前只有内联样式、无任何稳定选择器，导致 e2e 探针只能用
    // 「扫全文找『显示名覆盖』文字」这种脆弱办法定位（实测 placeholder 是动态的
    // 字段显示名，无法按属性定位）。补锚点让探针与引导可精确定位。
      data-tour="control-inspector"
      style={{
        background: '#fafbfd', border: '1px solid #f0f0f0', borderRadius: 8,
        padding: 10, marginTop: 6, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8,
      }}
    >
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
            size="small" aria-label="默认隐藏该控件" checked={control.visible === false}
            onChange={(v) => onPatch({ visible: v ? false : undefined })}
          />
          <Text style={{ fontSize: 12 }}>默认隐藏</Text>
        </Space>
        <Space size={4}>
          <Switch size="small" aria-label="只读控件" checked={!!control.readonly} onChange={(v) => onPatch({ readonly: v || undefined })} />
          <Text style={{ fontSize: 12 }}>只读</Text>
        </Space>
        {field?.required && <Tag color="red" style={{ fontSize: 11 }}>元数据必填</Tag>}
      </Space>
      {/* N4：控件级校验规则编辑器（对标宜搭/PowerApps 字段级必填/最值/正则） */}
      <div style={{ gridColumn: '1 / -1', borderTop: '1px dashed #eee', marginTop: 4, paddingTop: 8 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>校验规则</Text>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 6 }}>
          <Space size={4}>
            <Switch
              size="small" aria-label="控件级必填覆盖" checked={!!control.required}
              onChange={(v) => onPatch({ required: v || undefined })}
            />
            <Text style={{ fontSize: 12 }}>必填覆盖</Text>
          </Space>
          {isNumeric && (
            <>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>最小值</Text>
                <InputNumber
                  size="small" style={{ width: 110 }} placeholder="min"
                  value={control.props?.min as number | undefined}
                  onChange={(v) => patchProp('min', v)}
                />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>最大值</Text>
                <InputNumber
                  size="small" style={{ width: 110 }} placeholder="max"
                  value={control.props?.max as number | undefined}
                  onChange={(v) => patchProp('max', v)}
                />
              </div>
            </>
          )}
          {isText && (
            <>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>最大长度</Text>
                <InputNumber
                  size="small" style={{ width: 110 }} min={1} placeholder="maxLength"
                  value={control.props?.maxLength as number | undefined}
                  onChange={(v) => patchProp('maxLength', v)}
                />
              </div>
              <div style={{ flex: '1 0 200px' }}>
                <Text type="secondary" style={{ fontSize: 11 }}>正则（pattern）</Text>
                <Input
                  size="small" placeholder="如 ^1[3-9]\d{9}$"
                  value={(control.props?.pattern as string | undefined) ?? ''}
                  onChange={(e) => patchProp('pattern', e.target.value)}
                />
              </div>
            </>
          )}
        </div>
      </div>
      {/* N7：字段级角色/档位可见性（协议 control.props.roles / tier已声明，此前引擎零消费） */}
      <div style={{ gridColumn: '1 / -1', borderTop: '1px dashed #eee', marginTop: 4, paddingTop: 8 }}>
        <Space size={4}>
          <Text type="secondary" style={{ fontSize: 11 }}>可见范围</Text>
          <Tooltip title="限定后，仅命中这些角色的用户能看到该控件；未配置=所有人可见。与行级权限（rowPolicies）独立，可叠加。">
            <QuestionCircleOutlined style={{ fontSize: 11, color: '#86909c' }} />
          </Tooltip>
        </Space>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 6 }}>
          <div style={{ flex: '1 0 240px' }}>
            <Text type="secondary" style={{ fontSize: 11 }}>限定角色（roles）</Text>
            {/* 受控多选（禁自由文本），与 VariantsPanel 的 match.roles 同一纪律：
                角色码打错（如 reviwer）在运行时永远不命中且无提示 —— 字段对所有人隐藏，
                用户看不出是打错了。自由输入等于允许制造这种静默失效。
                已知局限：ROLE_OPTIONS 是演示角色集，接IAM 角色目录后替换即可。 */}
            <Select
              mode="multiple" allowClear size="small" style={{ width: '100%' }} placeholder="留空=所有人可见"
              options={ROLE_OPTIONS}
              value={(control.props?.roles as string[] | undefined) ?? []}
              onChange={(v) => patchProp('roles', v)}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>订阅档（tier）</Text>
            {/* 档位是商业套餐码、无封闭枚举 ⇒ 允许自由输入（同VariantsPanel） */}
            <Select
              mode="tags" allowClear size="small" style={{ width: 200 }}
              options={TIER_OPTIONS}
              value={(control.props?.tier as string[] | undefined) ?? []}
              onChange={(v) => patchProp('tier', v)}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>脱敏标注（mask）</Text>
            <Tooltip title="⚠ 当前仅作建模标注：运行时数据仍返回明文，服务端尚无消费方，不可当作安全控制。">
              <Select
                allowClear size="small" style={{ width: 130 }}
                aria-label="脱敏标注"
                options={MASK_OPTIONS}
                value={(control.props?.mask as string | undefined)}
                onChange={(v) => patchProp('mask', v)}
              />
            </Tooltip>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function StructurePanel({ schema, onChange, fields, view, onViewChange, focus }: Props): React.ReactElement {
  const [sel, setSel] = useState<{ si: number; ci: number } | null>(null);
  const [dragOver, setDragOver] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [kindFilter, setKindFilter] = useState<string>('all');
  /** N6：折叠/展开全部分区（纯 UI 态，不进 schema）。折叠后仅留分区头，便于长表单快速总览。 */
  const [collapsedAll, setCollapsedAll] = useState(false);
  /** N1：多选批量态。开启后控件行改为「勾选」语义，禁用拖拽/单移以免下标漂移。 */
  const [multiMode, setMultiMode] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  /**
   * 键盘重排的焦点跟随（WCAG 2.1.1 键盘可达）：拖拽有鼠标态，但键盘移动后该行/分区的
   * 下标会变、React 按 key 卸载重挂，焦点随之丢失。移动后置 focusCode，渲染后由 effect
   * 把对应 data-code 元素重新聚焦（控件行的 data-code=字段码、分区头的 data-code=sec-<id>）。
   */
  const [focusCode, setFocusCode] = useState<string | null>(null);

  const toggleSel = (key: string) => {
    setSelectedKeys((prev) => {
      const nxt = new Set(prev);
      if (nxt.has(key)) nxt.delete(key); else nxt.add(key);
      return nxt;
    });
  };
  const exitMulti = () => { setMultiMode(false); setSelectedKeys(new Set()); };

  /**
   * P1-3 结构面板搜索 / 类型过滤（对标 Appsmith / 阿里宜搭在宽表实体里的控件定位）。
   * 仅做展示层隐藏（display:none），不改动 schema 数组顺序与下标 —— 保证 moveControl /
   * removeControl 的 si/ci 始终指向真实位置（过滤态下移动/删除仍精确）。
   */
  const active = query.trim() !== '' || kindFilter !== 'all';
  const ql = query.trim().toLowerCase();
  const matchControl = (c: Control): boolean => {
    if (kindFilter !== 'all' && c.controlKind !== kindFilter) return false;
    if (!ql) return true;
    const f = fields.find((x) => x.code === c.fieldCode);
    const name = (c.labelOverride || f?.displayName || c.fieldCode || '').toLowerCase();
    const code = (c.fieldCode || '').toLowerCase();
    return name.includes(ql) || code.includes(ql);
  };
  const usedKinds = useMemo(
    () => [...new Set((viewOf(schema, view).sections ?? []).flatMap((sec) => (sec.controls ?? []).map((c) => c.controlKind)))],
    [schema, view],
  );
  const matchCount = useMemo(
    () => (viewOf(schema, view).sections ?? []).reduce((n, sec) => n + (sec.controls ?? []).filter(matchControl).length, 0),
    // matchControl 依赖 ql/kindFilter/fields，显式列出避免过期闭包
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [schema, view, ql, kindFilter, fields],
  );

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

  // 键盘重排后把焦点还给被移动的元素（见 focusCode 注释；schema 变化即重渲染后再聚焦）
  useEffect(() => {
    if (!focusCode) return;
    const el = document.querySelector(`[data-code="${CSS.escape(focusCode)}"]`) as HTMLElement | null;
    el?.focus();
    setFocusCode(null);
  }, [focusCode, schema]);

  const sections = viewOf(schema, view).sections ?? [];

  // 定位滚动锚点键：focus 命中本面板的哪个元素，就滚哪个（ci 缺省=分区级）
  const locateKey = focus ? (focus.ci != null ? `s${focus.si}:c${focus.ci}` : `s${focus.si}`) : undefined;
  useScrollToFocus(locateKey);

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

      {/* ---------- N6 折叠/展开全部 · N1 多选批量 ---------- */}
      <Space style={{ marginBottom: 10 }} wrap>
        <Tooltip title="折叠/展开所有分区的控件列表（仅隐藏展示，不改动 schema）">
          <Button
            size="small" aria-label={collapsedAll ? '展开全部' : '折叠全部'}
            icon={collapsedAll ? <DownOutlined /> : <UpOutlined />}
            onClick={() => setCollapsedAll((v) => !v)}
          >
            {collapsedAll ? '展开全部' : '折叠全部'}
          </Button>
        </Tooltip>
        <Tooltip title="开启后控件行改为勾选，可跨分区批量隐藏/复制/删除">
          <Button
            size="small" aria-label={multiMode ? '退出多选' : '多选'}
            type={multiMode ? 'primary' : 'default'}
            onClick={() => (multiMode ? exitMulti() : setMultiMode(true))}
          >
            {multiMode ? '退出多选' : '多选'}
          </Button>
        </Tooltip>
        {multiMode && selectedKeys.size > 0 && (
          <Space size={4} style={{ background: '#f0f7ff', padding: '2px 8px', borderRadius: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>已选 {selectedKeys.size}</Text>
            <Button size="small" aria-label="批量隐藏" onClick={() => { onChange(batchHideControls(schema, view, [...selectedKeys])); setSelectedKeys(new Set()); }}>隐藏</Button>
            <Button size="small" aria-label="批量复制" onClick={() => { onChange(batchDuplicateControls(schema, view, [...selectedKeys])); setSelectedKeys(new Set()); }}>复制</Button>
            <Button size="small" danger aria-label="批量删除" onClick={() => { onChange(batchRemoveControls(schema, view, [...selectedKeys])); setSelectedKeys(new Set()); }}>删除</Button>
            <Button size="small" onClick={() => setSelectedKeys(new Set())}>取消</Button>
          </Space>
        )}
      </Space>

      {/* ---------- P1-3 搜索 / 类型过滤：宽表实体（100+ 字段）定位控件 ---------- */}
      <Space style={{ marginBottom: 10, width: '100%' }} wrap>
        <Input
          size="small" allowClear placeholder="搜索控件（显示名 / 字段码）"
          prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
          value={query} onChange={(e) => setQuery(e.target.value)}
          style={{ width: 210 }}
        />
        <Select
          size="small" style={{ width: 140 }} value={kindFilter} onChange={(v) => setKindFilter(v)}
          options={[{ value: 'all', label: '全部类型' }, ...usedKinds.map((k) => ({ value: k ?? '', label: KIND_MAP[k ?? '']?.label ?? (k ?? '') }))]}
        />
        {active && <Text type="secondary" style={{ fontSize: 12 }}>匹配 {matchCount} 个控件</Text>}
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
            data-code={`sec-${sec.id ?? si}`}
            data-locate={focus && focus.ci === undefined && focus.si === si ? locateKey : undefined}
            style={{
              border: `1px solid ${isFocused(si) ? '#1677ff' : secOver ? '#69c0ff' : '#f0f0f0'}`,
              borderRadius: 10, marginBottom: 10, background: '#fff',
              boxShadow: isFocused(si) ? '0 0 0 3px rgba(22,119,255,.12)' : secOver ? '0 0 0 3px rgba(105,192,255,.18)' : '0 1px 2px rgba(16,24,40,.04)',
              transition: 'box-shadow .18s, border-color .18s',
              display: active && !(sec.controls ?? []).some(matchControl) ? 'none' : undefined,
            }}
          >
            {/* 分区头（可聚焦 + Alt+↑/↓ 重排：拖拽有鼠标态，键盘态需等价可达，WCAG 2.1.1） */}
            <div
              tabIndex={0}
              role="group"
              aria-label={`分区 ${sec.title || sec.id || si}${si > 0 ? '，按 Alt+↑ 上移' : ''}${si < sections.length - 1 ? '，按 Alt+↓ 下移' : ''}`}
              onKeyDown={(e) => {
                if (!e.altKey) return;
                if (si > 0 && e.key === 'ArrowUp') {
                  e.preventDefault();
                  onChange(moveSection(schema, view, si, si - 1));
                  setFocusCode(`sec-${sections[si - 1]?.id ?? si - 1}`);
                } else if (si < sections.length - 1 && e.key === 'ArrowDown') {
                  e.preventDefault();
                  onChange(moveSection(schema, view, si, si + 1));
                  setFocusCode(`sec-${sections[si + 1]?.id ?? si + 1}`);
                }
              }}
              style={{
                display: 'flex', alignItems: 'center', gap: 6, padding: '8px 10px',
                borderBottom: '1px solid #f5f5f5', background: dragOver === `S${si}` ? '#f0f7ff' : '#fafbfd', borderTopLeftRadius: 10, borderTopRightRadius: 10,
                outline: undefined,
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
                  options={SECTION_KIND_OPTIONS}
                  onClick={(e) => e.stopPropagation()}
                />
              </Tooltip>
              <Tooltip title="分区布局（split 用于审查分栏：左 40% 风险面板 + 右 60% 原文）">
                <Select
                  size="small" style={{ width: 96 }} value={sec.layout ?? 'single'}
                  onChange={(v) => onChange(updateSection(schema, view, si, { layout: v }))}
                  options={SECTION_LAYOUT_OPTIONS}
                  onClick={(e) => e.stopPropagation()}
                />
              </Tooltip>
              <Tag style={{ fontSize: 11 }}>{sec.controls?.length ?? 0} 控件</Tag>
              <Space size={2}>
                <Tooltip title="上移">
                  <Button size="small" type="text" aria-label="分区上移" icon={<ArrowUpOutlined />} disabled={si === 0}
                    onClick={() => onChange(moveSection(schema, view, si, si - 1))} />
                </Tooltip>
                <Tooltip title="下移">
                  <Button size="small" type="text" aria-label="分区下移" icon={<ArrowDownOutlined />} disabled={si === sections.length - 1}
                    onClick={() => onChange(moveSection(schema, view, si, si + 1))} />
                </Tooltip>
                <Tooltip title={`删除分区「${sec.title || sec.id}」`}>
                  <Button size="small" type="text" danger aria-label="删除分区" icon={<DeleteOutlined />} onClick={() => onChange(removeSection(schema, view, si))} />
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

              {/* N6：折叠全部时仅留分区头，纯展示态不进 schema（用 display:none 隐藏而非卸载，避免重排下标） */}
              {collapsedAll && (
                <div style={{ padding: '6px 10px', color: '#8c8c8c', fontSize: 12 }}>
                  已折叠 · 含 {sec.controls?.length ?? 0} 个控件
                </div>
              )}

              {(sec.controls ?? []).map((c, ci) => {
                const kind = KIND_MAP[c.controlKind ?? ''] ?? KIND_MAP.INPUT;
                const Icon = kind.icon;
                const key = `${si}:${ci}`;
                // N1 多选态：行变为「勾选」语义；inspectorOpen 仅在非多选态生效
                const inspectorOpen = sel?.si === si && sel?.ci === ci && !multiMode;
                const rowSelected = multiMode ? selectedKeys.has(key) : (sel?.si === si && sel?.ci === ci);
                // 行级落点高亮（拖拽悬停本行=重排落点）；仅非多选态启用拖拽（多选态禁用拖拽/单移，避免下标漂移）
                const rowOver = !multiMode && dragOver === `R${si}:${ci}`;
                return (
                  <div key={`${c.fieldCode}-${ci}`}>
                    <div
                    // 控件行锚点：分区头的「删除分区」按钮与控件行的「移出分区」图标同为
                    // .anticon-delete，探针/引导无法用样式类区分二者（无脑 .first() 会删掉整个分区）。
                    // 给出稳定锚点，行级操作才能被精确定位。
                      data-tour="control-row"
                      data-locate={focus && focus.ci !== undefined && focus.si === si && focus.ci === ci ? locateKey : undefined}
                      data-code={c.fieldCode}
                      {...(multiMode ? {} : dragSort2(si, ci, (fs, fc, ts, tc) => {
                        onChange(fs === ts ? moveControl(schema, view, fs, fc, tc) : moveControlAcross(schema, view, fs, fc, ts, tc));
                      }, (over) => setDragOver((cur) => (over ? `R${si}:${ci}` : cur === `R${si}:${ci}` ? null : cur))))}
                      onClick={() => (multiMode ? toggleSel(key) : setSel(inspectorOpen ? null : { si, ci }))}
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.target !== e.currentTarget) return;
                        if (multiMode) {
                          if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); toggleSel(key); }
                          return;
                        }
                        // 仅在行本身聚焦时响应方向键，避免与行内 Select/输入框的方向键冲突（WCAG 2.1.1 键盘可操作）
                        const last = (sec.controls?.length ?? 0) - 1;
                        if (e.key === 'ArrowUp') { e.preventDefault(); if (ci > 0) { onChange(moveControl(schema, view, si, ci, ci - 1)); setFocusCode(c.fieldCode); } }
                        else if (e.key === 'ArrowDown') { e.preventDefault(); if (ci < last) { onChange(moveControl(schema, view, si, ci, ci + 1)); setFocusCode(c.fieldCode); } }
                      }}
                      style={{
                        display: (active && !matchControl(c)) || collapsedAll ? 'none' : 'flex', alignItems: 'center', gap: 6, padding: '6px 8px',
                        border: `1px solid ${rowSelected ? '#1677ff' : rowOver ? '#1677ff' : isFocused(si, ci) ? '#ffa940' : '#f0f0f0'}`,
                        borderRadius: 8, marginBottom: 5,
                        background: rowSelected ? '#f0f7ff' : rowOver ? '#e6f4ff' : '#fff',
                        boxShadow: rowOver ? '0 0 0 2px rgba(22,119,255,.15)' : undefined,
                        cursor: multiMode ? 'pointer' : 'grab', transition: 'all .15s',
                      }}
                    >
                      {multiMode && (
                        <Checkbox
                          checked={selectedKeys.has(key)}
                          onClick={(e) => e.stopPropagation()}
                          onChange={() => toggleSel(key)}
                          aria-label={`选择控件 ${c.fieldCode}`}
                        />
                      )}
                      {!multiMode && <span style={{ color: '#bfbfbf', cursor: 'grab', display: 'inline-flex' }}><DragOutlined /></span>}
                      <Icon />
                      <Text code style={{ fontSize: 12 }}>{c.fieldCode || '（未绑定）'}</Text>
                      <Select
                        size="small" style={{ width: 118 }} value={c.controlKind}
                        onChange={(v) => onChange(updateControl(schema, view, si, ci, { controlKind: v as ControlKind }))}
                        options={CONTROL_KIND_OPTIONS}
                        onClick={(e) => e.stopPropagation()}
                      />
                      {c.labelOverride && <Tag color="blue" style={{ fontSize: 11 }}>{c.labelOverride}</Tag>}
                      {c.visible === false && <Tooltip title="默认隐藏"><EyeInvisibleOutlined style={{ color: '#faad14' }} /></Tooltip>}
                      {c.readonly && <Tag style={{ fontSize: 11 }}>只读</Tag>}
                      {rowOver && <Text type="secondary" style={{ fontSize: 10 }}>松开落到此处</Text>}
                      <span style={{ flex: 1 }} />
                      {!multiMode && (
                        <>
                          <Tooltip title="上移控件">
                            <Button size="small" type="text" aria-label="控件上移" icon={<ArrowUpOutlined />} disabled={ci === 0}
                              onClick={(e) => { e.stopPropagation(); onChange(moveControl(schema, view, si, ci, ci - 1)); }} />
                          </Tooltip>
                          <Tooltip title="下移控件">
                            <Button size="small" type="text" aria-label="控件下移" icon={<ArrowDownOutlined />} disabled={ci >= (sec.controls?.length ?? 0) - 1}
                              onClick={(e) => { e.stopPropagation(); onChange(moveControl(schema, view, si, ci, ci + 1)); }} />
                          </Tooltip>
                          <Tooltip title="控件属性">
                            <SettingOutlined
                              role="button" tabIndex={0} aria-label={`控件属性 ${c.fieldCode}`}
                              style={{ color: inspectorOpen ? '#1677ff' : '#bfbfbf', cursor: 'pointer' }}
                              onClick={(e) => { e.stopPropagation(); setSel(inspectorOpen ? null : { si, ci }); }}
                              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); setSel(inspectorOpen ? null : { si, ci }); } }}
                            />
                          </Tooltip>
                          <Tooltip title={`复制控件（克隆 ${c.fieldCode}）`}>
                            <CopyOutlined
                              role="button" tabIndex={0} aria-label={`复制控件 ${c.fieldCode}`}
                              style={{ color: '#1677ff', cursor: 'pointer' }}
                              onClick={(e) => { e.stopPropagation(); onChange(duplicateControl(schema, view, si, ci)); }}
                              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); onChange(duplicateControl(schema, view, si, ci)); } }}
                            />
                          </Tooltip>
                          <Tooltip title="移出分区">
                            <DeleteOutlined
                              role="button" tabIndex={0} aria-label={`移出分区 ${c.fieldCode}`}
                              style={{ color: '#ff7875', cursor: 'pointer' }}
                              onClick={(e) => { e.stopPropagation(); onChange(removeControl(schema, view, si, ci)); }}
                              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); onChange(removeControl(schema, view, si, ci)); } }}
                            />
                          </Tooltip>
                        </>
                      )}
                    </div>

                    {inspectorOpen && (
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
