/**
 * 列表视图面板：列投影（显示哪些列、顺序、宽度、是否固定）。
 *
 * 列表不是表单的附属品 —— 审批人 80% 的时间在列表页。列宽/顺序/固定列直接影响效率，
 * 因此单列一个 tab 而不是塞在结构面板里。
 */
import {
  Button, Empty, Input, InputNumber, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, EyeInvisibleOutlined, PlusOutlined, PushpinOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { addColumn, moveColumn, updateColumn } from './schemaOps';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  focus?: { idx?: number };
}

export default function ListPanel({ schema, onChange, fields, focus }: Props): React.ReactElement {
  const cols = schema.views.list?.columns ?? [];
  const fieldOptions = fields.map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）` }));
  const unused = fields.filter((f) => !cols.some((c) => c.fieldCode === f.code));

  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 10 }}>
        列表列按顺序渲染；宽度单位为像素，建议合计不超过 1600（超出横向滚动）。
      </Text>

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
          <Tooltip title="固定列（横向滚动时钉住）">
            <Space size={2}>
              <Switch size="small" checked={!!c.pinned}
                onChange={(v) => onChange(updateColumn(schema, i, { pinned: v || undefined }))} />
              <PushpinOutlined style={{ color: c.pinned ? '#1677ff' : '#d9d9d9' }} />
            </Space>
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
          options={unused.map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）` }))}
          onChange={(v) => { if (v) onChange(addColumn(schema, v)); }}
          suffixIcon={<PlusOutlined />}
        />
        <Text type="secondary" style={{ fontSize: 11 }}>
          共 {cols.length} 列 · 可见 {cols.filter((c) => c.visible !== false).length} 列
        </Text>
      </Space>
    </div>
  );
}
