/**
 * 条件（when / visibleWhen）结构化编辑器。
 *
 * 替代原先"手写 JSON TextArea"的两个理由：
 * 1. 手写 JSON 无法保证 fieldCode 来自实体字段、operator 来自白名单 —— 配错要到运行时才发现；
 * 2. 审核者看不懂 JSON，而句子化描述需要结构化数据支撑。
 *
 * 嵌套（all/any/not）走「高级模式」JSON 编辑：可视化只覆盖高频的单一条件，
 * 避免为了 5% 的复杂场景把 95% 的界面做复杂（Salesforce/宜搭同策略）。
 */
import { Input, Select, Space, Tooltip, Typography } from 'antd';
import { CodeOutlined, FormOutlined } from '@ant-design/icons';
import type { Operator, UiCondition } from '../../utils/uiSchemaEngine';
import { OPERATORS, OPERATOR_MAP } from './constants';

const { Text } = Typography;

interface Props {
  value?: UiCondition;
  onChange: (next: UiCondition) => void;
  /** 可选字段（实体字段 code） */
  fieldOptions: { value: string; label: string }[];
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
  if (!simple) {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Text type="warning" style={{ fontSize: 11 }}>
          嵌套条件（all / any / not）暂用 JSON 编辑；切回可视化将丢弃嵌套结构。
        </Text>
        <Input.TextArea
          size="small"
          rows={3}
          defaultValue={JSON.stringify(c, null, 2)}
          onBlur={(e) => {
            try {
              onChange(JSON.parse(e.target.value) as UiCondition);
            } catch {
              /* 非法 JSON 不落库，保留编辑中文本 */
            }
          }}
        />
      </Space>
    );
  }

  const op = c.operator;
  const opMeta = OPERATOR_MAP[op ?? ''];
  const needsValue = opMeta ? opMeta.needsValue : true;
  const isArray = op ? ARRAY_OPS.includes(op) : false;
  const isFieldRef = c.valueType === 'field';

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
        onChange={(v) => patch({ fieldCode: v })}
      />
      <Select
        size="small"
        style={{ width: compact ? 100 : 120 }}
        placeholder="判断"
        options={OPERATORS.map((o) => ({ value: o.value, label: o.label }))}
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
        <CodeOutlined
          style={{ color: '#8c8c8c', cursor: 'pointer', fontSize: 13 }}
          onClick={() => onChange({ all: [c.fieldCode ? c : { fieldCode: '', operator: 'eq', value: '' }] })}
        />
      </Tooltip>
      {!simple && <FormOutlined />}
    </Space>
  );
}
