/**
 * 动作条面板：配置哪些动作在什么状态下可见、以什么顺序排列。
 *
 * actionCode 的下拉选项来自实体真实动作定义（GET /entities/{id}/action-defs），
 * 而不是手填文本 —— 手填必然与状态机真源漂移（填了个不存在的码，运行时按钮点了没反应）。
 * 真源缺失时降级为自由输入，并给出提示，不静默放行。
 */
import {
  Alert, Button, Empty, Input, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import type { ActionDefSpec } from '../../services/metadataApi';
import { describeAction } from './describe';
import ConditionEditor from './ConditionEditor';
import { addAction, moveAction, removeAction, updateAction } from './schemaOps';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  /** 实体已定义的动作（建模期真源）；null=尚未加载 */
  actionDefs: ActionDefSpec[] | null;
  /** 高亮定位 */
  focus?: { idx?: number };
}

export default function ActionBarPanel({ schema, onChange, fields, actionDefs, focus }: Props): React.ReactElement {
  const items = schema.actionBar ?? [];
  const fieldOptions = fields.map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）` }));

  const actionsFromDefs = (actionDefs ?? []).map((a) => ({
    value: a.actionCode,
    label: a.label ? `${a.label}（${a.actionCode}）` : a.actionCode,
    def: a,
  }));

  // 已定义动作里没有被动作条引用的（一键补齐用）
  const missing = (actionDefs ?? []).filter(
    (d) => !items.some((i) => i.actionCode === d.actionCode),
  );

  return (
    <div>
      {actionDefs !== null && actionDefs.length === 0 && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 10 }}
          message="该实体还没有定义任何动作"
          description="动作条引用的是实体动作定义（状态机边）。请先在实体的「动作定义」里登记动作，否则运行时按钮点不动。"
        />
      )}

      {items.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="动作条为空，运行时不显示任何操作按钮" style={{ padding: '18px 0' }} />
      )}

      {items.map((a, i) => {
        const def = (actionDefs ?? []).find((d) => d.actionCode === a.actionCode);
        const unknown = !!a.actionCode && actionDefs !== null && !def;
        return (
          <div
            key={i}
            style={{
              border: `1px solid ${unknown ? '#ffa940' : '#e6eaf0'}`,
              borderRadius: 10, marginBottom: 10, background: '#fff',
              boxShadow: focus?.idx === i ? '0 0 0 3px rgba(255,169,64,.18)' : '0 1px 2px rgba(16,24,40,.04)',
            }}
          >
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
              background: '#fafbfd', borderBottom: '1px solid #f0f0f0',
            }}>
              <ThunderboltOutlined style={{ color: '#1677ff' }} />
              <Text strong style={{ fontSize: 13 }}>{a.actionCode || '（未填动作码）'}</Text>
              {def?.label && <Tag color="blue" style={{ fontSize: 11 }}>{def.label}</Tag>}
              {def?.fromStatus && (
                <Tag style={{ fontSize: 11 }}>
                  {def.fromStatus} → {def.toStatus ?? '?'}
                </Tag>
              )}
              {def?.escape && <Tag color="red" style={{ fontSize: 11 }}>逃逸动作</Tag>}
              {unknown && <Tag color="orange" style={{ fontSize: 11 }}>实体中未定义此动作</Tag>}
              <span style={{ flex: 1 }} />
              <Tooltip title="上移（动作条从左到右按此顺序）">
                <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={i === 0}
                  onClick={() => onChange(moveAction(schema, i, i - 1))} />
              </Tooltip>
              <Tooltip title="下移">
                <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={i === items.length - 1}
                  onClick={() => onChange(moveAction(schema, i, i + 1))} />
              </Tooltip>
              <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => onChange(removeAction(schema, i))} />
            </div>

            <div style={{ padding: '10px 12px' }}>
              <div style={{ fontSize: 13, lineHeight: 1.7, marginBottom: 10 }}>{describeAction(a)}</div>

              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Space wrap>
                  <Text type="secondary" style={{ fontSize: 12, width: 60 }}>动作码</Text>
                  {actionsFromDefs.length ? (
                    <Select
                      size="small" showSearch optionFilterProp="label" style={{ minWidth: 200 }}
                      placeholder="选择已定义的动作" value={a.actionCode || undefined}
                      onChange={(v) => onChange(updateAction(schema, i, { actionCode: v }))}
                      options={actionsFromDefs.map((o) => ({ value: o.value, label: o.label }))}
                    />
                  ) : (
                    <Input size="small" style={{ width: 200 }} placeholder="动作编码（实体未定义动作时可手填）"
                      value={a.actionCode ?? ''}
                      onChange={(e) => onChange(updateAction(schema, i, { actionCode: e.target.value }))} />
                  )}
                  <Text type="secondary" style={{ fontSize: 12, width: 46 }}>排序</Text>
                  <Input size="small" style={{ width: 60 }} value={a.sortOrder ?? ''}
                    onChange={(e) => onChange(updateAction(schema, i, {
                      sortOrder: e.target.value === '' ? undefined : Number(e.target.value),
                    }))} />
                </Space>

                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    显示门控（通常按状态字段，如 status = DRAFT）
                  </Text>
                  <div style={{ marginTop: 4 }}>
                    <ConditionEditor
                      value={a.visibleWhen}
                      fieldOptions={fieldOptions}
                      onChange={(w) => onChange(updateAction(schema, i, {
                        visibleWhen: Object.keys(w).length ? w : undefined,
                      }))}
                    />
                  </div>
                </div>

                <Space wrap>
                  <Text type="secondary" style={{ fontSize: 12, width: 60 }}>二次确认</Text>
                  <Switch
                    size="small" checked={!!a.confirm}
                    onChange={(v) => onChange(updateAction(schema, i, { confirm: v ? { title: '' } : undefined }))}
                  />
                  {a.confirm && (
                    <>
                      <Input size="small" style={{ width: 150 }} placeholder="确认标题"
                        value={a.confirm.title ?? ''}
                        onChange={(e) => onChange(updateAction(schema, i, {
                          confirm: { ...a.confirm, title: e.target.value },
                        }))} />
                      <Input size="small" style={{ width: 220 }} placeholder="确认正文（可选）"
                        value={a.confirm.content ?? ''}
                        onChange={(e) => onChange(updateAction(schema, i, {
                          confirm: { ...a.confirm, content: e.target.value },
                        }))} />
                    </>
                  )}
                </Space>
              </Space>
            </div>
          </div>
        );
      })}

      <Space wrap>
        <Button size="small" icon={<PlusOutlined />} onClick={() => onChange(addAction(schema))}>
          添加动作
        </Button>
        {missing.length > 0 && (
          <Tooltip title={`还有 ${missing.length} 个已定义动作未出现在动作条：${missing.map((m) => m.actionCode).join('、')}`}>
            <Button
              size="small" type="dashed"
              onClick={() => {
                let next = schema;
                missing.forEach((m) => {
                  next = {
                    ...next,
                    actionBar: [...(next.actionBar ?? []), {
                      actionCode: m.actionCode,
                      sortOrder: (next.actionBar?.length ?? 0) + 1,
                      ...(m.fromStatus ? {
                        visibleWhen: {
                          fieldCode: m.statusField ?? 'status',
                          operator: 'eq' as const,
                          value: m.fromStatus,
                        },
                      } : {}),
                    }],
                  };
                });
                onChange(next);
              }}
            >
              补齐剩余 {missing.length} 个已定义动作
            </Button>
          </Tooltip>
        )}
      </Space>
    </div>
  );
}
