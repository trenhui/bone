import type { MouseEvent, ReactElement } from 'react';
import { Button, Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import { buildCase360Link, isSafeClaimId, type CaseTab } from '../workbench/caseDeepLinks';

/**
 * 案件 ID / 报案号 → 案件 360 详情页的唯一「可点击单元格」入口。
 *
 * <p>为什么要这个组件
 * ----------------
 * 案件 360 页面（`/case?claimId=...&tab=...`）早已完整实现，但核赔/初审/盲标三个工作台的
 * 「报案号 / 案件 ID」列此前是纯文本——用户看到一个 19 位雪花号，只能手抄到查询框。
 * Nielsen #6「识别优于回忆」：列表里给出的对象标识应可直接点击下钻，而不是逼用户记忆/转录。
 *
 * <p>单一真源
 * ----------
 * URL 契约完全委托给 {@link buildCase360Link}，本组件不拼任何字符串；安全由
 * {@link isSafeClaimId}（[A-Za-z0-9_-]{1,64}）把关。非法 ID **fail-closed** 降级为纯文本，
 * 绝不生成可能被注入的链接——与 caseDeepLinks.ts 的安全约定一致。
 *
 * <p>导航行为（业界惯例）
 * ----------------------
 * · 普通左键：应用内 `navigate()`（微应用 hash 路由，不整页刷新）；
 * · Ctrl/Cmd+点击、中键：交给浏览器新标签页打开（保留用户的工作上下文，便于多案并行核对）。
 *   通过渲染真实 `<a href>` 实现，而不是在 onClick 里手写 window.open —— 后者丢掉
 *   「复制链接地址 / 新标签页打开 / 状态栏预览」等原生语义。
 */
export interface ClaimIdLinkProps {
  /** 案件 ID（真值来源）。数字类型会被转成字符串；空值降级为占位符。 */
  claimId: string | number | null | undefined;
  /** 单元格显示文本。缺省显示 claimId；传入时用显示文本（如报案号）但下钻仍用 claimId。 */
  text?: string;
  /** 目标案件 360 Tab，默认 overview。 */
  tab?: CaseTab;
  /** E2E 稳定锚点；未传时按 `claim-id-link-<claimId>` 生成。 */
  testId?: string;
  /** 非法/空 ID 时的占位符，默认 '—'。 */
  placeholder?: string;
}

export function ClaimIdLink({
  claimId,
  text,
  tab = 'overview',
  testId,
  placeholder = '—',
}: ClaimIdLinkProps): ReactElement {
  const navigate = useNavigate();
  const id = claimId == null ? '' : String(claimId);
  const label = text?.trim() || id || placeholder;
  const link = buildCase360Link(id, tab);

  // fail-closed：非法 ID（含注入字符）或空值不生成链接，只显示纯文本。
  if (!link || !isSafeClaimId(id)) {
    return <span data-testid={testId ?? 'claim-id-link-invalid'}>{label}</span>;
  }

  const resolvedTestId = testId ?? `claim-id-link-${id}`;
  // qiankun 微应用：hash 路由锚点 `#/case?...`；带 origin+pathname 才能在浏览器新标签页正确打开。
  const href = `${window.location.origin}${window.location.pathname}#${link}`;

  const handleClick = (e: MouseEvent<HTMLElement>): void => {
    // 阻止冒泡：盲标等工作台整行有 onClick（选中任务），点 ID 不应同时触发行选中。
    e.stopPropagation();
    // 修饰键 / 中键交给浏览器默认行为（新标签页），仅普通左键走应用内导航。
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    navigate(link);
  };

  return (
    <Tooltip title={`打开案件 ${id} 详情`} mouseEnterDelay={0.4}>
      <Button
        type="link"
        size="small"
        href={href}
        data-testid={resolvedTestId}
        aria-label={`打开案件 ${label} 详情`}
        onClick={handleClick}
        style={{ paddingInline: 0, height: 'auto' }}
      >
        {label}
      </Button>
    </Tooltip>
  );
}
