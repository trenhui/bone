/**
 * 条件（when / visibleWhen）结构化编辑器。
 *
 * 替代原先"手写 JSON TextArea"的两个理由：
 * 1. 手写 JSON 无法保证 fieldCode 来自实体字段、operator 来自白名单 —— 配错要到运行时才发现；
 * 2. 审核者看不懂 JSON，而句子化描述需要结构化数据支撑。
 *
 * 嵌套（all/any/not）走「高级模式」JSON 编辑：可视化只覆盖高频的单一条件，
 * 避免为了 5% 的复杂场景把 95% 的界面做复杂（Salesforce/宜搭同策略）。
 *
 * v1.2 补齐：
 * - 运算符按字段类型收窄（文本不该出现 between/gt——字符串比较"看起来生效"实则误配，
 *   对标 Salesforce 筛选器 / 宜搭条件）；换字段时算子不在白名单自动回落 eq；
 * - 嵌套 JSON 改**受控**（此前 defaultValue：撤销/快照恢复后文本框不刷新，用户看到旧结构），
 *   非法 JSON 显式报错（此前被 catch 静默吞掉，用户以为已保存）；
 * - 简单→嵌套的结构改写前二次确认（此前一键 {all:[…]} 且明示"切回丢弃嵌套"，不可逆无提示）。
 */
import { useEffect, useState } from 'react';
import { Input, Modal, Select, Space, Tooltip, Typography } from 'antd';
import { CodeOutlined, FormOutlined } from '@ant-design/icons';
import type { Operator, UiCondition } from '../../utils/uiSchemaEngine';
import { OPERATOR_MAP, operatorAllowedFor, operatorsForType } from './constants';

const { Text } = Typography;

interface Props {
  value?: UiCondition;
  onChange: (next: UiCondition) => void;
  /** 可选字段（实体字段 code + 类型；type 用于运算符收窄，缺失=不限制） */
  fieldOptions: { value: string; label: string; type?: string }[];
  /** 紧凑模式（用于动作条门控等窄空间） */
  compact?: boolean;
}

/** 值是否按数组输入（in/notIn/between） */
const ARRAY_OPS: Operator[] = ['in', 'notIn', 'between'];
const NO_VALUE_OPS: Operator[] = ['isNull', 'isNotNull'];

function isSimple(c?: UiCondition): boolean {
  return !c || (!c.all && !c.any && !c.not);
}

function toArrayValue(raw: string): unknown[] {
  return raw.split(',').map((x) => x.trim()).filter(Boolean);
}

export default function ConditionEditor({ value, onChange, fieldOptions, compact }: Props): React.ReactElement {
  const c = value ?? {};
  const simple = isSimple(c);

  /* ---------- 高级模式（嵌套条件） ---------- */
  const [jsonText, setJsonText] = useState(() => JSON.stringify(c, null, 2));
  const [jsonErr, setJsonErr] = useState<string | null>(null);
  // 受控同步：外部变化（撤销/重做/快照恢复/切规则）时文本框必须跟着刷新——
  // 此前用 defaultValue，外部变了框里还是旧结构（用户按旧结构编辑 → 覆盖新配置）。
  useEffect(() => {
    setJsonText(JSON.stringify(c, null, 2));
    setJsonErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  if (!simple) {
    const applyJson = (raw: string) => {
      try {
        const next = JSON.parse(raw) as UiCondition;
        if (!next || typeof next !== 'object' || Array.isArray(next)) throw new Error('顶层必须是条件对象');
        onChange(next);
        setJsonErr(null);
      } catch (e) {
        // 显式报错：此前静默 catch，用户以为已保存，实际编辑被丢弃
        setJsonErr(e instanceof Error ? e.message : String(e));
      }
    };
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Text type="warning" style={{ fontSize: 11 }}>
          嵌套条件（all / any / not）暂用 JSON 编辑；切回可视化将丢弃嵌套结构。
        </Text>
        <Input.TextArea
          size="small"
          rows={3}
          value={jsonText}
          status={jsonErr ? 'error' : undefined}
          onChange={(e) => { setJsonText(e.target.value); setJsonErr(null); }}
          onBlur={(e) => applyJson(e.target.value)}
        />
        {jsonErr && <Text type="danger" style={{ fontSize: 11 }}>JSON 非法，未写入：{jsonErr}</Text>}
      </Space>
    );
  }

  const op = c.operator;
  const opMeta = OPERATOR_MAP[op ?? ''];
  const needsValue = opMeta ? opMeta.needsValue : true;
  const isArray = op ? ARRAY_OPS.includes(op) : false;
  const isFieldRef = c.valueType === 'field';

  const fieldType = fieldOptions.find((o) => o.value === c.fieldCode)?.type;
  const typeOps = operatorsForType(fieldType);
  const opMismatch = !!op && !!fieldType && !operatorAllowedFor(op, fieldType);

  const patch = (p: Partial<UiCondition>) => onChange({ ...c, ...p });

  return (
    <Space size={4} wrap style={{ rowGap: 4 }}>
      <Select
        size="small"
        style={{ minWidth: compact ? 110 : 150 }}
        placeholder="字段"
        allowClear
        showSearch
        optionFilterProp="label"
        options={fieldOptions}
        value={c.fieldCode || undefined}
        onChange={(v) => {
          const nt = fieldOptions.find((o) => o.value === v)?.type;
          // 换字段后当前算子不在新类型白名单（如 文本→between）⇒ 回落 eq，禁误配
          if (op && !operatorAllowedFor(op, nt)) patch({ fieldCode: v, operator: 'eq' });
          else patch({ fieldCode: v });
        }}
      />
      <Tooltip title={opMismatch ? `当前算子「${opMeta?.label}」不适用于${fieldType}类型字段，建议改选` : undefined}>
        <Select
          size="small"
          style={{ width: compact ? 100 : 120, borderColor: opMismatch ? '#faad14' : undefined }}
          placeholder="判断"
          options={typeOps.map((o) => ({ value: o.value, label: o.label }))}
          value={op || undefined}
          onChange={(v) => {
            const next: UiCondition = { ...c, operator: v };
            // 切到无值操作符时清掉右值，避免遗留脏数据
            if (NO_VALUE_OPS.includes(v)) {
              delete next.value;
              delete next.valueType;
              delete next.compareField;
            }
            onChange(next);
          }}
        />
      </Tooltip>
      {opMismatch && <Text type="warning" style={{ fontSize: 11 }}>算子与字段类型不匹配</Text>}

      {needsValue && (
        <>
          <Tooltip title="右值可以是固定值，也可以是另一个字段（如：开始日期 早于 结束日期）">
            <Select
              size="small"
              style={{ width: 78 }}
              value={isFieldRef ? 'field' : 'fixed'}
              options={[{ value: 'fixed', label: '固定值' }, { value: 'field', label: '字段' }]}
              onChange={(v) => patch(
                v === 'field'
                  ? { valueType: 'field', compareField: c.compareField ?? '', value: undefined }
                  : { valueType: 'fixed', value: '', compareField: undefined },
              )}
            />
          </Tooltip>

          {isFieldRef ? (
            <Select
              size="small"
              style={{ minWidth: compact ? 110 : 150 }}
              placeholder="比较字段"
              allowClear
              showSearch
              optionFilterProp="label"
              options={fieldOptions}
              value={c.compareField || undefined}
              onChange={(v) => patch({ compareField: v })}
            />
          ) : (
            <Input
              size="small"
              style={{ width: compact ? 110 : 150 }}
              placeholder={isArray ? '多个用逗号分隔' : '值（$me/$myteam/$all）'}
              value={Array.isArray(c.value) ? (c.value as unknown[]).join(',') : (c.value as string | number | undefined) ?? ''}
              onChange={(e) => {
                const raw = e.target.value;
                patch({ value: isArray ? toArrayValue(raw) : raw });
              }}
            />
          )}
        </>
      )}

      <Tooltip title="切换到 JSON 模式以编写嵌套条件">
        <span
          role="button" tabIndex={0} aria-label="切换到 JSON 嵌套条件模式"
          style={{ color: '#8c8c8c', cursor: 'pointer', fontSize: 13, display: 'inline-flex' }}
          onClick={() => {
            // 结构改写不可逆（切回可视化会丢嵌套）：二次确认，明示影响
            Modal.confirm({
              title: '切换到 JSON 嵌套模式',
              content: '当前简单条件会被包装为 { all: [ … ] }；之后切回可视化将丢弃嵌套结构。',
              okText: '切换', cancelText: '取消',
              onOk: () => onChange({ all: [c.fieldCode ? c : { fieldCode: '', operator: 'eq', value: '' }] }),
            });
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (e.currentTarget as HTMLElement).click(); }
          }}
        >
          <CodeOutlined />
        </span>
      </Tooltip>
      {!simple && <FormOutlined />}
    </Space>
  );
}
