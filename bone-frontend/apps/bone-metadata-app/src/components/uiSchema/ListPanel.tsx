/**
 * 列表视图面板：列投影 + 页签 + 搜索栏 + 快捷筛选 + 数据范围 + 统计卡 + 批量动作 + 视图形态。
 *
 * 列表不是表单的附属品 —— 审批人 80% 的时间在列表页（协议 §3.2）。此前面板只编辑 columns，
 * 而 SchemaPreview 早已能渲染 tabs/searchFields/dataScope/stats —— 「配不出来但预览得到」
 * 是预览玩具；本面板把协议 list 全量能力开放为编辑器（业界对标：Salesforce List View /
 * NocoBase 区块 / TPA processPage）。
 *
 * 引用纪律：dataScope.policyCode 只引用 row_policies 声明的 rule name（后端 WHERE 强制，
 * 前端不拼）；batchActions 只引用已发布 actionCode；stats.valueRef 只引用后端聚合口径。
 */
import { useMemo } from 'react';
import {
  Alert, Button, Divider, Dropdown, Empty, Input, InputNumber, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, EyeInvisibleOutlined, PlusOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import type { ActionDefSpec } from '../../services/metadataApi';
import {
  addColumn, addStat, addTab, moveColumn, moveTab, removeStat, removeTab,
  setBatchActions, setDataScope, setDegradedBanner, setViewKind, toggleQuickFilters, toggleSearchFields,
  updateColumn, updateStat, updateTab,
} from './schemaOps';
import { defaultOverlay, projectRuntimeColumns } from '../../utils/columnProjection';

const { Text } = Typography;

/** tabs.value 可用的上下文变量（协议 §11.1-3：统一 $me/$myteam/$all，禁方言） */
const DOLLAR_VARS = [
  { value: '$me', label: '$me · 我提交的' },
  { value: '$myteam', label: '$myteam · 我团队的' },
  { value: '$all', label: '$all · 全部' },
];

const VIEW_KINDS = [
  { value: 'table', label: '表格（首版唯一完整形态）' },
  { value: 'kanban', label: '看板（P1）' },
  { value: 'calendar', label: '日历（P1）' },
];

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  /**
   * 实体行策略声明的 rule name 集合（dataScope 下拉真源；来自 entity.rowPolicies）。
   * 注意：policy 指行级数据策略（RLS），与保险业务域的「保单」无关。
   */
  rowPolicyCodes?: string[];
  /** 实体已定义动作（批量动作下拉真源） */
  actionDefs?: ActionDefSpec[] | null;
  focus?: { idx?: number };
}

/** 小节标题（统一视觉：编号 + 标题 + 协议出处，降低"这是什么"的认知成本） */
function GroupTitle({ no, title, proto }: { no: string; title: string; proto?: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, margin: '18px 0 8px' }}>
      <Text strong style={{ fontSize: 13 }}>{no} · {title}</Text>
      {proto && <Text type="secondary" style={{ fontSize: 11 }}>{proto}</Text>}
    </div>
  );
}

export default function ListPanel({ schema, onChange, fields, rowPolicyCodes = [], actionDefs = null, focus }: Props): React.ReactElement {
  const list = schema.views.list ?? {};
  const cols = list.columns ?? [];
  const tabs = list.tabs ?? [];
  const stats = list.stats ?? [];
  const searchFields = list.searchFields ?? [];
  const quickFilters = list.quickFilters ?? [];
  const batchActions = list.batchActions ?? [];
  const unused = fields.filter((f) => !cols.some((c) => c.fieldCode === f.code));
  // 与 fieldOptions 同源标注（含 CHILD_TABLE「明细列」后缀）：添加列下拉是
  // 建模者接触该语义的第一现场，两处 label 口径必须一致
  const optionLabel = (f: MetaField) =>
    `${f.displayName}（${f.code}）${(f.type || '').toUpperCase() === 'CHILD_TABLE' ? ' · 明细列' : ''}`;
  const fieldOptions = fields.map((f) => ({ value: f.code, label: optionLabel(f) }));

  const actionOptions = useMemo(
    () => (actionDefs ?? []).map((a) => ({ value: a.actionCode, label: a.label ? `${a.label}（${a.actionCode}）` : a.actionCode })),
    [actionDefs],
  );

  /**
   * 最终列序核对（契约口径 · 默认覆盖 = 无人为干预的团队视角）。
   *
   * <p>用**运行时同一个** {@code projectRuntimeColumns} 投影，而不是就地数 columns：
   * 契约引用了已删字段会被跳过、visible=false 被剔除，落库后运行时就是这样渲染——
   * 这里必须显示"运行时最终会看到的 N 列"，否则建模者数出来的列数与上线不符。
   * 个人覆盖不参与（个人语义归运行时数据页，建模台不夹带个人噪声）。
   */
  const finalCols = useMemo(() => {
    const listView = cols.length > 0 ? { columns: cols } : null;
    return projectRuntimeColumns(fields, defaultOverlay(fields), listView).map((c) => c.field);
  }, [cols, fields]);

  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
        列表列按顺序渲染；宽度单位为像素，建议合计不超过 1600（超出横向滚动）。
        以下配置随实体发布生效，数据范围由后端强制（前端声明、不拼 WHERE）。
        CHILD_TABLE（子表）字段可作为「明细」列：运行时渲染为行内「查看明细」入口，不占数据列、不可筛选/排序。
      </Text>

      {/* ---------- 1. 列投影 ---------- */}
      <GroupTitle no="1" title="列投影" proto="columns[]" />
      {cols.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未配置列投影，运行时按实体字段默认渲染" style={{ padding: '18px 0' }} />
      )}

      {cols.map((c, i) => (
        <div
          key={`${c.fieldCode}-${i}`}
          data-tour="list-row"
          style={{
            display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
            padding: '7px 9px', border: `1px solid ${focus?.idx === i ? '#ffa940' : '#f0f0f0'}`,
            borderRadius: 8, marginBottom: 6, background: c.visible === false ? '#fafafa' : '#fff',
            opacity: c.visible === false ? 0.7 : 1,
          }}
        >
          <Text type="secondary" style={{ fontSize: 11, width: 20 }}>{i + 1}</Text>
          <Select size="small" showSearch optionFilterProp="label" style={{ minWidth: 160 }}
            options={fieldOptions} value={c.fieldCode || undefined}
            onChange={(v) => onChange(updateColumn(schema, i, { fieldCode: v }))} />
          <Input size="small" style={{ width: 130 }} placeholder="列标题覆盖"
            value={(c.format?.title as string | undefined) ?? ''}
            onChange={(e) => onChange(updateColumn(schema, i, {
              format: { ...c.format, title: e.target.value || undefined },
            }))} />
          <Tooltip title="列宽（px）">
            <InputNumber size="small" style={{ width: 76 }} min={0} value={c.width ?? undefined}
              onChange={(v) => onChange(updateColumn(schema, i, { width: v ?? undefined }))} />
          </Tooltip>
          <Tooltip title="固定列方向（横向滚动时钉住）：左=主键/案件号，右=操作列">
            <Segmented
              size="small" value={c.pinned ?? 'none'}
              onChange={(v) => onChange(updateColumn(schema, i, { pinned: v === 'none' ? undefined : (v as 'left' | 'right') }))}
              options={[
                { value: 'none', label: '不固定' },
                { value: 'left', label: '钉左' },
                { value: 'right', label: '钉右' },
              ]}
            />
          </Tooltip>
          <Tooltip title="显示">
            <Switch size="small" checked={c.visible !== false}
              onChange={(v) => onChange(updateColumn(schema, i, { visible: v ? undefined : false }))} />
          </Tooltip>
          {c.visible === false && <Tag style={{ fontSize: 11 }}>已隐藏</Tag>}
          <span style={{ flex: 1 }} />
          <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={i === 0}
            onClick={() => onChange(moveColumn(schema, i, i - 1))} />
          <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={i === cols.length - 1}
            onClick={() => onChange(moveColumn(schema, i, i + 1))} />
          <Tooltip title="从列表移除（保留契约记录，可再开启；彻底删条目会让该列在运行时重新追加到末尾）">
            <Button size="small" type="text" danger icon={<EyeInvisibleOutlined />}
              onClick={() => onChange(updateColumn(schema, i, { visible: false }))} />
          </Tooltip>
        </div>
      ))}

      <Space wrap style={{ marginTop: 6 }}>
        <Select
          size="small" showSearch optionFilterProp="label" style={{ minWidth: 180 }}
          placeholder={unused.length ? '添加列…' : '字段已全部加入'} value={undefined}
          options={unused.map((f) => ({ value: f.code, label: optionLabel(f) }))}
          onChange={(v) => { if (v) onChange(addColumn(schema, v)); }}
          suffixIcon={<PlusOutlined />}
        />
        <Text type="secondary" style={{ fontSize: 11 }}>
          共 {cols.length} 列 · 可见 {cols.filter((c) => c.visible !== false).length} 列
        </Text>
      </Space>

      {/* 最终列序核对：与运行时列表同源投影，改列即时看到「上线后实际看到的列」 */}
      <Alert
        type="info"
        showIcon
        style={{ marginTop: 8 }}
        message={<span style={{ fontSize: 13 }}>运行时最终显示 {finalCols.length} 列（发布后全员一致）</span>}
        description={
          <div style={{ fontSize: 12 }}>
            <div>
              {finalCols.length === 0
                ? '当前无可见列，运行时列表为空。'
                : finalCols.map((f) => f.displayName || f.code).join(' → ')}
            </div>
            <div style={{ marginTop: 4 }}>
              口径：与运行时数据列同一投影函数（已剔除隐藏列与已删字段的失效引用）。个人显隐不在此生效——
              团队契约定 everyone 看到什么，终端用户的个人偏好在运行时数据页调整。
            </div>
          </div>
        }
      />

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 2. 页签 ---------- */}
      <GroupTitle no="2" title="列表页签" proto="tabs[] · 按字段值分 Tab（如 待我审批/我提交的/全部）" />
      {tabs.length === 0 && (
        <Text type="secondary" style={{ fontSize: 12 }}>未配置页签，列表为单视图。</Text>
      )}
      {tabs.map((t, i) => (
        <div key={i} style={{
          display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
          padding: '7px 9px', border: '1px solid #f0f0f0', borderRadius: 8, marginBottom: 6, background: '#fff',
        }}>
          <Input size="small" style={{ width: 130 }} placeholder="页签标题" value={t.title ?? ''}
            onChange={(e) => onChange(updateTab(schema, i, { title: e.target.value || undefined }))} />
          <Text type="secondary" style={{ fontSize: 11 }}>字段</Text>
          <Select size="small" showSearch optionFilterProp="label" style={{ minWidth: 140 }}
            options={fieldOptions} value={t.fieldCode || undefined}
            onChange={(v) => onChange(updateTab(schema, i, { fieldCode: v }))} />
          <Text type="secondary" style={{ fontSize: 11 }}>值</Text>
          <Space.Compact>
            <Input
              size="small" style={{ width: 130 }} placeholder="固定值"
              value={t.value === undefined ? '' : String(t.value)}
              onChange={(e) => onChange(updateTab(schema, i, { value: e.target.value === '' ? undefined : e.target.value }))}
            />
            <Dropdown
              menu={{
                items: DOLLAR_VARS.map((d) => ({
                  key: d.value,
                  label: d.label,
                  onClick: () => onChange(updateTab(schema, i, { value: d.value })),
                })),
              }}
            >
              <Button size="small">上下文变量</Button>
            </Dropdown>
          </Space.Compact>
          {t.value === '$myteam' && <Tag color="blue" style={{ fontSize: 11 }}>需后端团队口径</Tag>}
          <span style={{ flex: 1 }} />
          <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={i === 0}
            onClick={() => onChange(moveTab(schema, i, i - 1))} />
          <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={i === tabs.length - 1}
            onClick={() => onChange(moveTab(schema, i, i + 1))} />
          <Button size="small" type="text" danger onClick={() => onChange(removeTab(schema, i))}>删</Button>
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange(addTab(schema))}>添加页签</Button>

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 3. 搜索栏 ---------- */}
      <GroupTitle no="3" title="搜索栏字段" proto="searchFields[] · 声明搜哪些（filter JSON v1.0.5 已就位）" />
      <Select
        size="small" mode="multiple" allowClear style={{ width: '100%', maxWidth: 420 }}
        placeholder="选择进入搜索栏的字段（空 = 不渲染搜索栏）"
        value={searchFields}
        onChange={(v) => onChange(toggleSearchFields(schema, v as string[]))}
        options={fieldOptions}
      />
      <div style={{ marginTop: 6 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>
          提示：日期/金额字段进搜索栏即获得区间查询（后端 filter 契约），文本字段为关键词包含。
        </Text>
      </div>

      {/* ---------- 4. 快捷筛选 ---------- */}
      <GroupTitle no="4" title="快捷筛选" proto="quickFilters[] · 列表顶部一键筛选 chips" />
      <Select
        size="small" mode="multiple" allowClear style={{ width: '100%', maxWidth: 420 }}
        placeholder="选择快捷筛选字段（枚举/布尔最佳）"
        value={quickFilters}
        onChange={(v) => onChange(toggleQuickFilters(schema, v as string[]))}
        options={fieldOptions}
      />
      <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {quickFilters.map((c) => (
          <Tag key={c} closable
            onClose={(e) => { e.preventDefault(); onChange(toggleQuickFilters(schema, quickFilters.filter((x) => x !== c))); }}>
            {fields.find((f) => f.code === c)?.displayName ?? c}
          </Tag>
        ))}
      </div>

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 5. 数据范围 ---------- */}
      <GroupTitle no="5" title="数据范围" proto="dataScope.policyCode · 只引用 row_policies，WHERE 由后端强制" />
      {rowPolicyCodes.length > 0 ? (
        <Select
          size="small" allowClear style={{ width: '100%', maxWidth: 320 }}
          placeholder="选择行策略（后端按策略加 WHERE，前端不拼条件）"
          value={list.dataScope?.policyCode}
          onChange={(v) => onChange(setDataScope(schema, v ?? ''))}
          options={rowPolicyCodes.map((p) => ({ value: p, label: p }))}
        />
      ) : (
        <Alert
          type="info" showIcon style={{ maxWidth: 520 }}
          message="该实体尚未声明行策略"
          description="在实体「结构」页签配置 rowPolicies（如 owner_or_approver）后，这里即可引用；无策略时列表按平台默认可见性。"
        />
      )}
      {list.dataScope && (
        <div style={{ marginTop: 6 }}>
          <Tag color="blue">已引用 {list.dataScope.policyCode}</Tag>
          <Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>
            行级 ACL 由 RuntimeAccessGuard 逐请求断言——前端显隐永不作为鉴权依据。
          </Text>
        </div>
      )}

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 6. 统计卡 ---------- */}
      <GroupTitle no="6" title="统计卡" proto="stats[] · 待办/总额类卡片（valueRef 引用后端聚合口径，UI 不自算）" />
      {stats.length === 0 && (
        <Text type="secondary" style={{ fontSize: 12 }}>未配置统计卡。</Text>
      )}
      {stats.map((t, i) => (
        <div key={i} style={{
          display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
          padding: '7px 9px', border: '1px solid #f0f0f0', borderRadius: 8, marginBottom: 6, background: '#fff',
        }}>
          <Input size="small" style={{ width: 150 }} placeholder="卡片标题（如 待我审批）" value={t.title ?? ''}
            onChange={(e) => onChange(updateStat(schema, i, { title: e.target.value || undefined }))} />
          <Text type="secondary" style={{ fontSize: 11 }}>口径</Text>
          <Input size="small" style={{ width: 170 }} placeholder="聚合口径 code（valueRef）" value={t.valueRef ?? ''}
            onChange={(e) => onChange(updateStat(schema, i, { valueRef: e.target.value || undefined }))} />
          <Input size="small" style={{ width: 120 }} placeholder="趋势说明（可选）" value={t.trend ?? ''}
            onChange={(e) => onChange(updateStat(schema, i, { trend: e.target.value || undefined }))} />
          <span style={{ flex: 1 }} />
          <Button size="small" type="text" danger onClick={() => onChange(removeStat(schema, i))}>删</Button>
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange(addStat(schema))}>添加统计卡</Button>
      <div style={{ marginTop: 4 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>
          聚合口径由后端提供；未接入时预览显示「聚合口径待接入」占位（禁静默空白，PRD §5.5）。
        </Text>
      </div>

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 7. 批量动作 ---------- */}
      <GroupTitle no="7" title="批量动作" proto="batchActions[] · 只引用已发布 actionCode" />
      {actionOptions.length > 0 ? (
        <Select
          size="small" mode="multiple" allowClear style={{ width: '100%', maxWidth: 420 }}
          placeholder="选择可批量执行的动作"
          value={batchActions}
          onChange={(v) => onChange(setBatchActions(schema, v as string[]))}
          options={actionOptions}
        />
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>该实体未定义动作，暂无可批量执行的动作。</Text>
      )}

      <Divider style={{ margin: '16px 0 0' }} />

      {/* ---------- 8. 视图形态 ---------- */}
      <GroupTitle no="8" title="视图形态与降级" proto="viewKind / degradedBanner{when,text}" />
      <Space wrap align="center">
        <Select
          size="small" style={{ width: 220 }}
          value={list.viewKind ?? 'table'}
          onChange={(v) => onChange(setViewKind(schema, v))}
          options={VIEW_KINDS}
        />
        <Input
          size="small" style={{ width: 150 }} allowClear
          placeholder="触发条件（如 llm_unavailable）"
          value={list.degradedBanner?.when ?? ''}
          onChange={(e) => onChange(setDegradedBanner(schema, {
            when: e.target.value || list.degradedBanner?.when || 'llm_unavailable',
            text: list.degradedBanner?.text ?? '',
          }))}
        />
        <Input
          size="small" style={{ width: 280 }} allowClear
          placeholder="降级横幅文案（依赖不可用时顶部提示，可空）"
          value={list.degradedBanner?.text ?? ''}
          onChange={(e) => onChange(setDegradedBanner(schema, {
            when: list.degradedBanner?.when ?? 'llm_unavailable',
            text: e.target.value,
          }))}
        />
      </Space>
      <div style={{ marginTop: 4 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>
          看板/日历为 P1 能力，声明后运行时按表格降级并展示 degradedBanner（前向兼容：未知形态不白屏）。
        </Text>
      </div>
    </div>
  );
}
