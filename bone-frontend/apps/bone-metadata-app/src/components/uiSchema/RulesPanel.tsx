/**
 * 规则面板：联动规则 + 提交校验。
 *
 * 三条设计原则：
 * 1. **句子优先**：卡片首行是自然语言句子（由规则对象现算），展开才是表单。
 *    审核者扫一眼句子就能判断规则对不对，不需要读 JSON。
 * 2. **可独立启停**：每条带 enabled 开关。线上误伤时"停用"而不是"删除"——
 *    删了就丢失"曾经配过什么"的审计轨迹（协议 v1.2 待评审）。
 * 3. **停用要有视觉共识**：置灰 + 删除线 + 「已停用」角标，避免"看起来关了其实还在跑"。
 */
import { useState } from 'react';
import {
  Button, Empty, Input, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined,
  DownOutlined, UpOutlined, PauseCircleOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema, Operator, SubmitFunction } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { FUNCTIONS, OPERATORS, VERIFY_TYPES } from './constants';
import { describeLinkage, describeSubmit } from './describe';
import ConditionEditor from './ConditionEditor';
import {
  addLinkage, addLinkageThen, addSubmit, moveLinkage, moveSubmit,
  removeLinkage, removeLinkageThen, removeSubmit, updateLinkage, updateLinkageThen, updateSubmit,
} from './schemaOps';

const { Text, Paragraph } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  /** 默认展开的分组 */
  defaultKind?: 'linkage' | 'submit';
  /** 高亮定位 */
  focus?: { ri?: number; tab?: string };
}

/** 规则卡外壳：统一处理停用视觉、标题区、展开收起 */
function RuleCard({
  title, code, enabled, onToggleEnabled, level, levelHint,
  sentence, children, onRemove, onUp, onDown, canUp, canDown, defaultOpen, focusRing,
}: {
  title: string;
  code?: string;
  enabled: boolean;
  onToggleEnabled: (v: boolean) => void;
  level?: { label: string; color: string; hint: string };
  levelHint?: string;
  sentence: React.ReactNode;
  children?: React.ReactNode;
  onRemove: () => void;
  onUp: () => void;
  onDown: () => void;
  canUp: boolean;
  canDown: boolean;
  defaultOpen?: boolean;
  focusRing?: boolean;
}) {
  const [open, setOpen] = useState(!!defaultOpen);
  const off = !enabled;

  return (
    <div data-tour="rule-card" style={{
      border: `1px solid ${focusRing ? '#ffa940' : off ? '#f0f0f0' : '#e6eaf0'}`,
      borderRadius: 10, marginBottom: 10, overflow: 'hidden',
      background: off ? '#fafafa' : '#fff',
      opacity: off ? 0.72 : 1,
      boxShadow: focusRing ? '0 0 0 3px rgba(255,169,64,.18)' : '0 1px 2px rgba(16,24,40,.04)',
      transition: 'all .18s',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
        background: off ? '#fbfbfb' : '#fafbfd', borderBottom: open ? '1px solid #f0f0f0' : undefined,
      }}>
        <Text strong style={{ fontSize: 13, textDecoration: off ? 'line-through' : undefined }}>
          {title || '（未命名）'}
        </Text>
        {code && <Text code style={{ fontSize: 11 }}>{code}</Text>}
        {level && (
          <Tooltip title={level.hint ?? levelHint}>
            <Tag color={level.color} style={{ fontSize: 11, margin: 0 }}>{level.label}</Tag>
          </Tooltip>
        )}
        {off && (
          <Tag icon={<PauseCircleOutlined />} style={{ fontSize: 11, margin: 0 }}>已停用 · 运行期不生效</Tag>
        )}
        <span style={{ flex: 1 }} />
        <Tooltip title="停用后前端渲染与后端强制都整条跳过（不是隐藏提示，是不执行）">
          <Space size={4}>
            <Switch size="small" checked={enabled} onChange={onToggleEnabled} />
            <Text type="secondary" style={{ fontSize: 11 }}>启用</Text>
          </Space>
        </Tooltip>
        <Tooltip title="上移（规则按顺序执行，后者覆盖前者）">
          <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={canUp ? !canUp : true} onClick={onUp} />
        </Tooltip>
        <Tooltip title="下移">
          <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={canDown ? !canDown : true} onClick={onDown} />
        </Tooltip>
        <Tooltip title="删除规则">
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={onRemove} />
        </Tooltip>
        <Button
          size="small" type="text"
          icon={open ? <UpOutlined /> : <DownOutlined />}
          onClick={() => setOpen(!open)}
        />
      </div>

      <div style={{ padding: '10px 12px' }}>
        <div style={{
          fontSize: 13, lineHeight: 1.75, color: off ? '#8c8c8c' : '#262626',
          textDecoration: off ? 'line-through' : undefined,
        }}>
          {sentence}
        </div>
        {open && <div style={{ marginTop: 12 }}>{children}</div>}
      </div>
    </div>
  );
}

export default function RulesPanel({ schema, onChange, fields, defaultKind = 'linkage', focus }: Props): React.ReactElement {
  const [kind, setKind] = useState<'linkage' | 'submit'>(defaultKind);
  const fieldOptions = fields.map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）` }));

  const linkages = schema.linkageRules ?? [];
  const submits = schema.submitRules ?? [];

  /* ---------- 联动 ---------- */
  const renderLinkage = () => (
    <>
      {linkages.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有联动规则" style={{ padding: '18px 0' }} />
      )}
      {linkages.map((r, ri) => (
        <RuleCard
          key={ri}
          title={r.name ?? ''}
          code={r.code}
          enabled={r.enabled !== false}
          onToggleEnabled={(v) => onChange(updateLinkage(schema, ri, { enabled: v ? undefined : false }))}
          sentence={describeLinkage(r)}
          focusRing={kind === 'linkage' && focus?.ri === ri}
          onRemove={() => onChange(removeLinkage(schema, ri))}
          onUp={() => onChange(moveLinkage(schema, ri, ri - 1))}
          onDown={() => onChange(moveLinkage(schema, ri, ri + 1))}
          canUp={ri > 0}
          canDown={ri < linkages.length - 1}
          defaultOpen={ri === linkages.length - 1}
        >
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space wrap>
              <Text type="secondary" style={{ fontSize: 12, width: 46 }}>名称</Text>
              <Input size="small" style={{ width: 180 }} value={r.name ?? ''}
                onChange={(e) => onChange(updateLinkage(schema, ri, { name: e.target.value }))} />
              <Text type="secondary" style={{ fontSize: 12, width: 46 }}>编码</Text>
              <Input size="small" style={{ width: 150 }} placeholder="前后端对账用" value={r.code ?? ''}
                onChange={(e) => onChange(updateLinkage(schema, ri, { code: e.target.value }))} />
            </Space>

            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>当（when）</Text>
              <div style={{ marginTop: 4 }}>
                <ConditionEditor
                  value={r.when}
                  fieldOptions={fieldOptions}
                  onChange={(w) => onChange(updateLinkage(schema, ri, { when: w }))}
                />
              </div>
            </div>

            <div>
              <Space style={{ marginBottom: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>则（then）</Text>
                <Button
                  size="small" type="link" icon={<PlusOutlined />}
                  onClick={() => onChange(addLinkageThen(schema, ri, fields[0]?.code ?? ''))}
                >
                  添加动作
                </Button>
              </Space>
              {(r.then ?? []).map((t, ti) => (
                <div key={ti} style={{
                  display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
                  padding: '6px 8px', border: '1px solid #f0f0f0', borderRadius: 8, marginBottom: 6, background: '#fafbfd',
                }}>
                  <Select size="small" style={{ minWidth: 150 }} showSearch optionFilterProp="label"
                    options={fieldOptions} value={t.fieldCode || undefined}
                    onChange={(v) => onChange(updateLinkageThen(schema, ri, ti, { fieldCode: v }))} />
                  {(['visible', 'required', 'readonly', 'disabled'] as const).map((act) => {
                    const cur = t[act];
                    return (
                      <Select
                        key={act} size="small" style={{ width: 92 }}
                        value={cur === undefined ? 'skip' : cur ? 'on' : 'off'}
                        onChange={(v) => onChange(updateLinkageThen(schema, ri, ti, {
                          [act]: v === 'skip' ? undefined : v === 'on',
                        } as never))}
                        options={[
                          { value: 'skip', label: `不改${act === 'visible' ? '可见性' : act === 'required' ? '必填' : act === 'readonly' ? '只读' : '禁用'}` },
                          { value: 'on', label: act === 'visible' ? '显示' : act === 'required' ? '必填' : act === 'readonly' ? '只读' : '禁用' },
                          { value: 'off', label: act === 'visible' ? '隐藏' : act === 'required' ? '非必填' : act === 'readonly' ? '可编辑' : '启用' },
                        ]}
                      />
                    );
                  })}
                  <Input size="small" style={{ width: 160 }} placeholder="提示文案（可选）" value={t.notice ?? ''}
                    onChange={(e) => onChange(updateLinkageThen(schema, ri, ti, { notice: e.target.value || undefined }))} />
                  <Button size="small" type="text" danger icon={<DeleteOutlined />}
                    onClick={() => onChange(removeLinkageThen(schema, ri, ti))} />
                </div>
              ))}
            </div>
          </Space>
        </RuleCard>
      ))}
      <Button block size="small" icon={<PlusOutlined />}
        onClick={() => onChange(addLinkage(schema, fields[0]?.code ?? ''))}>
        添加联动规则
      </Button>
    </>
  );

  /* ---------- 提交校验 ---------- */
  const renderSubmit = () => (
    <>
      {submits.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有提交校验" style={{ padding: '18px 0' }} />
      )}
      {submits.map((r, ri) => {
        const vt = VERIFY_TYPES.find((v) => v.value === (r.verifyType ?? 'ERROR'))!;
        const fnMeta = FUNCTIONS.find((f) => f.value === r.function);
        return (
          <RuleCard
            key={ri}
            title={r.name ?? ''}
            code={r.code}
            enabled={r.enabled !== false}
            onToggleEnabled={(v) => onChange(updateSubmit(schema, ri, { enabled: v ? undefined : false }))}
            level={{ label: vt.label, color: vt.color, hint: vt.hint }}
            sentence={describeSubmit(r)}
            focusRing={kind === 'submit' && focus?.ri === ri}
            onRemove={() => onChange(removeSubmit(schema, ri))}
            onUp={() => onChange(moveSubmit(schema, ri, ri - 1))}
            onDown={() => onChange(moveSubmit(schema, ri, ri + 1))}
            canUp={ri > 0}
            canDown={ri < submits.length - 1}
            defaultOpen={ri === submits.length - 1}
          >
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Space wrap>
                <Text type="secondary" style={{ fontSize: 12, width: 46 }}>名称</Text>
                <Input size="small" style={{ width: 170 }} value={r.name ?? ''}
                  onChange={(e) => onChange(updateSubmit(schema, ri, { name: e.target.value }))} />
                <Text type="secondary" style={{ fontSize: 12, width: 46 }}>编码</Text>
                <Input size="small" style={{ width: 140 }} placeholder="前后端对账用" value={r.code ?? ''}
                  onChange={(e) => onChange(updateSubmit(schema, ri, { code: e.target.value }))} />
              </Space>

              <Space wrap>
                <Text type="secondary" style={{ fontSize: 12, width: 46 }}>函数</Text>
                <Select size="small" style={{ width: 130 }} value={r.function}
                  onChange={(v) => onChange(updateSubmit(schema, ri, { function: v as SubmitFunction }))}
                  options={FUNCTIONS.map((f) => ({ value: f.value, label: f.label }))} />
                <Tooltip title={fnMeta?.hint}>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    左值={fnMeta?.left} · 右值={fnMeta?.right ?? '由字段/参数决定'}
                  </Text>
                </Tooltip>
              </Space>

              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>取数字段（支持 items.amount 这类路径）</Text>
                <Select
                  size="small" mode="tags" style={{ width: '100%', marginTop: 4 }}
                  placeholder="输入后回车，如 items.amount"
                  value={r.fields ?? []}
                  onChange={(v) => onChange(updateSubmit(schema, ri, { fields: v as string[] }))}
                  options={fieldOptions}
                />
              </div>

              <Space wrap>
                <Text type="secondary" style={{ fontSize: 12, width: 46 }}>比较</Text>
                <Select size="small" style={{ width: 110 }} value={r.operator}
                  onChange={(v) => onChange(updateSubmit(schema, ri, { operator: v as Operator }))}
                  options={OPERATORS.map((o) => ({ value: o.value, label: o.label }))} />
                {fnMeta?.right && (
                  <>
                    <Select size="small" style={{ width: 80 }}
                      value={r.compare?.valueType ?? 'fixed'}
                      onChange={(v) => onChange(updateSubmit(schema, ri, {
                        compare: { valueType: v, value: '', fieldCode: undefined },
                      }))}
                      options={[{ value: 'fixed', label: '固定值' }, { value: 'field', label: '字段' }]} />
                    {r.compare?.valueType === 'field' ? (
                      <Select size="small" style={{ minWidth: 150 }} showSearch optionFilterProp="label"
                        placeholder="比较字段" options={fieldOptions} value={r.compare?.fieldCode || undefined}
                        onChange={(v) => onChange(updateSubmit(schema, ri, {
                          compare: { valueType: 'field', fieldCode: v },
                        }))} />
                    ) : (
                      <Input size="small" style={{ width: 120 }} placeholder="比较值"
                        value={String(r.compare?.value ?? '')}
                        onChange={(e) => onChange(updateSubmit(schema, ri, {
                          compare: { valueType: 'fixed', value: e.target.value },
                        }))} />
                    )}
                  </>
                )}
                {r.function === 'completeness' && (
                  <>
                    <Text type="secondary" style={{ fontSize: 12 }}>最少要素</Text>
                    <Input size="small" style={{ width: 70 }} placeholder={String(r.fields?.length ?? 0)}
                      value={r.params?.minReasons ?? ''}
                      onChange={(e) => onChange(updateSubmit(schema, ri, {
                        params: { ...r.params, minReasons: e.target.value === '' ? undefined : Number(e.target.value) },
                      }))} />
                  </>
                )}
                {(r.function === 'balance' || r.function === 'consistency') && (
                  <>
                    <Text type="secondary" style={{ fontSize: 12 }}>容差</Text>
                    <Input size="small" style={{ width: 70 }} placeholder="0.005"
                      value={r.params?.tolerance ?? ''}
                      onChange={(e) => onChange(updateSubmit(schema, ri, {
                        params: { ...r.params, tolerance: e.target.value === '' ? undefined : Number(e.target.value) },
                      }))} />
                  </>
                )}
              </Space>

              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  生效前置（可选，如仅 status=REJECTING 时才校验）
                </Text>
                <div style={{ marginTop: 4 }}>
                  <ConditionEditor
                    value={r.when}
                    fieldOptions={fieldOptions}
                    onChange={(w) => onChange(updateSubmit(schema, ri, {
                      when: Object.keys(w).length ? w : undefined,
                    }))}
                  />
                </div>
              </div>

              <Space wrap>
                <Text type="secondary" style={{ fontSize: 12, width: 46 }}>级别</Text>
                <Segmented
                  size="small" value={r.verifyType ?? 'ERROR'}
                  onChange={(v) => onChange(updateSubmit(schema, ri, { verifyType: v as 'ERROR' | 'WARNING' }))}
                  options={VERIFY_TYPES.map((v) => ({ value: v.value, label: v.label }))}
                />
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {r.verifyType === 'WARNING' ? '弹确认后可继续提交' : '阻断提交'}
                </Text>
              </Space>

              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>失败提示（用户可见）</Text>
                <Input size="small" style={{ marginTop: 4 }} placeholder="如：费用明细合计必须等于申报金额"
                  value={r.message ?? ''}
                  onChange={(e) => onChange(updateSubmit(schema, ri, { message: e.target.value }))} />
              </div>
            </Space>
          </RuleCard>
        );
      })}
      <Button block size="small" icon={<PlusOutlined />} onClick={() => onChange(addSubmit(schema))}>
        添加提交校验
      </Button>
    </>
  );

  return (
    <div>
      <Segmented
        block size="small" style={{ marginBottom: 12 }}
        value={kind} onChange={(v) => setKind(v as 'linkage' | 'submit')}
        options={[
          { value: 'linkage', label: `联动规则（${linkages.length}）` },
          { value: 'submit', label: `提交校验（${submits.length}）` },
        ]}
      />
      <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
        {kind === 'linkage'
          ? '联动只在前端跑（体验拦截），按顺序执行、后者覆盖前者；安全底线仍在后端。'
          : '前端先拦（体验），后端同 code 强制（安全底线不后移）。停用规则前后端都会跳过。'}
      </Paragraph>
      {kind === 'linkage' ? renderLinkage() : renderSubmit()}
    </div>
  );
}
