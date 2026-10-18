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
  Button, Empty, Input, Modal, Segmented, Select, Space, Switch, Tag, Tooltip, Typography,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined,
  DownOutlined, UpOutlined, PauseCircleOutlined, PlayCircleOutlined,
} from '@ant-design/icons';
import { evalCond, runSubmit } from '../../utils/uiSchemaEngine';
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
  sentence, children, onRemove, onUp, onDown, canUp, canDown, defaultOpen, focusRing, onDryRun,
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
  /** 单条试运行（贴样例数据看条件是否命中；不配完也能验） */
  onDryRun?: () => void;
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
            <Switch size="small" aria-label="启用规则" checked={enabled} onChange={onToggleEnabled} />
            <Text type="secondary" style={{ fontSize: 11 }}>启用</Text>
          </Space>
        </Tooltip>
        {onDryRun && (
          <Tooltip title="试运行：贴一段样例数据，看这条规则的条件是否命中（不用先配完）">
            <Button size="small" type="text" aria-label="试运行规则" icon={<PlayCircleOutlined />} onClick={onDryRun} />
          </Tooltip>
        )}
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
  // type 随选项下传：ConditionEditor 按字段类型收窄运算符（文本不出现 between/gt）
  const fieldOptions = fields.map((f) => ({ value: f.code, label: `${f.displayName}（${f.code}）`, type: f.type }));

  /**
   * 单条试运行（dry-run）：贴样例数据 → 与运行时同源的 evalCond / linkageState /
   * runSubmit 求值，回显「条件是否命中 / 校验是否拦截」。
   * 为什么需要：此前只能配完整条规则 → 切右侧预览 → 选场景 → 逐字段手填样例值，
   * 配到一半的规则无法验证（对标 Zapier/n8n 的 Test step）。
   */
  const [dryRun, setDryRun] = useState<{ kind: 'linkage' | 'submit'; ri: number } | null>(null);
  const [dryText, setDryText] = useState('{}');
  const openDryRun = (k: 'linkage' | 'submit', ri: number) => {
    // 预填全部字段的空值骨架：用户只改关心的几个键即可试
    const skeleton = Object.fromEntries(fields.map((f) => [f.code, (f.type ?? '').toUpperCase() === 'BOOLEAN' ? false : '']));
    setDryText(JSON.stringify(skeleton, null, 2));
    setDryRun({ kind: k, ri });
  };

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
          onDryRun={() => openDryRun('linkage', ri)}
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
                  {/* 档位显隐（v1.1 §10.2-4：订阅档上下文命中才显示；缺 tier 的上下文不命中） */}
                  <Tooltip title="档位显隐：仅当前上下文 tier 命中列表时该字段可见（投研 free/pro 即此维度）">
                    <Select
                      size="small" mode="tags" style={{ width: 130 }} placeholder="档位（可选）"
                      value={t.tier ?? []}
                      onChange={(v) => onChange(updateLinkageThen(schema, ri, ti, { tier: v.length ? v : undefined }))}
                      options={[
                        { value: 'free', label: 'free' },
                        { value: 'pro', label: 'pro' },
                        { value: 'team', label: 'team' },
                        { value: 'enterprise', label: 'enterprise' },
                      ]}
                    />
                  </Tooltip>
                  {/* 赋值（协议 §3.5：仅 eq/null 两算子的保守口径，防联动写爆） */}
                  <Tooltip title="赋值：条件命中时把固定值写入该字段（保守口径，联动不做表达式）">
                    <Input size="small" style={{ width: 120 }} placeholder="赋值（可选）"
                      value={t.value === undefined || t.value === null ? '' : String(t.value)}
                      onChange={(e) => onChange(updateLinkageThen(schema, ri, ti, { value: e.target.value === '' ? undefined : e.target.value }))} />
                  </Tooltip>
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
            onDryRun={() => openDryRun('submit', ri)}
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

  /* ---------- 试运行 Modal（引擎同源求值） ---------- */
  let dryData: Record<string, unknown> | null = null;
  let dryParseErr: string | null = null;
  try {
    dryData = dryText.trim() ? (JSON.parse(dryText) as Record<string, unknown>) : {};
  } catch (e) {
    dryParseErr = e instanceof Error ? e.message : String(e);
  }

  const dryModal = (
    <Modal
      title={dryRun?.kind === 'linkage' ? '试运行 · 联动规则' : '试运行 · 提交校验'}
      open={!!dryRun}
      onCancel={() => setDryRun(null)}
      footer={null}
      width={560}
    >
      <Text type="secondary" style={{ fontSize: 12 }}>
        粘贴一段样例数据（JSON），用与运行时同源的引擎求值这一条规则。
      </Text>
      <Input.TextArea
        style={{ marginTop: 8, fontFamily: 'ui-monospace, Menlo, Consolas, monospace', fontSize: 12 }}
        rows={6} value={dryText} spellCheck={false}
        status={dryParseErr ? 'error' : undefined}
        onChange={(e) => setDryText(e.target.value)}
      />
      {dryParseErr && <Text type="danger" style={{ fontSize: 12 }}>JSON 非法：{dryParseErr}</Text>}
      {dryData && dryRun?.kind === 'linkage' && (() => {
        const rule = linkages[dryRun.ri];
        if (!rule) return null;
        const hit = evalCond(rule.when, dryData as Record<string, unknown>, {});
        return (
          <div style={{ marginTop: 10 }}>
            <Tag color={hit ? 'green' : 'default'} style={{ fontSize: 12 }}>
              {hit ? '● 条件命中，then 动作已生效' : '○ 条件未命中，规则不生效'}
            </Tag>
            {hit && (
              <div style={{ marginTop: 6, fontSize: 12, lineHeight: 1.8 }}>
                {(rule.then ?? []).map((t, ti) => {
                  const eff = [
                    t.visible !== undefined ? `可见=${t.visible}` : '',
                    t.required !== undefined ? `必填=${t.required}` : '',
                    t.readonly !== undefined ? `只读=${t.readonly}` : '',
                    t.disabled !== undefined ? `禁用=${t.disabled}` : '',
                    t.notice ? `提示「${t.notice}」` : '',
                    t.value !== undefined && t.value !== null ? `赋值=${String(t.value)}` : '',
                  ].filter(Boolean).join('，');
                  return <div key={ti}>· {t.fieldCode || '（未指定字段）'}：{eff || '（无实际动作）'}</div>;
                })}
                {(rule.then ?? []).length === 0 && <Text type="danger" style={{ fontSize: 12 }}>该规则没有 then 动作（自检报 error）</Text>}
              </div>
            )}
          </div>
        );
      })()}
      {dryData && dryRun?.kind === 'submit' && (() => {
        const rule = submits[dryRun.ri];
        if (!rule) return null;
        const baseRequired: Record<string, boolean> = {};
        fields.forEach((f) => { if (f.required) baseRequired[f.code] = true; });
        const res = runSubmit(JSON.parse(JSON.stringify(dryData)), [rule], [], {}, baseRequired);
        return (
          <div style={{ marginTop: 10 }}>
            {res.errors.length === 0 && res.warnings.length === 0 ? (
              <Tag color="green" style={{ fontSize: 12 }}>✓ 校验通过（该条不拦截）</Tag>
            ) : (
              <>
                {res.errors.map((m, i) => <div key={`e${i}`} style={{ color: '#ff4d4f', fontSize: 12 }}>· {m}</div>)}
                {res.warnings.map((m, i) => <div key={`w${i}`} style={{ color: '#faad14', fontSize: 12 }}>· {m}</div>)}
              </>
            )}
          </div>
        );
      })()}
    </Modal>
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
      {dryModal}
    </div>
  );
}
