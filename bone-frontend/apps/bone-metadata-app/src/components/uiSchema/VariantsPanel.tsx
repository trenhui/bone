/**
 * 变体创作面板（协议 v1.1 `variants`；对标 designer.html 原型 FR-07）。
 *
 * 为什么必须有这个面板：身份模拟（SchemaPreview 四维：角色/档位/渠道/设备）只是"看"，
 * 变体创作才是"改"。此前 variants 只能预览裁决、不能编辑——预览玩具配不出可保存的配置。
 *
 * 复用纪律：结构/规则/动作条/列表四个子面板都是「纯 schema + onChange」契约，
 * 这里用「合成 schema」把变体的对应块透传给它们，写回时只取该块——
 * 不另造一套编辑 UI（双份编辑逻辑必然漂移；同 preview 与 runtime 共用引擎的纪律）。
 *
 * 口径对齐（engine `resolveVariant`）：命中 priority 最小且维度全匹配的变体按块整体替换 base；
 * 空 match = 全匹配兜底；同 priority 取声明顺序。面板文案必须让用户按这个心智配。
 */
import { useState } from 'react';
import {
  Alert, Button, Empty, Input, InputNumber, Popconfirm, Segmented, Select, Tag, Tooltip, Typography,
} from 'antd';
import {
  DeleteOutlined, PlusOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema, Variant, VariantMatch } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import type { ActionDefSpec } from '../../services/metadataApi';
import StructurePanel from './StructurePanel';
import RulesPanel from './RulesPanel';
import ActionBarPanel from './ActionBarPanel';
import ListPanel from './ListPanel';
import { addVariant, removeVariant, updateVariant, mergeVariantIntoBase, type ViewKey } from './schemaOps';

const { Text, Paragraph } = Typography;

const ROLE_OPTIONS = [
  { value: 'employee', label: '员工 employee' },
  { value: 'reviewer', label: '核赔 reviewer' },
  { value: 'admin', label: '管理员 admin' },
];
const CHANNEL_OPTIONS = [
  { value: 'web', label: 'Web' },
  { value: 'mobile', label: 'Mobile' },
  { value: 'wechat', label: '微信' },
];
const DEVICE_OPTIONS = [
  { value: 'desktop', label: '桌面' },
  { value: 'tablet', label: '平板' },
  { value: 'mobile', label: '手机' },
];

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  /** 实体已定义的动作（建模期真源） */
  actionDefs: ActionDefSpec[] | null;
}

/** match 维度摘要（人类可读的命中条件） */
function matchSummary(m?: VariantMatch): string {
  if (!m) return '全部身份（兜底）';
  const parts: string[] = [];
  if (m.roles?.length) parts.push(`角色∈{${m.roles.join('、')}}`);
  if (m.tiers?.length) parts.push(`档位∈{${m.tiers.join('、')}}`);
  if (m.channels?.length) parts.push(`渠道∈{${m.channels.join('、')}}`);
  if (m.devices?.length) parts.push(`设备∈{${m.devices.join('、')}}`);
  return parts.length ? parts.join(' 且 ') : '全部身份（兜底）';
}

function VariantCard({
  schema, variant, index, fields, actionDefs, onChange,
}: {
  schema: BoneUiSchema;
  variant: Variant;
  index: number;
  fields: MetaField[];
  actionDefs: ActionDefSpec[] | null;
  onChange: (next: BoneUiSchema) => void;
}): React.ReactElement {
  const [override, setOverride] = useState(false);
  const [sub, setSub] = useState<'structure' | 'rules' | 'actions' | 'list'>('structure');
  const [view, setView] = useState<ViewKey>('form');

  const patch = (p: Partial<Variant>) => onChange(updateVariant(schema, index, p));
  const setMatch = (p: Partial<VariantMatch>) => patch({ match: { ...(variant.match ?? {}), ...p } });

  /**
   * 合成 schema：把变体的块装配成一份"看起来就是它"的 schema 传给子面板。
   * 写回时只取对应块——base 的其余部分原样不动（块级替换语义）。
   */
  const overrideSchema: BoneUiSchema = {
    ...schema,
    views: {
      ...schema.views,
      form: variant.views?.form ?? { sections: [] },
      detail: variant.views?.detail ?? { sections: [] },
      list: variant.views?.list ?? { columns: [] },
    },
    linkageRules: variant.linkageRules ?? [],
    submitRules: variant.submitRules ?? [],
    actionBar: variant.actionBar ?? [],
  };
  const onOverrideChange = (next: BoneUiSchema) => patch({
    views: {
      form: next.views.form ?? variant.views?.form,
      detail: next.views.detail ?? variant.views?.detail,
      list: next.views.list ?? variant.views?.list,
    },
    linkageRules: next.linkageRules,
    submitRules: next.submitRules,
    actionBar: next.actionBar,
  });

  const secCount = (variant.views?.form?.sections ?? []).length + (variant.views?.detail?.sections ?? []).length;
  const ruleCount = (variant.linkageRules ?? []).length + (variant.submitRules ?? []).length;

  return (
    <div style={{
      border: '1px solid #e6eaf0', borderRadius: 10, marginBottom: 12, background: '#fff', overflow: 'hidden',
    }}>
      {/* 变体头：标识 + 命中条件 + 操作 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', padding: '8px 10px', background: '#fafbfd', borderBottom: '1px solid #f0f0f0' }}>
        <Input
          size="small" style={{ width: 130, fontWeight: 600 }} placeholder="变体名称"
          value={variant.label ?? ''}
          onChange={(e) => patch({ label: e.target.value || undefined })}
        />
        <Tooltip title="机器键：运行时/日志引用，建议语义化（如 reviewer_workbench）">
          <Input
            size="small" style={{ width: 150 }} placeholder="key"
            value={variant.key ?? ''}
            onChange={(e) => patch({ key: e.target.value })}
          />
        </Tooltip>
        <Tooltip title="优先级：数字越小越先命中；相同优先级按声明顺序">
          <InputNumber
            size="small" style={{ width: 80 }} min={0} placeholder="优先级"
            value={variant.priority ?? 100}
            onChange={(v) => patch({ priority: v ?? 100 })}
          />
        </Tooltip>
        <Tag color="blue" style={{ fontSize: 11, margin: 0 }}>{secCount} 分区</Tag>
        <Tag color="purple" style={{ fontSize: 11, margin: 0 }}>{ruleCount} 规则</Tag>
        <span style={{ flex: 1 }} />
        <Popconfirm
          title="合入基础模板"
          description="变体分区按 id 覆盖 base 同名分区、新 id 追加尾部；声明的规则/动作条覆盖 base。合入后移除该变体（可 Ctrl+Z 撤销）。"
          okText="合入" cancelText="取消"
          onConfirm={() => onChange(mergeVariantIntoBase(schema, index))}
        >
          <Button size="small" icon={<ThunderboltOutlined />}>合入基础模板</Button>
        </Popconfirm>
        <Popconfirm
          title="删除变体"
          description="删除后命中该身份的画面回落到基础模板。"
          okText="删除" okButtonProps={{ danger: true }} cancelText="取消"
          onConfirm={() => onChange(removeVariant(schema, index))}
        >
          <Button size="small" type="text" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </div>

      {/* 命中条件 */}
      <div style={{ padding: '10px 12px', borderBottom: '1px solid #f5f5f5' }}>
        <Text type="secondary" style={{ fontSize: 11 }}>命中条件（留空 = 该维度不限制）</Text>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 6 }}>
          <Select
            mode="tags" size="small" allowClear placeholder="角色" style={{ minWidth: 180 }}
            value={variant.match?.roles ?? []}
            onChange={(v) => setMatch({ roles: v })}
            options={ROLE_OPTIONS}
          />
          <Select
            mode="tags" size="small" allowClear placeholder="档位 tier" style={{ minWidth: 140 }}
            value={variant.match?.tiers ?? []}
            onChange={(v) => setMatch({ tiers: v })}
          />
          <Select
            mode="multiple" size="small" allowClear placeholder="渠道" style={{ minWidth: 150 }}
            value={variant.match?.channels ?? []}
            onChange={(v) => setMatch({ channels: v })}
            options={CHANNEL_OPTIONS}
          />
          <Select
            mode="multiple" size="small" allowClear placeholder="设备" style={{ minWidth: 150 }}
            value={variant.match?.devices ?? []}
            onChange={(v) => setMatch({ devices: v })}
            options={DEVICE_OPTIONS}
          />
        </div>
        <div style={{ marginTop: 6 }}>
          <Text style={{ fontSize: 12 }}>命中：<Text code style={{ fontSize: 12 }}>{matchSummary(variant.match)}</Text></Text>
        </div>
      </div>

      {/* 覆盖编辑 */}
      <div style={{ padding: '10px 12px' }}>
        <Segmented
          size="small"
          value={override ? 'edit' : 'base'}
          onChange={(v) => setOverride(v === 'edit')}
          options={[
            { value: 'base', label: '沿用基础模板' },
            { value: 'edit', label: '覆盖编辑' },
          ]}
        />
        {!override ? (
          <Paragraph type="secondary" style={{ fontSize: 12, margin: '8px 0 0' }}>
            命中该变体时整体使用基础模板的结构/规则/动作条/列表。需要为特定身份改布局时切到「覆盖编辑」。
          </Paragraph>
        ) : (
          <div style={{ marginTop: 10 }}>
            <Segmented
              size="small" value={sub} onChange={(v) => setSub(v as typeof sub)}
              options={[
                { value: 'structure', label: '结构' },
                { value: 'rules', label: '规则' },
                { value: 'actions', label: '动作条' },
                { value: 'list', label: '列表' },
              ]}
            />
            <div style={{ marginTop: 10 }}>
              {sub === 'structure' && (
                <StructurePanel
                  schema={overrideSchema} onChange={onOverrideChange} fields={fields}
                  view={view} onViewChange={setView}
                />
              )}
              {sub === 'rules' && (
                <RulesPanel schema={overrideSchema} onChange={onOverrideChange} fields={fields} />
              )}
              {sub === 'actions' && (
                <ActionBarPanel schema={overrideSchema} onChange={onOverrideChange} fields={fields} actionDefs={actionDefs} />
              )}
              {sub === 'list' && (
                <ListPanel schema={overrideSchema} onChange={onOverrideChange} fields={fields} />
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function VariantsPanel({ schema, onChange, fields, actionDefs }: Props): React.ReactElement {
  const variants = schema.variants ?? [];

  return (
    <div>
      <Alert
        type="info" showIcon style={{ marginBottom: 12 }}
        message="变体 = 按身份（角色/档位/渠道/设备）切换的另一套界面"
        description={
          <div style={{ fontSize: 12 }}>
            <div>命中优先级数字最小、四维全匹配的变体，其结构/规则/动作条/列表<b>按块整体替换</b>基础模板；未声明的块回落基础模板。命中结果在右侧预览「身份模拟」栏实时可见。</div>
            <div style={{ marginTop: 4, color: '#86909c' }}>已知边界：自检（Lint）暂只扫基础模板，变体内引用的字段错误不会被标出——保存前请用预览确认。</div>
          </div>
        }
      />

      {variants.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="还没有变体。基础模板对所有身份一致；需要差异化时（如核赔工作台 vs 员工端）再建变体"
          style={{ padding: '18px 0' }}
        >
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => onChange(addVariant(schema))}>
            新增变体
          </Button>
        </Empty>
      ) : (
        <>
          {variants.map((v, i) => (
            <VariantCard
              key={`${v.key}-${i}`}
              schema={schema}
              variant={v}
              index={i}
              fields={fields}
              actionDefs={actionDefs}
              onChange={onChange}
            />
          ))}
          <Button block size="small" icon={<PlusOutlined />} onClick={() => onChange(addVariant(schema))}>
            新增变体
          </Button>
        </>
      )}
    </div>
  );
}
