/**
 * 条件（when / visibleWhen）结构化编辑器。
 *
 * 三种形态，同一数据模型（协议 UiCondition）：
 * 1. 简单条件：字段 / 算子 / 右值 一行式（高频场景，Salesforce/宜搭同策略）；
 * 2. 嵌套组：all/any/not **可视化树**（递归 GroupTree，可增删子条件/子组、
 *    切换且/或、单子组可无损拆回简单条件）；
 * 3. JSON 模式：与可视化共用同一模型，可随时互切不丢结构（此前的 JSON 是
 *    「单向门」——切过去就回不来，故必须二次确认；现在不需要了）。
 *
 * v1.2 补齐：
 * - 运算符按字段类型收窄（文本不该出现 between/gt——字符串比较"看起来生效"实则误配，
 *   对标 Salesforce 筛选器 / 宜搭条件）；换字段时算子不在白名单自动回落 eq；
 * - JSON 模式**受控**（此前 defaultValue：撤销/快照恢复后文本框不刷新，用户看到旧结构），
 *   非法 JSON 显式报错（此前被 catch 静默吞掉，用户以为已保存）；
 * - 嵌套深度上限 MAX_GROUP_DEPTH 与引擎 MAX_COND_DEPTH(=3) 对齐：
 *   超限嵌套在引擎里 fail-closed 不命中，编辑器从源头就不让配出来。
 */
import { useEffect, useMemo, useState } from 'react';
import { Button, Input, Segmented, Select, Space, Tag, Tooltip, Typography } from 'antd';
import { CodeOutlined, DeleteOutlined, FormOutlined, PlusOutlined } from '@ant-design/icons';
import type { Operator, UiCondition } from '@bone/ui-schema';
import { OPERATOR_MAP, operatorAllowedFor, operatorsForType } from './constants';
import {
  MAX_GROUP_DEPTH, addChild, childrenOf, emptyGroup, emptyLeaf, groupKindOf, isGroup,
  removeChildAt, setGroupKind, unwrapGroup, updateChildAt,
} from './conditionOps';

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

function toArrayValue(raw: string): unknown[] {
  return raw.split(',').map((x) => x.trim()).filter(Boolean);
}

/* ---------------- 简单条件行 ---------------- */

function SimpleCondition({ cond, fieldOptions, compact, onChange }: {
  cond: UiCondition;
  fieldOptions: Props['fieldOptions'];
  compact?: boolean;
  /** 收到整个更新后的叶条件（不是 patch——组树里按整娃替换，口径必须一致） */
  onChange: (next: UiCondition) => void;
}): React.ReactElement {
  const op = cond.operator;
  const opMeta = OPERATOR_MAP[op ?? ''];
  const needsValue = opMeta ? opMeta.needsValue : true;
  const isArray = op ? ARRAY_OPS.includes(op) : false;
  const isFieldRef = cond.valueType === 'field';
  const fieldType = fieldOptions.find((o) => o.value === cond.fieldCode)?.type;
  // 运算符下拉的 options 依赖 fieldType（真动态，不能用模块单例）⇒ 按 type 缓存：
  // 同一 type 的 options 结构相同，antd 拿到同引用就不会每帧重建 flatten。
  // 规则面板里每个联动条件都渲染一个 ConditionEditor，不缓存就是「条件数 × 帧」重建。
  const typeOps = useMemo(
    () => operatorsForType(fieldType).map((o) => ({ value: o.value, label: o.label })),
    [fieldType],
  );
  const opMismatch = !!op && !!fieldType && !operatorAllowedFor(op, fieldType);

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
        value={cond.fieldCode || undefined}
        onChange={(v) => {
          const nt = fieldOptions.find((o) => o.value === v)?.type;
          // 换字段后当前算子不在新类型白名单（如 文本→between）⇒ 回落 eq，禁误配
          if (op && !operatorAllowedFor(op, nt)) onChange({ ...cond, fieldCode: v, operator: 'eq' });
          else onChange({ ...cond, fieldCode: v });
        }}
      />
      <Tooltip title={opMismatch ? `当前算子「${opMeta?.label}」不适用于${fieldType}类型字段，建议改选` : undefined}>
        <Select
          size="small"
          style={{ width: compact ? 100 : 120, borderColor: opMismatch ? '#faad14' : undefined }}
          placeholder="判断"
          options={typeOps}
          value={op || undefined}
          onChange={(v) => {
            const next: UiCondition = { ...cond, operator: v };
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
              onChange={(v) => onChange(
                v === 'field'
                  ? { ...cond, valueType: 'field', compareField: cond.compareField ?? '', value: undefined }
                  : { ...cond, valueType: 'fixed', value: '', compareField: undefined },
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
              value={cond.compareField || undefined}
              onChange={(v) => onChange({ ...cond, compareField: v })}
            />
          ) : (
            <Input
              size="small"
              style={{ width: compact ? 110 : 150 }}
              placeholder={isArray ? '多个用逗号分隔' : '值（$me/$myteam/$all）'}
              value={Array.isArray(cond.value) ? (cond.value as unknown[]).join(',') : (cond.value as string | number | undefined) ?? ''}
              onChange={(e) => {
                const raw = e.target.value;
                onChange({ ...cond, value: isArray ? toArrayValue(raw) : raw });
              }}
            />
          )}
        </>
      )}
    </Space>
  );
}

/* ---------------- 嵌套组树 ---------------- */

function GroupTree({ cond, depth, fieldOptions, compact, onChange }: {
  cond: UiCondition;
  /** 当前组的树深度（root = 1） */
  depth: number;
  fieldOptions: Props['fieldOptions'];
  compact?: boolean;
  /** 收到整个更新后的组 */
  onChange: (next: UiCondition) => void;
}): React.ReactElement {
  const kind = groupKindOf(cond);
  const kids = childrenOf(cond);
  const canNest = depth < MAX_GROUP_DEPTH;
  const unwrappable = unwrapGroup(cond);

  return (
    <div style={{
      border: '1px solid #e6eaf0', borderRadius: 8, padding: '6px 8px',
      background: depth === 1 ? '#fafbfd' : '#fff',
    }}>
      <Space size={4} wrap style={{ marginBottom: kids.length ? 6 : 0 }}>
        {kind === 'not' ? (
          <Tag color="orange" style={{ margin: 0 }}>不满足（非）</Tag>
        ) : (
          <Segmented
            size="small" value={kind}
            onChange={(v) => onChange(setGroupKind(cond, v as 'all' | 'any'))}
            options={[
              { value: 'all', label: '全部满足（且）' },
              { value: 'any', label: '任一满足（或）' },
            ]}
          />
        )}
        {canNest && (
          <>
            <Button size="small" type="link" icon={<PlusOutlined />} onClick={() => onChange(addChild(cond, emptyLeaf()))}>
              条件
            </Button>
            <Button size="small" type="link" icon={<PlusOutlined />} onClick={() => onChange(addChild(cond, emptyGroup()))}>
              条件组
            </Button>
          </>
        )}
        {!canNest && (
          <Tooltip title={`嵌套深度上限 ${MAX_GROUP_DEPTH} 层（与引擎 fail-closed 阈值对齐，再深引擎不命中）`}>
            <Tag style={{ margin: 0, fontSize: 11 }}>已达嵌套上限</Tag>
          </Tooltip>
        )}
        {unwrappable && (
          <Tooltip title="只有一个子条件时可无损拆回简单条件">
            <Button size="small" type="link" onClick={() => onChange(unwrappable)}>拆回简单条件</Button>
          </Tooltip>
        )}
      </Space>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {kids.map((child, i) => (
          <div key={i} style={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              {isGroup(child) ? (
                <GroupTree
                  cond={child} depth={depth + 1} fieldOptions={fieldOptions} compact={compact}
                  onChange={(n) => onChange(updateChildAt(cond, i, n))}
                />
              ) : (
                <SimpleCondition
                  cond={child} fieldOptions={fieldOptions} compact={compact}
                  onChange={(n) => onChange(updateChildAt(cond, i, n))}
                />
              )}
            </div>
            {kind !== 'not' && (
              <Tooltip title="移除该条件">
                <Button
                  size="small" type="text" danger aria-label="移除条件" icon={<DeleteOutlined />}
                  onClick={() => onChange(removeChildAt(cond, i))}
                />
              </Tooltip>
            )}
            {kind !== 'not' && i < kids.length - 1 && (
              <span style={{ alignSelf: 'center', color: '#bfbfbf', fontSize: 11 }}>{kind === 'any' ? '或' : '且'}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ---------------- 主组件 ---------------- */

export default function ConditionEditor({ value, onChange, fieldOptions, compact }: Props): React.ReactElement {
  const c = value ?? {};
  const grouped = isGroup(c);

  /* ---------- JSON 模式（组结构的文本编辑；与可视化共用模型，可无损互切） ---------- */
  const [jsonMode, setJsonMode] = useState(false);
  const [jsonText, setJsonText] = useState(() => JSON.stringify(c, null, 2));
  const [jsonErr, setJsonErr] = useState<string | null>(null);
  // 受控同步：外部变化（撤销/重做/快照恢复/切规则）时文本框必须跟着刷新——
  // 此前用 defaultValue，外部变了框里还是旧结构（用户按旧结构编辑 → 覆盖新配置）。
  useEffect(() => {
    setJsonText(JSON.stringify(c, null, 2));
    setJsonErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  if (grouped) {
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
      <Space direction="vertical" size={6} style={{ width: '100%' }}>
        <Space size={4} wrap>
          <Button
            size="small" type={jsonMode ? 'link' : 'primary'} icon={<FormOutlined />}
            onClick={() => setJsonMode(false)}
          >
            可视化
          </Button>
          <Button
            size="small" type={jsonMode ? 'primary' : 'link'} icon={<CodeOutlined />}
            onClick={() => setJsonMode(true)}
          >
            JSON
          </Button>
          <Text type="secondary" style={{ fontSize: 11 }}>两种模式共用同一结构，可随时互切不丢数据</Text>
        </Space>
        {jsonMode ? (
          <>
            <Input.TextArea
              size="small"
              rows={4}
              value={jsonText}
              status={jsonErr ? 'error' : undefined}
              onChange={(e) => { setJsonText(e.target.value); setJsonErr(null); }}
              onBlur={(e) => applyJson(e.target.value)}
            />
            {jsonErr && <Text type="danger" style={{ fontSize: 11 }}>JSON 非法，未写入：{jsonErr}</Text>}
          </>
        ) : (
          <GroupTree
            cond={c} depth={1} fieldOptions={fieldOptions} compact={compact}
            onChange={onChange}
          />
        )}
      </Space>
    );
  }

  /* ---------- 简单条件 ---------- */
  return (
    <Space size={4} wrap style={{ rowGap: 4 }}>
      <SimpleCondition cond={c} fieldOptions={fieldOptions} compact={compact} onChange={onChange} />
      <Tooltip title="升级为嵌套条件组（且/或/非组合；只有一个子条件时可无损拆回）">
        <Button
          size="small" type="text" aria-label="升级为嵌套条件组"
          onClick={() => onChange(c.fieldCode ? { all: [c] } : emptyGroup())}
        >
          嵌套
        </Button>
      </Tooltip>
    </Space>
  );
}
