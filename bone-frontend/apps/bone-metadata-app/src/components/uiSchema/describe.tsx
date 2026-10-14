/**
 * 规则句子化：把 linkage/submit/action 的结构化配置渲染成自然语言句子。
 *
 * 为什么不做成"另写一段文案"：文案一旦独立存放就会与规则对象漂移（改了规则不改文案）。
 * 这里所有句子**由规则对象现算**，改规则即改句子 —— 无第二真源。
 *
 * 对标：Salesforce 验证规则把公式展示为可读描述、钉钉/宜搭的规则卡片。
 */
import { Typography } from 'antd';
import type {
  ActionBarItem, LinkageRule, SubmitRule, UiCondition,
} from '../../utils/uiSchemaEngine';
import { FUNCTION_MAP, opLabel } from './constants';

const { Text } = Typography;

/** 句子内的代码片段（字段/值），等宽 + 主色 */
function Code({ children }: { children: React.ReactNode }) {
  return (
    <Text code style={{ fontSize: 12, padding: '0 5px', borderRadius: 4 }}>{children}</Text>
  );
}

/** 值渲染：上下文变量翻译、字符串加引号、数组列枚举 */
function valueText(v: unknown): string {
  if (v === '$me') return '我（当前用户）';
  if (v === '$myteam') return '我的团队';
  if (v === '$all') return '全部';
  if (Array.isArray(v)) return `[${v.map(valueText).join(', ')}]`;
  if (v === null || v === undefined || v === '') return '空';
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return `"${String(v)}"`;
}

/** 条件 → 自然语言（递归支持 all/any/not，深度与引擎 MAX_COND_DEPTH 同口径） */
export function condText(c?: UiCondition, depth = 0): React.ReactNode {
  if (!c) return <Text type="secondary">（无条件 · 始终生效）</Text>;
  if (depth > 3) return <Text type="danger">（条件嵌套过深）</Text>;

  if (c.all?.length) {
    return <>{c.all.map((x, i) => (<span key={i}>{i > 0 && ' 且 '}{condText(x, depth + 1)}</span>))}</>;
  }
  if (c.any?.length) {
    return <>{c.any.map((x, i) => (<span key={i}>{i > 0 && ' 或 '}{condText(x, depth + 1)}</span>))}</>;
  }
  if (c.not) {
    return <>非（{condText(c.not, depth + 1)}）</>;
  }
  if (!c.fieldCode || !c.operator) return <Text type="secondary">（条件不完整 · 恒不生效）</Text>;

  const right = c.valueType === 'field'
    ? <>字段 <Code>{c.compareField || '?'}</Code></>
    : <Code>{valueText(c.value)}</Code>;

  return <><Code>{c.fieldCode}</Code> {opLabel(c.operator)} {right}</>;
}

/** 联动规则 → 句子 */
export function describeLinkage(r: LinkageRule): React.ReactNode {
  const thens = (r.then ?? []).map((t, i) => {
    const parts: string[] = [];
    if (t.visible !== undefined) parts.push(t.visible ? '显示' : '隐藏');
    if (t.required !== undefined) parts.push(t.required ? '必填' : '非必填');
    if (t.readonly !== undefined) parts.push(t.readonly ? '只读' : '可编辑');
    if (t.disabled !== undefined) parts.push(t.disabled ? '禁用' : '启用');
    if (t.notice) parts.push(`提示「${t.notice}」`);
    if ('value' in t && t.value !== undefined) parts.push(`赋值为 ${valueText(t.value)}`);
    return (
      <span key={i}>
        {i > 0 && '；'}
        <Code>{t.fieldCode || '?'}</Code> 变为 <b>{parts.join('、') || '—'}</b>
      </span>
    );
  });

  return (
    <>
      当 {condText(r.when)} 时，{thens.length ? thens : <Text type="warning">（未配置任何动作）</Text>}。
    </>
  );
}

/** 提交校验 → 句子 */
export function describeSubmit(r: SubmitRule): React.ReactNode {
  const fn = FUNCTION_MAP[r.function];
  const flds = (r.fields ?? []).length
    ? (r.fields ?? []).map((f, i) => (<span key={i}>{i > 0 && ' , '}<Code>{f}</Code></span>))
    : <Text type="danger">（未指定字段）</Text>;

  let right: React.ReactNode = <Text type="secondary">（由函数自身决定）</Text>;
  if (r.compare) {
    right = r.compare.valueType === 'field'
      ? <>字段 <Code>{r.compare.fieldCode || '?'}</Code></>
      : <Code>{valueText(r.compare.value)}</Code>;
  } else if (r.function === 'completeness') {
    right = <Code>{String(r.params?.minReasons ?? r.fields?.length ?? 0)}</Code>;
  }

  const prefix = r.when && Object.keys(r.when).length
    ? (<>仅当 {condText(r.when)} 时，</>)
    : null;

  return (
    <>
      {prefix}
      提交时校验 <b>{fn?.label ?? r.function}</b>（{flds}）{opLabel(r.operator)} {right}
      {' → '}
      {r.message ? <Text>“{r.message}”</Text> : <Text type="warning">（缺少提示文案）</Text>}
    </>
  );
}

/** 动作条项 → 句子 */
export function describeAction(a: ActionBarItem): React.ReactNode {
  const gate = a.visibleWhen && Object.keys(a.visibleWhen).length
    ? <>当 {condText(a.visibleWhen)} 时显示</>
    : <Text type="secondary">始终显示</Text>;
  return (
    <>
      动作 <Code>{a.actionCode || '（未填动作码）'}</Code> · {gate}
      {a.confirm?.title ? <> · 需确认</> : null}
    </>
  );
}
