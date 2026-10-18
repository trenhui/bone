/**
 * 动作条面板：配置哪些动作在什么状态下可见、以什么顺序排列。
 *
 * actionCode 的下拉选项来自实体真实动作定义（GET /entities/{id}/action-defs），
 * 而不是手填文本 —— 手填必然与状态机真源漂移（填了个不存在的码，运行时按钮点了没反应）。
 * 真源缺失时降级为自由输入，并给出提示，不静默放行。
 */
import { useMemo } from 'react';
import {
  Alert, Button, Empty, Input, InputNumber, Select, Space, Switch, Tag, Tooltip, Typography,
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
  /**
   * 展示序 = sortOrder 序（引擎 resolveVisibleActions 的唯一排序真源）。
   * 编辑操作全部按**原始下标**寻址（order[].i）——展示第 2 项对应的存储下标
   * 未必是 2（用户可直接编辑 sortOrder）。不映射就会「看见的第 2 项改的是
   * 存储的第 2 项」错位；这也从根上消除了此前的双真源（数组序 vs sortOrder）。
   */
  const order = useMemo(
    () => (schema.actionBar ?? [])
      .map((a, i) => ({ a, i }))
      .sort((x, y) => (x.a.sortOrder ?? x.i + 1) - (y.a.sortOrder ?? y.i + 1)),
    [schema.actionBar],
  );
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

      {order.map(({ a, i }, di) => {
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
              <Tooltip title="上移（与 sortOrder 同步交换，运行期即此顺序）">
                <Button size="small" type="text" aria-label="上移动作" icon={<ArrowUpOutlined />} disabled={di === 0}
                  onClick={() => onChange(moveAction(schema, i, order[di - 1].i))} />
              </Tooltip>
              <Tooltip title="下移（与 sortOrder 同步交换，运行期即此顺序）">
                <Button size="small" type="text" aria-label="下移动作" icon={<ArrowDownOutlined />} disabled={di === order.length - 1}
                  onClick={() => onChange(moveAction(schema, i, order[di + 1].i))} />
              </Tooltip>
              <Button size="small" type="text" danger aria-label="删除动作" icon={<DeleteOutlined />} onClick={() => onChange(removeAction(schema, i))} />
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
                    size="small" aria-label="二次确认" checked={!!a.confirm}
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

                {/* HITL 决策配置（v1.1 §10.2-6：审查/核赔的采纳-驳回-改判三态） */}
                <Space wrap>
                  <Text type="secondary" style={{ fontSize: 12, width: 60 }}>决策类型</Text>
                  <Select
                    size="small" style={{ width: 200 }} value={a.decisionKind ?? 'open'}
                    onChange={(v) => onChange(updateAction(schema, i, { decisionKind: v as 'open' | 'hitl' }))}
                    options={[
                      { value: 'open', label: '开放式（普通动作）' },
                      { value: 'hitl', label: '人机协同（采纳/驳回/改判）' },
                    ]}
                  />
                  {a.decisionKind === 'hitl' && (
                    <Tooltip title="hitl 动作在运行时应弹理由选择（reasonTypes），处置理由随审计留痕">
                      <Tag color="purple" style={{ fontSize: 11 }}>运行时报理由入审计</Tag>
                    </Tooltip>
                  )}
                </Space>

                {a.decisionKind === 'hitl' && (
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      理由分类（reasonTypes · 如 理赔六类拒赔理由 / 采购审查六类）
                    </Text>
                    <Select
                      size="small" mode="tags" style={{ width: '100%', marginTop: 4 }}
                      placeholder="输入后回车，如 资料不齐 / 超保险责任 / 既往症"
                      value={a.reasonTypes ?? []}
                      onChange={(v) => onChange(updateAction(schema, i, { reasonTypes: v as string[] }))}
                    />
                  </div>
                )}

                <Space wrap>
                  <Text type="secondary" style={{ fontSize: 12, width: 60 }}>处置轮次</Text>
                  <InputNumber
                    size="small" style={{ width: 90 }} min={1} placeholder="不限"
                    value={a.maxRounds ?? undefined}
                    onChange={(v: number | null) => onChange(updateAction(schema, i, { maxRounds: v ?? undefined }))}
                  />                  <Text type="secondary" style={{ fontSize: 11 }}>
                    最多 N 轮，超限转人工（采购审查 3 轮即此）
                  </Text>
                </Space>

                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    双人复核条件（requireDualApproveWhen · 满足时需第二人确认，如 金额≥1 万且高风险）
                  </Text>
                  <div style={{ marginTop: 4 }}>
                    <ConditionEditor
                      value={a.requireDualApproveWhen}
                      fieldOptions={fieldOptions}
                      onChange={(w) => onChange(updateAction(schema, i, {
                        requireDualApproveWhen: Object.keys(w).length ? w : undefined,
                      }))}
                    />
                  </div>
                </div>
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
